package com.orizon.openkiwi.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream

object DocumentTextExtractor {

    private var pdfboxInitialized = false

    private fun ensurePdfBox(ctx: Context) {
        if (!pdfboxInitialized) {
            PDFBoxResourceLoader.init(ctx.applicationContext)
            pdfboxInitialized = true
        }
    }

    fun canExtract(mime: String, fileName: String): Boolean {
        val lower = fileName.lowercase()
        return mime == "application/pdf" || lower.endsWith(".pdf")
                || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || lower.endsWith(".docx")
                || mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" || lower.endsWith(".xlsx")
                || mime == "application/vnd.openxmlformats-officedocument.presentationml.presentation" || lower.endsWith(".pptx")
                || mime == "application/vnd.ms-excel" || lower.endsWith(".xls")
                || mime == "application/msword" || lower.endsWith(".doc")
    }

    fun extract(ctx: Context, uri: Uri, mime: String, fileName: String, maxChars: Int = 30_000): String? {
        val lower = fileName.lowercase()
        return try {
            when {
                mime == "application/pdf" || lower.endsWith(".pdf") ->
                    extractPdf(ctx, uri, maxChars)
                lower.endsWith(".docx") || mime.contains("wordprocessingml") ->
                    extractDocx(ctx, uri, maxChars)
                lower.endsWith(".xlsx") || mime.contains("spreadsheetml") ->
                    extractXlsx(ctx, uri, maxChars)
                lower.endsWith(".pptx") || mime.contains("presentationml") ->
                    extractPptx(ctx, uri, maxChars)
                lower.endsWith(".doc") || lower.endsWith(".xls") ->
                    "（旧版 Office 格式 .doc/.xls 暂不支持文本提取，请转为 .docx/.xlsx 后重试）"
                else -> null
            }
        } catch (e: Exception) {
            Log.w("DocExtractor", "extract failed for $fileName", e)
            null
        }
    }

