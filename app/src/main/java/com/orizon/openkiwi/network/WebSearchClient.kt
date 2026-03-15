package com.orizon.openkiwi.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String = ""
)

interface WebSearchEngine {
    val name: String
    suspend fun search(query: String, maxResults: Int = 10): Result<List<SearchResult>>
}

class CustomApiSearchEngine(
    private val httpClient: OkHttpClient,
    private val apiUrl: String,
    private val apiKey: String,
    override val name: String = "Custom"
) : WebSearchEngine {
    
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String, maxResults: Int): Result<List<SearchResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$apiUrl?q=${java.net.URLEncoder.encode(query, "UTF-8")}&count=$maxResults"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw java.io.IOException("Search API error: ${response.code}")
                }
                val body = response.body?.string() ?: "[]"
                json.decodeFromString<List<SearchResult>>(body)
            }
        }
}
