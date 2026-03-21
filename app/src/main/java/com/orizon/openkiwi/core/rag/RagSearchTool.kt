package com.orizon.openkiwi.core.rag

import android.content.Context
import com.orizon.openkiwi.data.local.dao.RagChunkDao
import com.orizon.openkiwi.core.tool.PermissionLevel
import com.orizon.openkiwi.core.tool.Tool
import com.orizon.openkiwi.core.tool.ToolCategory
import com.orizon.openkiwi.core.tool.ToolDefinition
import com.orizon.openkiwi.core.tool.ToolParamDef
import com.orizon.openkiwi.core.tool.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lightweight local "RAG": refresh indexes text from Documents/Downloads into SQLite, search uses SQL LIKE.
 */
class RagSearchTool(
    private val context: Context,
    private val ragChunkDao: RagChunkDao
) : Tool {

    override val definition = ToolDefinition(
        name = "local_rag",
        description = """Local file knowledge base (no cloud embeddings).
- action=refresh: re-scan Documents + Downloads (limited file count/size), extract text from txt/md/pdf/docx/xlsx/pptx, chunk and store locally.
- action=search: keyword search over stored chunks (use specific nouns).
- action=stats: return chunk count.""",
        category = ToolCategory.SYSTEM.name,
        permissionLevel = PermissionLevel.NORMAL.name,
        parameters = mapOf(
            "action" to ToolParamDef("string", "refresh | search | stats", true),
            "query" to ToolParamDef("string", "keywords for search", false)
        ),
        requiredParams = listOf("action"),
        timeoutMs = 120_000L
    )

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        val action = params["action"]?.toString()?.trim()?.lowercase() ?: return@withContext ToolResult(
            definition.name, false, "", "missing action"
        )
        when (action) {
            "refresh", "reindex", "index" -> {
                val msg = LocalFileIndexer.refresh(context.applicationContext, ragChunkDao)
                ToolResult(definition.name, true, msg, null)
            }
            "search", "query" -> {
                val q = params["query"]?.toString()?.trim().orEmpty()
                if (q.length < 2) {
                    return@withContext ToolResult(definition.name, false, "", "query too short")
                }
                val rows = ragChunkDao.search(q, 20)
                if (rows.isEmpty()) {
                    ToolResult(definition.name, true, "无匹配片段。可先 action=refresh 建立索引。", null)
                } else {
                    val out = buildString {
                        rows.forEachIndexed { i, r ->
                            appendLine("--- #${i + 1} ${r.path} [${r.chunkIndex}] ---")
                            appendLine(r.content.trim())
                            appendLine()
                        }
                    }
                    ToolResult(definition.name, true, out, null)
                }
            }
            "stats", "count" -> {
                val n = ragChunkDao.count()
                ToolResult(definition.name, true, "本地索引块数量: $n", null)
            }
            else -> ToolResult(definition.name, false, "", "unknown action")
        }
    }
}
