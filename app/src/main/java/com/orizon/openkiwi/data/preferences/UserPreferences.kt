package com.orizon.openkiwi.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "openkiwi_preferences")

class UserPreferences(private val context: Context) {

    companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DEFAULT_MODEL_ID = stringPreferencesKey("default_model_id")
        val KEY_ENABLE_STREAMING = booleanPreferencesKey("enable_streaming")
        val KEY_ENABLE_SOUND = booleanPreferencesKey("enable_sound")
        val KEY_MAX_CONTEXT_MESSAGES = intPreferencesKey("max_context_messages")
        val KEY_CONFIRM_DANGEROUS_OPS = booleanPreferencesKey("confirm_dangerous_ops")
        val KEY_ENABLE_AUDIT_LOG = booleanPreferencesKey("enable_audit_log")
        val KEY_EMERGENCY_STOP_ENABLED = booleanPreferencesKey("emergency_stop_enabled")
        val KEY_SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        val KEY_FEISHU_APP_ID = stringPreferencesKey("feishu_app_id")
        val KEY_FEISHU_APP_SECRET = stringPreferencesKey("feishu_app_secret")
        val KEY_FEISHU_VERIFICATION_TOKEN = stringPreferencesKey("feishu_verification_token")
        val KEY_FEISHU_ENCRYPT_KEY = stringPreferencesKey("feishu_encrypt_key")
        val KEY_NOTIFICATION_MODEL_ID = stringPreferencesKey("notification_model_id")
        val KEY_NOTIFICATION_PROCESSING = booleanPreferencesKey("notification_processing_enabled")
    }

    val setupCompleted: Flow<Boolean> = context.dataStore.data.map { it[KEY_SETUP_COMPLETED] ?: false }
    val themeMode: Flow<String> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: "system" }
    val defaultModelId: Flow<String> = context.dataStore.data.map { it[KEY_DEFAULT_MODEL_ID] ?: "" }
    val enableStreaming: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENABLE_STREAMING] ?: true }
    val enableSound: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENABLE_SOUND] ?: true }
    val maxContextMessages: Flow<Int> = context.dataStore.data.map { it[KEY_MAX_CONTEXT_MESSAGES] ?: 50 }
    val confirmDangerousOps: Flow<Boolean> = context.dataStore.data.map { it[KEY_CONFIRM_DANGEROUS_OPS] ?: true }
    val enableAuditLog: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENABLE_AUDIT_LOG] ?: true }
    val feishuAppId: Flow<String> = context.dataStore.data.map { it[KEY_FEISHU_APP_ID] ?: "" }
    val feishuAppSecret: Flow<String> = context.dataStore.data.map { it[KEY_FEISHU_APP_SECRET] ?: "" }
    val feishuVerificationToken: Flow<String> = context.dataStore.data.map { it[KEY_FEISHU_VERIFICATION_TOKEN] ?: "" }
    val feishuEncryptKey: Flow<String> = context.dataStore.data.map { it[KEY_FEISHU_ENCRYPT_KEY] ?: "" }
    val notificationModelId: Flow<String> = context.dataStore.data.map { it[KEY_NOTIFICATION_MODEL_ID] ?: "" }
    val notificationProcessing: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFICATION_PROCESSING] ?: false }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setDefaultModelId(id: String) {
        context.dataStore.edit { it[KEY_DEFAULT_MODEL_ID] = id }
    }

    suspend fun setEnableStreaming(enable: Boolean) {
        context.dataStore.edit { it[KEY_ENABLE_STREAMING] = enable }
    }

    suspend fun setMaxContextMessages(max: Int) {
        context.dataStore.edit { it[KEY_MAX_CONTEXT_MESSAGES] = max }
    }

    suspend fun setConfirmDangerousOps(confirm: Boolean) {
        context.dataStore.edit { it[KEY_CONFIRM_DANGEROUS_OPS] = confirm }
    }

    suspend fun setEnableAuditLog(enable: Boolean) {
        context.dataStore.edit { it[KEY_ENABLE_AUDIT_LOG] = enable }
    }

    suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_SETUP_COMPLETED] = completed }
    }

    suspend fun setFeishuAppId(id: String) {
        context.dataStore.edit { it[KEY_FEISHU_APP_ID] = id }
    }

    suspend fun setFeishuAppSecret(secret: String) {
        context.dataStore.edit { it[KEY_FEISHU_APP_SECRET] = secret }
    }

    suspend fun setFeishuVerificationToken(token: String) {
        context.dataStore.edit { it[KEY_FEISHU_VERIFICATION_TOKEN] = token }
    }

    suspend fun setFeishuEncryptKey(key: String) {
        context.dataStore.edit { it[KEY_FEISHU_ENCRYPT_KEY] = key }
    }

    suspend fun setNotificationModelId(id: String) {
        context.dataStore.edit { it[KEY_NOTIFICATION_MODEL_ID] = id }
    }

    suspend fun setNotificationProcessing(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATION_PROCESSING] = enabled }
    }

    suspend fun getString(key: String): String {
        val prefKey = stringPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] ?: "" }.first()
    }

    suspend fun setString(key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        context.dataStore.edit { it[prefKey] = value }
    }

    suspend fun getFloat(key: String, default: Float = 0f): Float {
        val prefKey = floatPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] ?: default }.first()
    }

    suspend fun setFloat(key: String, value: Float) {
        val prefKey = floatPreferencesKey(key)
        context.dataStore.edit { it[prefKey] = value }
    }

    suspend fun getBoolean(key: String, default: Boolean = false): Boolean {
        val prefKey = booleanPreferencesKey(key)
        return context.dataStore.data.map { it[prefKey] ?: default }.first()
    }

    suspend fun setBoolean(key: String, value: Boolean) {
        val prefKey = booleanPreferencesKey(key)
        context.dataStore.edit { it[prefKey] = value }
    }
}
