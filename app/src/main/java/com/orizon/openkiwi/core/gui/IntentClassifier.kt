package com.orizon.openkiwi.core.gui

class IntentClassifier {

    enum class IntentType { TASK, CHAT, HYBRID, QUICK_ACTION, CLARIFICATION }

    data class IntentResult(
        val type: IntentType,
        val confidence: Float,
        val taskDescription: String?,
        val chatResponse: String?,
        val clarificationQuestion: String?
    )

    private val taskVerbs = setOf(
        "打开", "关闭", "启动", "退出", "切换",
        "点击", "点", "按", "滑动", "滑", "长按", "双击",
        "输入", "填写", "写", "搜索", "查找",
        "发", "发送", "转发", "分享", "群发",
        "设置", "设", "调", "改", "修改", "开启", "关掉",
        "看", "查", "查看", "检查", "获取", "下载", "保存",
        "付款", "支付", "转账", "充值",
        "打电话", "拨打", "发微信", "发短信"
    )

    private val appKeywords = setOf(
        "微信", "微博", "抖音", "淘宝", "支付宝", "美团", "饿了么",
        "京东", "拼多多", "bilibili", "B站", "哔哩哔哩",
        "QQ", "钉钉", "飞书", "滴滴", "高德", "百度",
        "网易云", "QQ音乐", "酷狗", "喜马拉雅",
        "设置", "相机", "相册", "日历", "闹钟", "备忘录",
        "电话", "短信", "联系人", "浏览器"
    )

    private val chatPatterns = listOf(
        "你是谁", "你叫什么", "你能做什么", "你会什么",
        "什么是", "怎么理解", "解释一下", "告诉我", "介绍一下",
        "为什么", "是什么意思", "有什么区别",
        "你觉得", "你认为", "你的看法", "建议",
        "谢谢", "好的", "明白", "懂了",
        "你好", "嗨", "hello", "hi",
        "晚安", "早安", "再见",
        "是的", "对的", "不是", "不对", "行", "可以", "不行",
        "无聊", "陪我聊天", "聊聊", "说说"
    )

    private val questionPatterns = listOf(
        "是什么", "是谁", "在哪里", "什么时候",
        "多少", "多大", "多长", "多远",
        "有没有", "能不能", "会不会",
        "哪个", "哪些", "哪里",
        "怎么回事", "什么情况", "发生了什么"
    )

    fun classify(userInput: String): IntentResult {
        val input = userInput.trim().lowercase()

        if (isSimpleGreeting(input)) {
            return IntentResult(IntentType.CHAT, 0.95f, null, generateChatResponse(input), null)
        }

        val chatScore = calculateChatScore(input)
        val taskScore = calculateTaskScore(input)

        if (chatScore > taskScore && chatScore >= 0.4f) {
            return IntentResult(IntentType.CHAT, chatScore, null, null, null)
        }

        if (taskScore >= 0.5f) {
            return IntentResult(IntentType.TASK, taskScore, userInput, null, null)
        }

        return IntentResult(IntentType.CHAT, 0.5f, null, null, null)
    }

    private fun isSimpleGreeting(input: String): Boolean {
        val greetings = setOf("你好", "嗨", "hi", "hello", "hey", "早", "早安", "晚安", "再见", "拜拜", "谢谢", "感谢", "好的", "明白", "懂了", "ok")
        return greetings.contains(input) || input.length <= 3
    }

    private fun calculateChatScore(input: String): Float {
        var score = 0f
        if (questionPatterns.any { input.contains(it) }) score += 0.5f
        if (chatPatterns.any { input.contains(it) }) score += 0.4f
        if ((input.endsWith("?") || input.endsWith("？")) && input.length < 30) score += 0.2f
        if (!appKeywords.any { input.contains(it) } && input.length < 25) score += 0.1f
        return score.coerceIn(0f, 1f)
    }

    private fun calculateTaskScore(input: String): Float {
        var score = 0f
        if (taskVerbs.any { input.contains(it) }) score += 0.4f
        if (appKeywords.any { input.contains(it) }) score += 0.3f
        if (input.contains("给") || input.contains("到") || input.contains("在") || input.contains("去")) score += 0.2f
        if (Regex("[\"'「」《》]").containsMatchIn(input) || input.contains("：") || input.contains(":")) score += 0.1f
        return score.coerceIn(0f, 1f)
    }

    private fun generateChatResponse(input: String): String = when {
        input.contains("你是谁") || input.contains("你叫什么") -> "我是你的 AI 助理，可以帮你操作手机完成各种任务。比如发微信、设闹钟、打开应用等。"
        input.contains("你能做什么") -> "我可以帮你：\n📱 操作应用（发微信、刷抖音）\n⏰ 设置提醒和闹钟\n🔍 搜索和查询信息\n⚙️ 调整手机设置\n\n直接告诉我你想做什么吧！"
        input.contains("谢谢") -> "不客气！有需要随时叫我～"
        input.contains("你好") || input.contains("嗨") || input.contains("hello") -> "你好！有什么可以帮你的吗？"
        input.contains("晚安") -> "晚安，好梦！"
        input.contains("早安") -> "早安！新的一天，有什么任务需要我帮忙吗？"
        else -> "我是你的手机 AI 助理，告诉我你想做什么，我来帮你操作～"
    }

    fun isLikelyTask(input: String): Boolean {
        val lower = input.lowercase()
        return taskVerbs.any { lower.contains(it) } || appKeywords.any { lower.contains(it) }
    }
}
