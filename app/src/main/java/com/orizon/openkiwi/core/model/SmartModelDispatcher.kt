package com.orizon.openkiwi.core.model

import com.orizon.openkiwi.data.repository.ModelRepository

class SmartModelDispatcher(private val modelRepository: ModelRepository) {

    companion object {
        private val SCENE_KEYWORDS = mapOf(
            "code" to listOf("代码", "编程", "开发", "编写", "debug", "code", "程序", "函数", "脚本", "compile", "refactor"),
            "vision" to listOf("截图", "屏幕", "图片", "看", "识别", "screenshot", "image", "photo", "OCR", "界面"),
            "chat" to listOf("聊天", "对话", "闲聊", "你好", "谢谢", "chat", "hello"),
            "search" to listOf("搜索", "查找", "查询", "search", "find", "网上", "最新"),
            "system" to listOf("系统", "设置", "安装", "卸载", "WiFi", "蓝牙", "电池", "文件", "存储"),
            "creative" to listOf("写作", "创作", "文案", "翻译", "摘要", "总结", "写", "故事", "诗")
        )
    }

    suspend fun dispatch(userMessage: String, forcedModelId: String? = null): ModelConfig? {
        if (forcedModelId != null) {
            return modelRepository.getConfig(forcedModelId)
        }

        val scene = classifyScene(userMessage)
        return getModelForScene(scene) ?: modelRepository.getDefaultConfig()
    }

    fun classifyScene(input: String): String {
        val scores = mutableMapOf<String, Int>()
        val lower = input.lowercase()

        for ((scene, keywords) in SCENE_KEYWORDS) {
            val score = keywords.count { lower.contains(it.lowercase()) }
            if (score > 0) scores[scene] = score
        }

        return scores.maxByOrNull { it.value }?.key ?: "chat"
    }

    private suspend fun getModelForScene(scene: String): ModelConfig? {
        val configs = modelRepository.getAllConfigsOnce()
        return configs.firstOrNull { scene in it.sceneTags }
            ?: configs.firstOrNull {
                when (scene) {
                    "vision" -> it.supportsVision
                    "code" -> it.sceneTags.any { t -> t.contains("code", true) }
                    else -> false
                }
            }
    }
}
