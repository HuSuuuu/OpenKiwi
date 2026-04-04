package com.orizon.openkiwi.core.gui

class ActionValidator(private val taskContext: GuiTaskContext) {

    private val recentLaunches = mutableListOf<String>()
    private val maxRecentLaunches = 10

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String, val suggestedAction: GuiAction? = null) : ValidationResult()
        data class Modified(val newAction: GuiAction, val reason: String) : ValidationResult()
    }

    fun validate(action: GuiAction, currentApp: String?, consecutiveWaits: Int = 0): ValidationResult {
        return when (action) {
            is GuiAction.Launch -> validateLaunch(action, currentApp)
            is GuiAction.Wait -> validateWait(action, consecutiveWaits)
            is GuiAction.Tap -> validateTap(action)
            is GuiAction.Home -> validateHome(currentApp)
            else -> ValidationResult.Valid
        }
    }

    fun recordLaunch(appName: String) {
        recentLaunches.add(appName.lowercase())
        if (recentLaunches.size > maxRecentLaunches) recentLaunches.removeAt(0)
    }

    fun reset() {
        recentLaunches.clear()
    }

    private fun validateLaunch(action: GuiAction.Launch, currentApp: String?): ValidationResult {
        val lower = action.appName.lowercase()
        if (lower.contains("openkiwi") || lower.contains("com.orizon.openkiwi")) {
            return ValidationResult.Invalid(
                reason = "禁止启动控制应用",
                suggestedAction = taskContext.targetApp?.let { GuiAction.Launch(it) }
            )
        }

        if (currentApp != null) {
            val launchPkg = AppPackages.getPackageName(action.appName)
            val launchAppName = AppPackages.getAppName(currentApp)

            val isAlreadyForeground = when {
                launchPkg != null && currentApp.equals(launchPkg, ignoreCase = true) -> true
                launchAppName != null && action.appName.contains(launchAppName, ignoreCase = true) -> true
                currentApp.contains(action.appName, ignoreCase = true) -> true
                action.appName.contains(currentApp.substringAfterLast('.'), ignoreCase = true) -> true
                else -> false
            }
            if (isAlreadyForeground) {
                return ValidationResult.Invalid(
                    "应用已在前台，无需重复启动",
                    GuiAction.Wait(500)
                )
            }
        }

        val consecutiveSame = recentLaunches.takeLastWhile { it == lower }.size
        if (consecutiveSame >= 2) {
            return ValidationResult.Invalid(
                "已连续启动「${action.appName}」${consecutiveSame}次，禁止重复启动",
                GuiAction.Wait(500)
            )
        }

        return ValidationResult.Valid
    }

    private fun validateWait(action: GuiAction.Wait, consecutiveWaits: Int): ValidationResult {
        if (consecutiveWaits >= 3) {
            return ValidationResult.Invalid("已连续等待 $consecutiveWaits 次，请执行其他操作")
        }
        if (action.durationMs > 15000) {
            return ValidationResult.Modified(GuiAction.Wait(8000), "等待时间过长，已缩短至 8 秒")
        }
        return ValidationResult.Valid
    }

    private fun validateTap(action: GuiAction.Tap): ValidationResult {
        if (!action.isPixel && (action.x < 0 || action.x > 999 || action.y < 0 || action.y > 999)) {
            val fixedX = action.x.coerceIn(0, 999)
            val fixedY = action.y.coerceIn(0, 999)
            return ValidationResult.Modified(GuiAction.Tap(fixedX, fixedY), "坐标已修正到有效范围")
        }
        return ValidationResult.Valid
    }

    private fun validateHome(currentApp: String?): ValidationResult {
        return ValidationResult.Valid
    }
}
