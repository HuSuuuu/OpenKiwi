package com.orizon.openkiwi.core.gui

class GuiTaskContext {
    var originalGoal: String = ""; private set
    var targetApp: String? = null; private set
    var targetPackage: String? = null; private set
    var hasEnteredTargetApp: Boolean = false; private set
    var sameScreenCount: Int = 0; private set
    private var lastScreenHash: Int = 0
    val completedSteps = mutableListOf<String>()
    var currentProgress: String = ""; private set
    var consecutiveWaits: Int = 0; private set
    var lastAction: String? = null; private set
    var lastActionSuccess: Boolean = true; private set
    var consecutiveFailures: Int = 0; private set
    var totalSteps: Int = 0; private set
    private var screenSignature: String = ""

    fun initTask(goal: String) {
        originalGoal = goal
        completedSteps.clear()
        currentProgress = "任务开始"
        hasEnteredTargetApp = false
        sameScreenCount = 0; lastScreenHash = 0
        consecutiveWaits = 0; consecutiveFailures = 0; totalSteps = 0
        lastAction = null; lastActionSuccess = true
        screenSignature = ""

        val lower = goal.lowercase()
        for ((name, pkg) in AppPackages.packages) {
            if (lower.contains(name.lowercase())) { targetApp = name; targetPackage = pkg; return }
        }
        targetApp = extractAppName(goal)
        targetPackage = targetApp?.let { AppPackages.getPackageName(it) }
    }

    private fun extractAppName(goal: String): String? {
        val patterns = listOf(
            Regex("(?:在|打开|用|启动|去|进入)(.{2,8}?)(?:中|里|上|并|然后|，|。|搜索|找|查|发|看|$)"),
            Regex("(?:帮我|请|给我)(?:在)?(.{2,8}?)(?:中|里|上|搜|查|找|发|看|$)")
        )
        for (p in patterns) {
            p.find(goal)?.groupValues?.getOrNull(1)?.trim()?.let {
                if (it.length in 2..10 && !it.contains("手机")) return it
            }
        }
        return null
    }

    fun updateScreenHash(hash: Int) {
        if (hash == lastScreenHash) sameScreenCount++ else { sameScreenCount = 0; lastScreenHash = hash }
    }

    fun updateScreenSignature(sig: String) {
        if (sig == screenSignature) sameScreenCount++ else sameScreenCount = 0
        screenSignature = sig
    }

    fun markEnteredTargetApp() { hasEnteredTargetApp = true; addStep("进入目标应用: ${targetApp ?: ""}") }

    fun addStep(step: String) { completedSteps.add(step); currentProgress = step; totalSteps++ }

    fun updateAction(desc: String, success: Boolean) {
        lastAction = desc; lastActionSuccess = success
        if (desc.contains("Wait", true) || desc.contains("等待")) consecutiveWaits++ else consecutiveWaits = 0
        if (!success) consecutiveFailures++ else consecutiveFailures = 0
    }

    fun buildHints(): List<String> {
        val hints = mutableListOf<String>()
        if (consecutiveWaits >= 2) hints.add("已等待${consecutiveWaits}次，请换一种操作")
        if (sameScreenCount >= 3) hints.add("界面连续${sameScreenCount}次无变化，上一步可能无效，请尝试不同策略")
        else if (sameScreenCount >= 2) hints.add("界面无变化，上一步可能无效")
        if (!lastActionSuccess) hints.add("上一步失败，请尝试其他方法")
        if (consecutiveFailures >= 3) hints.add("已连续失败${consecutiveFailures}次，建议返回重试或换路径")
        if (totalSteps > 30) hints.add("已执行${totalSteps}步，请尽快完成任务")
        return hints
    }

    fun isStuck(): Boolean = sameScreenCount >= 3 || consecutiveWaits >= 3 || consecutiveFailures >= 3

    fun getProgressSummary(): String = buildString {
        append("目标: ${originalGoal.take(30)}")
        targetApp?.let { append(" | 应用: $it") }
        append(" | 步骤: $totalSteps")
        if (hasEnteredTargetApp) append(" ✓已进入")
    }

    fun reset() {
        originalGoal = ""; targetApp = null; targetPackage = null
        completedSteps.clear(); currentProgress = ""
        hasEnteredTargetApp = false; sameScreenCount = 0; lastScreenHash = 0
        consecutiveWaits = 0; consecutiveFailures = 0; totalSteps = 0
        lastAction = null; lastActionSuccess = true
        screenSignature = ""
    }
}