    private fun extractPdf(ctx: Context, uri: Uri, maxChars: Int): String? {
        ensurePdfBox(ctx)
        return ctx.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                val stripper = PDFTextStripper()
                val totalPages = doc.numberOfPages
                val sb = StringBuilder()
                sb.append("[PDF 共 $totalPages 页]\n")
                for (page in 1..totalPages) {
                    if (sb.length >= maxChars) {
                        sb.append("\n...[已截断，仅提取前 ${page - 1} 页]")
                        break
                    }
                    stripper.startPage = page
                    stripper.endPage = page
                    val text = stripper.getText(doc)
                    if (text.isNotBlank()) {
                        sb.append("--- 第 $page 页 ---\n")
                        sb.append(text.take(maxChars - sb.length))
                    }
                }
                sb.toString().takeIf { it.length > 20 }
            }
        }
    }

    private fun extractDocx(ctx: Context, uri: Uri, maxChars: Int): String? {
        return ctx.contentResolver.openInputStream(uri)?.use { input ->
            val sb = StringBuilder()
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        extractXmlText(zip, sb, maxChars)
                        break
                    }
                    entry = zip.nextEntry
                }
            }
            sb.toString().takeIf { it.isNotBlank() }
        }
    }

    private fun extractXlsx(ctx: Context, uri: Uri, maxChars: Int): String? {
        return ctx.contentResolver.openInputStream(uri)?.use { input ->
            val sharedStrings = mutableListOf<String>()
            val sheets = mutableMapOf<String, ByteArray>()

            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "xl/sharedStrings.xml" -> {
                            extractSharedStrings(zip, sharedStrings)
                        }
                        entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml") -> {
                            sheets[entry.name] = zip.readBytes()
                        }
                    }
                    entry = zip.nextEntry
                }
            }

            if (sheets.isEmpty()) return@use null

            val sb = StringBuilder()
            sb.append("[Excel 共 ${sheets.size} 个工作表]\n")
            for ((name, data) in sheets.entries.sortedBy { it.key }) {
                if (sb.length >= maxChars) break
                val sheetNum = Regex("sheet(\\d+)").find(name)?.groupValues?.get(1) ?: "?"
                sb.append("--- Sheet $sheetNum ---\n")
                parseSheetXml(data.inputStream(), sharedStrings, sb, maxChars)
                sb.append("\n")
            }
            sb.toString().takeIf { it.isNotBlank() }
        }
    }

    private fun extractPptx(ctx: Context, uri: Uri, maxChars: Int): String? {
        return ctx.contentResolver.openInputStream(uri)?.use { input ->
            val sb = StringBuilder()
            val slideTexts = mutableMapOf<Int, StringBuilder>()

            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val match = Regex("ppt/slides/slide(\\d+)\\.xml").find(entry.name)
                    if (match != null) {
                        val slideNum = match.groupValues[1].toIntOrNull() ?: 0
                        val slideSb = StringBuilder()
                        extractXmlText(zip, slideSb, maxChars)
                        if (slideSb.isNotBlank()) {
                            slideTexts[slideNum] = slideSb
                        }
                    }
                    entry = zip.nextEntry
                }
            }

            if (slideTexts.isEmpty()) return@use null
            sb.append("[PPT 共 ${slideTexts.size} 页]\n")
            for ((num, text) in slideTexts.entries.sortedBy { it.key }) {
                if (sb.length >= maxChars) {
                    sb.append("\n...[已截断]")
                    break
                }
                sb.append("--- 第 $num 页 ---\n")
                sb.append(text.toString().take(maxChars - sb.length))
                sb.append("\n")
            }
            sb.toString().takeIf { it.isNotBlank() }
        }
    }

    private fun extractXmlText(input: InputStream, sb: StringBuilder, maxChars: Int) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(input, "UTF-8")

            var eventType = parser.eventType
            var inTextElement = false
            while (eventType != XmlPullParser.END_DOCUMENT && sb.length < maxChars) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name?.lowercase() ?: ""
                        inTextElement = tag == "t" || tag == "a:t" || tag == "w:t"
                    }
                    XmlPullParser.TEXT -> {
                        if (inTextElement) {
                            val text = parser.text
                            if (!text.isNullOrBlank()) sb.append(text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name?.lowercase() ?: ""
                        if (tag == "p" || tag == "w:p" || tag == "a:p") {
                            sb.append("\n")
                        }
                        if (tag == "t" || tag == "a:t" || tag == "w:t") {
                            inTextElement = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w("DocExtractor", "XML parse error", e)
        }
    }

    private fun extractSharedStrings(input: InputStream, list: MutableList<String>) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(input, "UTF-8")

            var eventType = parser.eventType
            var inT = false
            val currentString = StringBuilder()
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "t") inT = true
                    }
                    XmlPullParser.TEXT -> {
                        if (inT) currentString.append(parser.text ?: "")
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "t" -> inT = false
                            "si" -> {
                                list.add(currentString.toString())
                                currentString.clear()
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w("DocExtractor", "SharedStrings parse error", e)
        }
    }

    private fun parseSheetXml(
        input: InputStream,
        sharedStrings: List<String>,
        sb: StringBuilder,
        maxChars: Int
    ) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(input, "UTF-8")

            var eventType = parser.eventType
            var cellType = ""
            var inV = false
            var isFirstCellInRow = true
            while (eventType != XmlPullParser.END_DOCUMENT && sb.length < maxChars) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "row" -> isFirstCellInRow = true
                            "c" -> cellType = parser.getAttributeValue(null, "t") ?: ""
                            "v" -> inV = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inV) {
                            if (!isFirstCellInRow) sb.append("\t")
                            isFirstCellInRow = false
                            val raw = parser.text ?: ""
                            if (cellType == "s") {
                                val idx = raw.trim().toIntOrNull()
                                sb.append(if (idx != null && idx < sharedStrings.size) sharedStrings[idx] else raw)
                            } else {
                                sb.append(raw)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "v" -> inV = false
                            "row" -> sb.append("\n")
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w("DocExtractor", "Sheet parse error", e)
        }
    }
}
