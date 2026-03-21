package com.orizon.openkiwi.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ArtifactOpener {
    fun open(context: Context, path: String, mimeType: String? = null): Result<Unit> = runCatching {
        val file = File(path)
        require(file.exists() && file.isFile) { "文件不存在: $path" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: guessMimeType(file.extension))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun share(context: Context, path: String, mimeType: String? = null): Result<Unit> = runCatching {
        val file = File(path)
        require(file.exists() && file.isFile) { "文件不存在: $path" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType ?: guessMimeType(file.extension)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享文件").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun guessMimeType(extension: String): String = when (extension.lowercase()) {
        "txt", "md", "log", "json", "xml", "yml", "yaml", "csv", "kt", "java", "py", "js", "ts" -> "text/plain"
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "apk" -> "application/vnd.android.package-archive"
        else -> "*/*"
    }
}
