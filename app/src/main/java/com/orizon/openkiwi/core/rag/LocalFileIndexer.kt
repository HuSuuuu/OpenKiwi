package com.orizon.openkiwi.core.rag

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import com.orizon.openkiwi.data.local.dao.RagChunkDao
import com.orizon.openkiwi.data.local.entity.RagChunkEntity
import com.orizon.openkiwi.util.DocumentTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object LocalFileIndexer {

    private val TEXT_EXT = setOf("txt", "md", "log", "csv", "json", "kt", "java", "py", "c", "h", "xml", "html", "htm")

    suspend fun refresh(
        context: Context,
        dao: RagChunkDao,
        maxFiles: Int = 40,
        chunkSize: Int = 900
    ): String = withContext(Dispatchers.IO) {
        dao.clearAll()
        val roots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        ).filter { it.exists() }

        val collected = mutableListOf<File>()
        for (root in roots) {
            if (collected.size >= maxFiles) break
            walk(root, depth = 0, maxDepth = 4, collected, maxFiles)
        }

        val chunks = mutableListOf<RagChunkEntity>()
        val now = System.currentTimeMillis()
        for (file in collected) {
            val text = readText(context, file) ?: continue
            val path = file.absolutePath
            text.chunked(chunkSize).forEachIndexed { idx, part ->
                if (part.isNotBlank()) {
                    chunks.add(RagChunkEntity(path = path, chunkIndex = idx, content = part, updatedAt = now))
                }
            }
        }
        if (chunks.isNotEmpty()) {
            dao.insertChunks(chunks)
        }
        "已索引 ${collected.size} 个文件，${chunks.size} 条文本块。"
    }

    private fun acceptFile(f: File): Boolean {
        val ext = f.extension.lowercase()
        if (TEXT_EXT.contains(ext)) return f.length() in 1..2_000_000
        return DocumentTextExtractor.canExtract(mimeFor(ext), f.name) && f.length() in 1..8_000_000
    }

    private fun File.extension(): String = name.substringAfterLast('.', "")

    private fun mimeFor(ext: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"

    private fun readText(context: Context, file: File): String? {
        val ext = file.extension.lowercase()
        if (TEXT_EXT.contains(ext)) {
            return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
                ?: runCatching { file.readText(Charsets.ISO_8859_1) }.getOrNull()
        }
        val uri = Uri.parse("file://${file.absolutePath}")
        val mime = mimeFor(ext)
        return DocumentTextExtractor.extract(context, uri, mime, file.name, maxChars = 80_000)
    }

    private fun walk(dir: File, depth: Int, maxDepth: Int, collected: MutableList<File>, maxFiles: Int) {
        if (depth > maxDepth || collected.size >= maxFiles) return
        val list = dir.listFiles() ?: return
        for (f in list) {
            if (collected.size >= maxFiles) return
            if (f.isDirectory) walk(f, depth + 1, maxDepth, collected, maxFiles)
            else if (f.isFile && acceptFile(f)) collected.add(f)
        }
    }
}
