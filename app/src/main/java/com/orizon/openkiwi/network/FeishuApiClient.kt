package com.orizon.openkiwi.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class FeishuConfig(
    val appId: String = "",
    val appSecret: String = "",
    val baseUrl: String = "https://open.feishu.cn/open-apis"
)

class FeishuApiClient(private val httpClient: OkHttpClient) {
    companion object {
        private const val TAG = "FeishuApiClient"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private var tenantAccessToken: String? = null
    private var config: FeishuConfig? = null

    suspend fun authenticate(config: FeishuConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val start = System.currentTimeMillis()
            Log.i(TAG, "authenticate start on ${Thread.currentThread().name}")
            this@FeishuApiClient.config = config
            val body = buildJsonObject {
                put("app_id", config.appId)
                put("app_secret", config.appSecret)
            }.toString()
            val request = Request.Builder()
                .url("${config.baseUrl}/auth/v3/tenant_access_token/internal")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val respBody = httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val respText = response.body?.string() ?: throw Exception("Empty response")
                if (!response.isSuccessful) {
                    throw Exception("HTTP $code: ${respText.take(300)}")
                }
                respText
            }
            val token = json.decodeFromString<FeishuTokenResponse>(respBody)
            if (token.code != 0) throw Exception("Feishu auth failed: ${token.msg}")
            tenantAccessToken = token.tenantAccessToken
            Log.i(
                TAG,
                "authenticate success in ${System.currentTimeMillis() - start}ms, tokenLen=${token.tenantAccessToken.length}"
            )
            token.tenantAccessToken
        }.onFailure {
            Log.e(TAG, "authenticate failed on ${Thread.currentThread().name}: ${it.message}", it)
        }
    }

    private suspend fun ensureToken(): String {
        tenantAccessToken?.let { return it }
        config?.let { authenticate(it).getOrThrow() }
        return tenantAccessToken ?: throw Exception("Feishu not authenticated. Call auth first.")
    }

    private fun authRequest(url: String): Request.Builder {
        val token = tenantAccessToken ?: throw Exception("Not authenticated")
        return Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
    }

    suspend fun sendMessage(receiveIdType: String, receiveId: String, msgType: String, content: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.currentTimeMillis()
                Log.i(TAG, "sendMessage start on ${Thread.currentThread().name}, type=$msgType")
                ensureToken()
                val body = buildJsonObject {
                    put("receive_id", receiveId)
                    put("msg_type", msgType)
                    put("content", content)
                }.toString()
                val request = authRequest("${baseUrl()}/im/v1/messages?receive_id_type=$receiveIdType")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val result = httpClient.newCall(request).execute().use { response ->
                    val responseText = response.body?.string() ?: "sent"
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${responseText.take(300)}")
                    }
                    responseText
                }
                Log.i(TAG, "sendMessage done in ${System.currentTimeMillis() - start}ms")
                result
            }.onFailure {
                Log.e(TAG, "sendMessage failed: ${it.message}", it)
            }
        }

    suspend fun replyMessage(messageId: String, msgType: String, content: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.currentTimeMillis()
                Log.i(TAG, "replyMessage start on ${Thread.currentThread().name}, messageId=${messageId.takeLast(6)}")
                ensureToken()
                val body = buildJsonObject {
                    put("msg_type", msgType)
                    put("content", content)
                }.toString()
                val request = authRequest("${baseUrl()}/im/v1/messages/$messageId/reply")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val result = httpClient.newCall(request).execute().use { response ->
                    val responseText = response.body?.string() ?: "replied"
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${responseText.take(300)}")
                    }
                    responseText
                }
                Log.i(TAG, "replyMessage done in ${System.currentTimeMillis() - start}ms")
                result
            }.onFailure {
                Log.e(TAG, "replyMessage failed: ${it.message}", it)
            }
        }

    suspend fun getChats(pageSize: Int = 20, pageToken: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureToken()
                val url = buildString {
                    append("${baseUrl()}/im/v1/chats?page_size=$pageSize")
                    if (!pageToken.isNullOrBlank()) append("&page_token=$pageToken")
                }
                val request = authRequest(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    val responseText = response.body?.string() ?: "[]"
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${responseText.take(300)}")
                    }
                    responseText
                }
            }
        }

    suspend fun getChatMessages(chatId: String, pageSize: Int = 20, pageToken: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureToken()
                val url = buildString {
                    append("${baseUrl()}/im/v1/messages?container_id_type=chat&container_id=$chatId&page_size=$pageSize")
                    if (!pageToken.isNullOrBlank()) append("&page_token=$pageToken")
                }
                val request = authRequest(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    val responseText = response.body?.string() ?: "[]"
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${responseText.take(300)}")
                    }
                    responseText
                }
            }
        }

    suspend fun getChatInfo(chatId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureToken()
                val request = authRequest("${baseUrl()}/im/v1/chats/$chatId").get().build()
                httpClient.newCall(request).execute().use { response ->
                    val responseText = response.body?.string() ?: "{}"
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${responseText.take(300)}")
                    }
                    responseText
                }
            }
        }

    suspend fun createGroup(name: String, description: String = "", userIds: List<String> = emptyList()): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureToken()
                val body = buildJsonObject {
                    put("name", name)
                    if (description.isNotBlank()) put("description", description)
                }.toString()
                val request = authRequest("${baseUrl()}/im/v1/chats")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    val responseText = response.body?.string() ?: "{}"
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${responseText.take(300)}")
                    }
                    responseText
                }
            }
        }

    suspend fun getUserInfo(userIdType: String = "open_id", userId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureToken()
                val request = authRequest("${baseUrl()}/contact/v3/users/$userId?user_id_type=$userIdType")
                    .get().build()
                httpClient.newCall(request).execute().use { response ->
                    val responseText = response.body?.string() ?: "{}"
                    if (!response.isSuccessful) {
                        throw Exception("HTTP ${response.code}: ${responseText.take(300)}")
                    }
                    responseText
                }
            }
        }

    fun isAuthenticated(): Boolean = tenantAccessToken != null

    private fun baseUrl(): String = config?.baseUrl ?: "https://open.feishu.cn/open-apis"
}

@Serializable
data class FeishuTokenResponse(
    val code: Int = 0,
    val msg: String = "",
    @kotlinx.serialization.SerialName("tenant_access_token")
    val tenantAccessToken: String = ""
)
