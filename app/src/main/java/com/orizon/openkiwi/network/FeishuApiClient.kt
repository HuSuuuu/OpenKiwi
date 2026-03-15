package com.orizon.openkiwi.network

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

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private var tenantAccessToken: String? = null
    private var config: FeishuConfig? = null

    suspend fun authenticate(config: FeishuConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            this@FeishuApiClient.config = config
            val body = buildJsonObject {
                put("app_id", config.appId)
                put("app_secret", config.appSecret)
            }.toString()
            val request = Request.Builder()
                .url("${config.baseUrl}/auth/v3/tenant_access_token/internal")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: throw Exception("Empty response")
            val token = json.decodeFromString<FeishuTokenResponse>(respBody)
            if (token.code != 0) throw Exception("Feishu auth failed: ${token.msg}")
            tenantAccessToken = token.tenantAccessToken
            token.tenantAccessToken
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
                ensureToken()
                val body = buildJsonObject {
                    put("receive_id", receiveId)
                    put("msg_type", msgType)
                    put("content", content)
                }.toString()
                val request = authRequest("${baseUrl()}/im/v1/messages?receive_id_type=$receiveIdType")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val response = httpClient.newCall(request).execute()
                response.body?.string() ?: "sent"
            }
        }

    suspend fun replyMessage(messageId: String, msgType: String, content: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureToken()
                val body = buildJsonObject {
                    put("msg_type", msgType)
                    put("content", content)
                }.toString()
                val request = authRequest("${baseUrl()}/im/v1/messages/$messageId/reply")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()
                val response = httpClient.newCall(request).execute()
                response.body?.string() ?: "replied"
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
                val response = httpClient.newCall(request).execute()
                response.body?.string() ?: "[]"
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
                val response = httpClient.newCall(request).execute()
                response.body?.string() ?: "[]"
            }
        }

    suspend fun getChatInfo(chatId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureToken()
                val request = authRequest("${baseUrl()}/im/v1/chats/$chatId").get().build()
                val response = httpClient.newCall(request).execute()
                response.body?.string() ?: "{}"
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
                val response = httpClient.newCall(request).execute()
                response.body?.string() ?: "{}"
            }
        }

    suspend fun getUserInfo(userIdType: String = "open_id", userId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureToken()
                val request = authRequest("${baseUrl()}/contact/v3/users/$userId?user_id_type=$userIdType")
                    .get().build()
                val response = httpClient.newCall(request).execute()
                response.body?.string() ?: "{}"
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
