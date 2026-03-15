package com.orizon.openkiwi.core.gui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TakeoverManager {

    companion object {
        private val SENSITIVE_APPS = setOf(
            "com.eg.android.AlipayGphone", "com.tencent.mm",
            "com.android.settings", "com.android.vending", "com.xiaomi.market"
        )

        private val SENSITIVE_KEYWORDS = listOf(
            "登录", "登陆", "注册", "验证码", "密码", "账号", "手机号",
            "login", "sign in", "register", "password", "verification",
            "支付", "付款", "转账", "充值", "购买", "付费", "红包",
            "pay", "payment", "transfer", "purchase",
            "删除", "注销", "解绑", "清空", "格式化", "卸载",
            "delete", "remove", "uninstall", "format",
            "授权", "权限", "允许", "同意", "permission", "authorize", "allow"
        )

        const val REASON_LOGIN = "login"
        const val REASON_PAYMENT = "payment"
        const val REASON_SENSITIVE = "sensitive"
        const val REASON_CAPTCHA = "captcha"
        const val REASON_UNCERTAIN = "uncertain"
    }

    sealed class TakeoverState {
        object Idle : TakeoverState()
        data class Requested(val reason: String, val reasonType: String, val instruction: String, val timestamp: Long = System.currentTimeMillis()) : TakeoverState()
        data class InProgress(val startTime: Long = System.currentTimeMillis()) : TakeoverState()
        data class Completed(val success: Boolean, val message: String?) : TakeoverState()
    }

    private val _takeoverState = MutableStateFlow<TakeoverState>(TakeoverState.Idle)
    val takeoverState: StateFlow<TakeoverState> = _takeoverState.asStateFlow()

    private var onTakeoverComplete: ((Boolean, String?) -> Unit)? = null

    data class TakeoverCheck(val needsTakeover: Boolean, val reason: String?, val reasonType: String)
    data class TakeoverHint(val title: String, val subtitle: String, val icon: String, val buttonText: String)

    fun checkNeedsTakeover(currentApp: String?, screenText: String?, aiSuggestion: String?): TakeoverCheck {
        val inSensitiveApp = currentApp?.let { pkg -> SENSITIVE_APPS.any { pkg.contains(it, ignoreCase = true) } } ?: false
        val hasSensitiveContent = screenText?.let { text -> SENSITIVE_KEYWORDS.any { text.contains(it, ignoreCase = true) } } ?: false
        val aiSuggestsTakeover = aiSuggestion?.let { it.contains("takeover", true) || it.contains("take_over", true) || it.contains("用户接管", true) } ?: false

        val detectedScenario = detectScenario(screenText)
        val needsTakeover = aiSuggestsTakeover || detectedScenario != null || (inSensitiveApp && hasSensitiveContent)
        val reason = when {
            aiSuggestsTakeover -> "AI 请求用户接管"
            detectedScenario != null -> detectedScenario
            else -> "检测到敏感操作"
        }
        return TakeoverCheck(needsTakeover, reason, classifyReason(reason))
    }

    private fun detectScenario(screenText: String?): String? {
        if (screenText == null) return null
        val lower = screenText.lowercase()
        return when {
            lower.contains("验证码") || lower.contains("verification code") -> "需要输入验证码"
            lower.contains("滑动验证") || lower.contains("拖动") -> "需要完成滑动验证"
            lower.contains("人脸") || lower.contains("face") -> "需要人脸识别验证"
            lower.contains("指纹") || lower.contains("fingerprint") -> "需要指纹验证"
            lower.contains("输入密码") || lower.contains("支付密码") -> "需要输入支付密码"
            lower.contains("确认支付") || lower.contains("confirm payment") -> "需要确认支付"
            lower.contains("是否允许") || lower.contains("授权") -> "需要确认权限授权"
            else -> null
        }
    }

    private fun classifyReason(reason: String): String {
        val lower = reason.lowercase()
        return when {
            lower.contains("验证码") || lower.contains("登录") -> REASON_LOGIN
            lower.contains("支付") || lower.contains("密码") -> REASON_PAYMENT
            lower.contains("删除") || lower.contains("敏感") -> REASON_SENSITIVE
            lower.contains("滑动") || lower.contains("人脸") -> REASON_CAPTCHA
            else -> REASON_UNCERTAIN
        }
    }

    fun requestTakeover(reason: String, instruction: String = "请完成此操作后点击\"继续\"") {
        _takeoverState.value = TakeoverState.Requested(reason, classifyReason(reason), instruction)
    }

    fun startTakeover() { _takeoverState.value = TakeoverState.InProgress() }

    fun completeTakeover(success: Boolean = true, message: String? = null) {
        _takeoverState.value = TakeoverState.Completed(success, message)
        onTakeoverComplete?.invoke(success, message)
    }

    fun reset() { _takeoverState.value = TakeoverState.Idle; onTakeoverComplete = null }

    fun setOnCompleteCallback(callback: (Boolean, String?) -> Unit) { onTakeoverComplete = callback }

    fun getTakeoverHint(reasonType: String): TakeoverHint = when (reasonType) {
        REASON_LOGIN -> TakeoverHint("需要登录验证", "请完成验证后点击继续", "🔐", "已完成验证")
        REASON_PAYMENT -> TakeoverHint("需要支付确认", "请完成支付操作后点击继续", "💳", "已完成支付")
        REASON_CAPTCHA -> TakeoverHint("需要人机验证", "请完成验证后点击继续", "🤖", "已通过验证")
        REASON_SENSITIVE -> TakeoverHint("敏感操作确认", "请确认操作后点击继续", "⚠️", "已确认操作")
        else -> TakeoverHint("需要你的帮助", "请完成操作后点击继续", "👆", "继续执行")
    }
}
