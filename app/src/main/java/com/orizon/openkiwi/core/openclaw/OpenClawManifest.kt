package com.orizon.openkiwi.core.openclaw

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Maps the static manifest found in each OpenClaw extension's openclaw.plugin.json.
 */
@Serializable
data class OpenClawPluginManifest(
    val id: String,
    val name: String? = null,
    val description: String? = null,
    val version: String? = null,
    val enabledByDefault: Boolean? = null,
    val kind: List<String>? = null,
    val channels: List<String>? = null,
    val providers: List<String>? = null,
    val skills: List<String>? = null,
    val contracts: ManifestContracts? = null,
    val configSchema: JsonElement? = null,
    val uiHints: Map<String, UiHint>? = null,
    val modelSupport: ModelSupport? = null,
    val cliBackends: List<String>? = null,
    val providerAuthEnvVars: Map<String, List<String>>? = null,
    val providerAuthChoices: List<JsonElement>? = null,
    val channelConfigs: Map<String, JsonElement>? = null,
    val legacyPluginIds: List<String>? = null,
    val autoEnableWhenConfiguredProviders: List<String>? = null
)

@Serializable
data class ManifestContracts(
    val tools: List<String>? = null,
    val speechProviders: List<String>? = null,
    val realtimeTranscriptionProviders: List<String>? = null,
    val realtimeVoiceProviders: List<String>? = null,
    val mediaUnderstandingProviders: List<String>? = null,
    val imageGenerationProviders: List<String>? = null,
    val webFetchProviders: List<String>? = null,
    val webSearchProviders: List<String>? = null
)

@Serializable
data class UiHint(
    val label: String? = null,
    val help: String? = null,
    val sensitive: Boolean? = null,
    val placeholder: String? = null
)

@Serializable
data class ModelSupport(
    val modelPrefixes: List<String>? = null
)

/**
 * Metadata from a package.json "openclaw" block.
 */
@Serializable
data class OpenClawPackageMeta(
    val extensions: List<String>? = null,
    val setupEntry: String? = null,
    val channel: PackageChannel? = null,
    val install: JsonElement? = null,
    val startup: JsonElement? = null,
    val bundle: JsonElement? = null
)

@Serializable
data class PackageChannel(
    val id: String? = null,
    val label: String? = null,
    val docsPath: String? = null
)

/**
 * Represents a discovered OpenClaw tool from the Gateway catalog.
 */
@Serializable
data class OpenClawToolSpec(
    val name: String,
    val description: String = "",
    val pluginId: String? = null,
    val pluginName: String? = null,
    val ownerOnly: Boolean = false,
    val inputSchema: JsonElement? = null
)

/**
 * Gateway frame protocol types.
 */
@Serializable
sealed class GatewayFrame {
    abstract val type: String
}

@Serializable
@SerialName("req")
data class RequestFrame(
    override val type: String = "req",
    val id: String,
    val method: String,
    val params: JsonElement? = null
) : GatewayFrame()

@Serializable
@SerialName("res")
data class ResponseFrame(
    override val type: String = "res",
    val id: String,
    val ok: Boolean,
    val payload: JsonElement? = null,
    val error: GatewayError? = null
) : GatewayFrame()

@Serializable
@SerialName("event")
data class EventFrame(
    override val type: String = "event",
    val event: String,
    val payload: JsonElement? = null,
    val seq: Int? = null,
    val stateVersion: Int? = null
) : GatewayFrame()

@Serializable
data class GatewayError(
    val code: String? = null,
    val message: String? = null,
    val details: JsonElement? = null
)

/**
 * Saved gateway connection info for persistence across app restarts.
 */
@Serializable
data class OpenClawSavedGateway(
    val id: String,
    val url: String,
    val token: String? = null
)

object ManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    fun parsePluginManifest(jsonStr: String): OpenClawPluginManifest? =
        runCatching { json.decodeFromString<OpenClawPluginManifest>(jsonStr) }.getOrNull()

    fun parsePackageMeta(jsonStr: String): OpenClawPackageMeta? = runCatching {
        val root = json.parseToJsonElement(jsonStr)
        val rootObj = root as? JsonObject ?: return@runCatching null
        val openclawBlock = rootObj["openclaw"] ?: return@runCatching null
        json.decodeFromJsonElement(OpenClawPackageMeta.serializer(), openclawBlock)
    }.getOrNull()
}
