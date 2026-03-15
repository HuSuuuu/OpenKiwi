package com.orizon.openkiwi.core.memory

import kotlin.math.ln

/**
 * BM25 full-text search engine for memory retrieval.
 * k1 and b are standard BM25 tuning parameters.
 */
class BM25Engine(private val k1: Double = 1.5, private val b: Double = 0.75) {

    fun search(
        query: String,
        documents: List<Pair<Long, String>>,
        topK: Int = 10
    ): List<Pair<Long, Double>> {
        if (documents.isEmpty()) return emptyList()

        val queryTerms = VectorEngine.tokenize(query)
        if (queryTerms.isEmpty()) return emptyList()

        val docTokens = documents.map { (id, content) ->
            id to VectorEngine.tokenize(content)
        }
        val avgDl = docTokens.map { it.second.size }.average().coerceAtLeast(1.0)
        val n = documents.size.toDouble()

        val df = mutableMapOf<String, Int>()
        for ((_, tokens) in docTokens) {
            tokens.toSet().forEach { term ->
                df[term] = (df[term] ?: 0) + 1
            }
        }

        return docTokens.map { (id, tokens) ->
            val dl = tokens.size.toDouble()
            val tf = mutableMapOf<String, Int>()
            tokens.forEach { tf[it] = (tf[it] ?: 0) + 1 }

            var score = 0.0
            for (term in queryTerms) {
                val termFreq = tf[term] ?: 0
                val docFreq = df[term] ?: 0
                if (termFreq == 0 || docFreq == 0) continue

                val idf = ln((n - docFreq + 0.5) / (docFreq + 0.5) + 1.0)
                val tfNorm = (termFreq * (k1 + 1)) / (termFreq + k1 * (1 - b + b * dl / avgDl))
                score += idf * tfNorm
            }

            id to score
        }
            .filter { it.second > 0.0 }
            .sortedByDescending { it.second }
            .take(topK)
    }
}
