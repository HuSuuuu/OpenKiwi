package com.orizon.openkiwi.core.recipe

import kotlinx.serialization.Serializable

@Serializable
data class AutomationRecipe(
    val id: String,
    val name: String,
    val description: String = "",
    val steps: List<String>,
    val targetPackage: String? = null,
    val preset: Boolean = false
)

object RecipePresets {
    fun builtin(): List<AutomationRecipe> = listOf(
        AutomationRecipe(
            id = "preset_open_wechat",
            name = "打开微信",
            description = "启动微信并进入主界面",
            steps = listOf("打开微信应用，等待进入主界面，如遇弹窗则关闭"),
            targetPackage = "com.tencent.mm",
            preset = true
        ),
        AutomationRecipe(
            id = "preset_open_settings_battery",
            name = "查看电池用量",
            description = "打开系统设置中的电池页面",
            steps = listOf(
                "打开系统设置",
                "进入电池或电量相关页面并停留以便查看"
            ),
            preset = true
        ),
        AutomationRecipe(
            id = "preset_capture_and_notes",
            name = "截图备忘",
            description = "截屏并打开笔记类应用（需设备支持）",
            steps = listOf(
                "使用系统截图快捷键或通知栏完成一次截图",
                "打开系统相册或文件管理查看最近截图"
            ),
            preset = true
        )
    )
}
