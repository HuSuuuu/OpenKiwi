package com.orizon.openkiwi.core.memory

import kotlin.math.sqrt

/**
 * Lightweight on-device vector search engine using TF-IDF embeddings.
 * No external API or model required – runs entirely on-device.
 */
object VectorEngine {

    fun tokenize(text: String): List<String> {
        val cleaned = text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .trim()
        val words = cleaned.split(Regex("\\s+")).filter { it.length >= 2 }
        val bigrams = words.windowed(2) { it.joinToString("_") }
        return words + bigrams
    }

    fun buildTfVector(tokens: List<String>): Map<String, Double> {
        if (tokens.isEmpty()) return emptyMap()
        val freq = mutableMapOf<String, Int>()
        tokens.forEach { freq[it] = (freq[it] ?: 0) + 1 }
        val total = tokens.size.toDouble()
        return freq.mapValues { (_, count) -> count / total }
    }

    fun cosineSimilarity(a: Map<String, Double>, b: Map<String, Double>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val allKeys = a.keys.intersect(b.keys)
        if (allKeys.isEmpty()) return 0.0

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (key in allKeys) {
            dotProduct += (a[key] ?: 0.0) * (b[key] ?: 0.0)
        }
        for ((_, v) in a) normA += v * v
        for ((_, v) in b) normB += v * v

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0.0
    }

    fun computeEmbedding(text: String): Map<String, Double> {
        return buildTfVector(tokenize(text))
    }

    fun search(
        query: String,
        documents: List<Pair<Long, String>>,
        topK: Int = 10
    ): List<Pair<Long, Double>> {
        val queryVec = computeEmbedding(query)
        if (queryVec.isEmpty()) return emptyList()

        return documents.map { (id, content) ->
            val docVec = computeEmbedding(content)
            id to cosineSimilarity(queryVec, docVec)
        }
            .filter { it.second > 0.01 }
            .sortedByDescending { it.second }
            .take(topK)
    }
}
