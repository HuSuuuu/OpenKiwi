package com.orizon.openkiwi.network

import com.orizon.openkiwi.data.preferences.UserPreferences
import kotlinx.coroutines.flow.first
import java.security.SecureRandom

class ApiAuth(private val userPreferences: UserPreferences) {

    companion object {
        private const val API_TOKEN_KEY = "api_access_token"

        fun generateToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    suspend fun getToken(): String {
        val existing = userPreferences.getString(API_TOKEN_KEY)
        if (existing.isNotBlank()) return existing
        val token = generateToken()
        userPreferences.setString(API_TOKEN_KEY, token)
        return token
    }

    suspend fun validateToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val stored = userPreferences.getString(API_TOKEN_KEY)
        if (stored.isBlank()) return true
        return token == stored || token == "Bearer $stored"
    }

    suspend fun regenerateToken(): String {
        val token = generateToken()
        userPreferences.setString(API_TOKEN_KEY, token)
        return token
    }
}
