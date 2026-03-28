package com.orizon.openkiwi.core.llm

import com.orizon.openkiwi.network.OpenAIApiClient
import okhttp3.OkHttpClient

class LlmProviderFactory(
    private val httpClient: OkHttpClient,
    private val openAIApiClient: OpenAIApiClient
) {
    private val providers = mutableMapOf<String, LlmProvider>()

    fun getProvider(providerType: String): LlmProvider {
        return providers.getOrPut(providerType) {
            when (providerType) {
                "anthropic" -> AnthropicProvider(httpClient)
                "gemini" -> GeminiProvider(httpClient)
                else -> OpenAIProvider(openAIApiClient)
            }
        }
    }
}
