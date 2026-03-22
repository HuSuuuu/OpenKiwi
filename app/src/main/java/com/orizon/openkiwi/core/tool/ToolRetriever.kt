package com.orizon.openkiwi.core.tool

import com.orizon.openkiwi.core.memory.BM25Engine
import com.orizon.openkiwi.core.memory.VectorEngine

/**
 * Selects a subset of tools for the chat completion request using on-device BM25
 * over tool name, description, category, and parameter text — no network.
 *
 * Pinned tools are always included. If the query cannot be tokenized or BM25
 * returns too few matches, callers should fall back to the full enabled set.
 */
object ToolRetriever {

    private val bm25 = BM25Engine()

    fun buildToolDocument(tool: Tool): String {
        val def = tool.definition
        val paramText = def.parameters.entries.joinToString(" ") { (k, p) ->
            "$k ${p.description} ${p.enumValues?.joinToString(" ").orEmpty()}"
        }
        return "${def.name} ${def.category} ${def.description} $paramText"
    }

    /**
     * @param tools enabled tools to rank (caller filters [Tool.definition.isEnabled])
     * @param pinNames always included if present in [tools]
     */
    fun selectTools(
        tools: List<Tool>,
        query: String,
        topK: Int = 22,
        pinNames: Set<String> = emptySet()
    ): List<Tool> {
        if (tools.isEmpty()) return emptyList()

        val byName = tools.associateBy { it.definition.name }
        val pinned = pinNames.mapNotNull { byName[it] }.distinctBy { it.definition.name }
        val pool = tools.filter { it.definition.name !in pinNames }
            .distinctBy { it.definition.name }

        if (pool.isEmpty()) return pinned

        val terms = VectorEngine.tokenize(query)
        if (terms.isEmpty()) {
            return (pinned + pool).distinctBy { it.definition.name }
        }

        val docs = pool.mapIndexed { i, t -> i.toLong() to buildToolDocument(t) }
        val ranked = bm25.search(query, docs, topK = topK.coerceAtLeast(1))
        val picked = ranked.map { pool[it.first.toInt()] }
        return (pinned + picked).distinctBy { it.definition.name }
    }
}
