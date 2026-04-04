package com.orizon.openkiwi.core.gui

import android.util.Log
import kotlinx.serialization.json.*

class GuiActionParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val tag = "GuiActionParser"

    fun parse(response: String): GuiAgentResponse {
        return tryParseJsonArray(response)
            ?: tryParseAutoGLM(response)
            ?: tryParseUITars(response)
            ?: tryParseDirect(response)
            ?: run {
                Log.w(tag, "All parsers failed, using fallback. Response: ${response.take(300)}")
                GuiAgentResponse("Fallback", fallbackParse(response), response)
            }
    }

    private fun tryParseJsonArray(response: String): GuiAgentResponse? {
        val start = response.indexOf("[{")
        if (start == -1) return null
        val end = findMatchingBracket(response, start) ?: return null
        val jsonStr = response.substring(start, end + 1)
        return try {
            val arr = json.parseToJsonElement(jsonStr).jsonArray
            val actions = arr.mapNotNull { parseJsonAction(it.jsonObject) }
            if (actions.isEmpty()) return null
            val action = if (actions.size == 1) actions.first() else GuiAction.Batch(actions)
            GuiAgentResponse("(JSON ${actions.size} actions)", action, response)
        } catch (e: Exception) {
            tryParseIncompleteJson(jsonStr, response)
        }
    }

    private fun findMatchingBracket(s: String, startIdx: Int): Int? {
        var depth = 0
        for (i in startIdx until s.length) {
            when (s[i]) {
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return i }
            }
        }
        return null
    }

    private fun parseJsonAction(obj: JsonObject): GuiAction? {
        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return null
        val params = obj["parameters"]?.jsonObject
        return when (name.lowercase()) {
            "tap", "click" -> {
                val el = params?.get("element")?.jsonArray
                if (el != null && el.size >= 2) GuiAction.Tap(el[0].jsonPrimitive.int, el[1].jsonPrimitive.int)
                else {
                    val x = params?.get("x")?.jsonPrimitive?.intOrNull ?: 500
                    val y = params?.get("y")?.jsonPrimitive?.intOrNull ?: 500
                    GuiAction.Tap(x, y)
                }
            }
            "long press", "longpress", "long_press" -> {
                val el = params?.get("element")?.jsonArray
                if (el != null && el.size >= 2) GuiAction.LongPress(el[0].jsonPrimitive.int, el[1].jsonPrimitive.int) else null
            }
            "double tap", "doubletap", "double_tap" -> {
                val el = params?.get("element")?.jsonArray
                if (el != null && el.size >= 2) GuiAction.DoubleTap(el[0].jsonPrimitive.int, el[1].jsonPrimitive.int) else null
            }
            "swipe" -> {
                val s = params?.get("start")?.jsonArray; val e = params?.get("end")?.jsonArray
                if (s != null && e != null && s.size >= 2 && e.size >= 2)
                    GuiAction.Swipe(s[0].jsonPrimitive.int, s[1].jsonPrimitive.int, e[0].jsonPrimitive.int, e[1].jsonPrimitive.int)
                else null
            }
            "type", "input" -> GuiAction.Type(params?.get("text")?.jsonPrimitive?.contentOrNull ?: "")
            "launch", "open", "open_app" -> {
                val app = params?.get("app")?.jsonPrimitive?.contentOrNull
                    ?: params?.get("app_name")?.jsonPrimitive?.contentOrNull
                    ?: params?.get("packageName")?.jsonPrimitive?.contentOrNull ?: return null
                GuiAction.Launch(app)
            }
            "back" -> GuiAction.Back()
            "home" -> GuiAction.Home()
            "wait" -> {
                val dur = params?.get("duration")?.jsonPrimitive?.longOrNull
                    ?: params?.get("ms")?.jsonPrimitive?.longOrNull
                GuiAction.Wait(dur?.coerceIn(500, 15000) ?: 3000)
            }
            "takeover", "take_over" -> GuiAction.Takeover(params?.get("message")?.jsonPrimitive?.contentOrNull ?: "User intervention needed")
            "finish", "done" -> GuiAction.Finish(params?.get("message")?.jsonPrimitive?.contentOrNull ?: "Task completed")
            else -> null
        }
    }

    private fun tryParseIncompleteJson(jsonStr: String, full: String): GuiAgentResponse? {
        val nameMatch = Regex(""""name"\s*:\s*"(\w+)"""").find(jsonStr) ?: return null
        val name = nameMatch.groupValues[1]
        val action = when (name.lowercase()) {
            "tap", "click" -> {
                val m = Regex(""""element"\s*:\s*\[\s*(\d+)\s*,\s*(\d+)""").find(jsonStr)
                if (m != null) {
                    GuiAction.Tap(m.groupValues[1].toInt(), m.groupValues[2].toInt())
                } else {
                    val xm = Regex(""""x"\s*:\s*(\d+)""").find(jsonStr)
                    val ym = Regex(""""y"\s*:\s*(\d+)""").find(jsonStr)
                    if (xm != null && ym != null) GuiAction.Tap(xm.groupValues[1].toInt(), ym.groupValues[1].toInt()) else null
                }
            }
            "launch" -> {
                val m = Regex(""""(?:app|appName|app_name|packageName)"\s*:\s*"([^"]+)"""").find(jsonStr)
                if (m != null) GuiAction.Launch(m.groupValues[1]) else null
            }
            "type" -> {
                val m = Regex(""""text"\s*:\s*"([^"]*)"?""").find(jsonStr)
                GuiAction.Type(m?.groupValues?.get(1) ?: "")
            }
            "back" -> GuiAction.Back()
            "home" -> GuiAction.Home()
            "finish" -> GuiAction.Finish(Regex(""""message"\s*:\s*"([^"]*)"?""").find(jsonStr)?.groupValues?.get(1) ?: "Done")
            else -> null
        } ?: return null
        return GuiAgentResponse("(Incomplete JSON)", action, full)
    }

    private fun tryParseAutoGLM(response: String): GuiAgentResponse? {
        val think = Regex("""<think>(.*?)</think>""", RegexOption.DOT_MATCHES_ALL).find(response)?.groupValues?.get(1)?.trim() ?: ""
        val answer = Regex("""<answer>(.*?)</answer>""", RegexOption.DOT_MATCHES_ALL).find(response)?.groupValues?.get(1)?.trim() ?: return null
        val actions = parseMultipleDo(answer)
        if (actions.isEmpty()) {
            val single = parseDoAction(answer) ?: return null
            return GuiAgentResponse(think, single, response)
        }
        val finalAction = if (actions.size == 1) actions.first() else GuiAction.Batch(actions)
        return GuiAgentResponse(think, finalAction, response)
    }

    private fun parseMultipleDo(answer: String): List<GuiAction> {
        return Regex("""(do|finish)\s*\([^)]+\)""", RegexOption.IGNORE_CASE).findAll(answer).mapNotNull { parseDoAction(it.value.trim()) }.toList()
    }

    private fun parseDoAction(s: String): GuiAction? {
        val trimmed = s.trim()
        if (trimmed.startsWith("finish")) {
            val msg = Regex("""finish\s*\(\s*message\s*=\s*["'](.*)["']\s*\)""", RegexOption.DOT_MATCHES_ALL).find(trimmed)?.groupValues?.get(1) ?: "Task completed"
            return GuiAction.Finish(msg)
        }
        if (!trimmed.startsWith("do")) return null
        val type = Regex("""action\s*=\s*["'](\w+(?:\s+\w+)?)["']""").find(trimmed)?.groupValues?.get(1) ?: return null
        return when (type) {
            "Tap" -> extractPoint(trimmed)?.let { (x, y) -> GuiAction.Tap(x, y) }
            "Long Press" -> extractPoint(trimmed)?.let { (x, y) -> GuiAction.LongPress(x, y) }
            "Double Tap" -> extractPoint(trimmed)?.let { (x, y) -> GuiAction.DoubleTap(x, y) }
            "Swipe" -> {
                val st = extractCoords(trimmed, "start"); val en = extractCoords(trimmed, "end")
                if (st != null && en != null) GuiAction.Swipe(st.first, st.second, en.first, en.second) else null
            }
            "Type", "Type_Name" -> {
                val text = Regex("""text\s*=\s*["'](.*?)["']""", RegexOption.DOT_MATCHES_ALL).find(trimmed)?.groupValues?.get(1) ?: ""
                GuiAction.Type(text)
            }
            "Launch" -> {
                val app = Regex("""app\s*=\s*["'](.+?)["']""").find(trimmed)?.groupValues?.get(1) ?: return null
                GuiAction.Launch(app)
            }
            "Back" -> GuiAction.Back()
            "Home" -> GuiAction.Home()
            "Wait" -> {
                val dur = Regex("""(?:duration|ms|time)\s*=\s*(\d+)""").find(trimmed)?.groupValues?.get(1)?.toLongOrNull()
                GuiAction.Wait(dur?.coerceIn(500, 15000) ?: 3000)
            }
            "Take_over" -> {
                val reason = Regex("""message\s*=\s*["'](.+?)["']""").find(trimmed)?.groupValues?.get(1) ?: "User intervention needed"
                GuiAction.Takeover(reason)
            }
            else -> null
        }
    }

    private fun extractPoint(text: String): Pair<Int, Int>? {
        for (p in listOf(
            Regex("""element\s*=\s*\[\s*(\d+)\s*,\s*(\d+)\s*]"""),
            Regex("""element\s*=\s*\[\s*(\d+)\s+(\d+)\s*]""")
        )) {
            p.find(text)?.let { return Pair(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        }
        val xMatch = Regex("""\bx\s*=\s*(\d+)""").find(text)
        val yMatch = Regex("""\by\s*=\s*(\d+)""").find(text)
        if (xMatch != null && yMatch != null) {
            return Pair(xMatch.groupValues[1].toInt(), yMatch.groupValues[1].toInt())
        }
        Regex("""\(\s*(\d+)\s*,\s*(\d+)\s*\)""").find(text)?.let {
            return Pair(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        return null
    }

    private fun extractCoords(text: String, key: String): Pair<Int, Int>? {
        for (p in listOf(Regex("""$key\s*=\s*\[\s*(\d+)\s*,\s*(\d+)\s*]"""), Regex("""$key\s*=\s*\[\s*(\d+)\s+(\d+)\s*]"""))) {
            p.find(text)?.let { return Pair(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        }
        return null
    }

    private fun tryParseUITars(response: String): GuiAgentResponse? {
        val think = Regex("""(?:Thought|Think)\s*:\s*(.+?)(?=\nAction|\n$|$)""", RegexOption.DOT_MATCHES_ALL).find(response)?.groupValues?.get(1)?.trim() ?: ""
        val actionStr = Regex("""Action\s*:\s*(.+)""", RegexOption.DOT_MATCHES_ALL).find(response)?.groupValues?.get(1)?.trim() ?: return null
        val action = parseUITarsAction(actionStr)
        if (action is GuiAction.Error && think.isEmpty()) return null
        return GuiAgentResponse(think, action, response)
    }

    private fun parseUITarsAction(s: String): GuiAction {
        val t = s.trim()
        Regex("""click\s*\(\s*point\s*=\s*['"]?<point>(\d+)[\s,]+(\d+)</point>['"]?\s*\)""", RegexOption.IGNORE_CASE).find(t)?.let {
            return GuiAction.Tap(it.groupValues[1].toInt(), it.groupValues[2].toInt(), isPixel = true)
        }
        Regex("""open_app\s*\(\s*app_name\s*=\s*['"](.+?)['"]?\s*\)""", RegexOption.IGNORE_CASE).find(t)?.let {
            return GuiAction.Launch(it.groupValues[1])
        }
        if (t.contains(Regex("""press_back\s*\(\s*\)""", RegexOption.IGNORE_CASE))) return GuiAction.Back()
        if (t.contains(Regex("""press_home\s*\(\s*\)""", RegexOption.IGNORE_CASE))) return GuiAction.Home()
        if (t.contains(Regex("""wait\s*\(\s*\)""", RegexOption.IGNORE_CASE))) return GuiAction.Wait()
        Regex("""finished\s*\(\s*content\s*=\s*['"](.*)['"]?\s*\)""", RegexOption.IGNORE_CASE).find(t)?.let {
            return GuiAction.Finish(it.groupValues[1])
        }
        return GuiAction.Error("Unknown UI-TARS action: $s")
    }

    private fun tryParseDirect(response: String): GuiAgentResponse? {
        val t = response.trim()
        if (!t.startsWith("do") && !t.startsWith("finish")) return null
        val action = parseDoAction(t) ?: return null
        return GuiAgentResponse("(Direct action)", action, response)
    }

    private fun fallbackParse(response: String): GuiAction {
        val lower = response.lowercase()

        for (p in listOf(
            Regex("""\[\s*(\d+)\s*,\s*(\d+)\s*]"""),
            Regex("""\(\s*(\d+)\s*,\s*(\d+)\s*\)"""),
            Regex("""(?:click|tap)[\s(]+(\d+)[\s,]+(\d+)""", RegexOption.IGNORE_CASE)
        )) {
            p.find(response)?.let { return GuiAction.Tap(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        }

        val xFb = Regex("""\bx\s*=\s*(\d+)""", RegexOption.IGNORE_CASE).find(response)
        val yFb = Regex("""\by\s*=\s*(\d+)""", RegexOption.IGNORE_CASE).find(response)
        if (xFb != null && yFb != null && lower.let { it.contains("tap") || it.contains("click") || it.contains("element") }) {
            return GuiAction.Tap(xFb.groupValues[1].toInt(), yFb.groupValues[1].toInt())
        }

        Regex("""swipe.*?(\d+)[\s,]+(\d+).*?(\d+)[\s,]+(\d+)""", RegexOption.IGNORE_CASE).find(response)?.let {
            return GuiAction.Swipe(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt(), it.groupValues[4].toInt())
        }
        for (p in listOf(Regex("""type[\s(]+['"](.+?)['"]""", RegexOption.IGNORE_CASE), Regex("""text\s*=\s*['"](.+?)['"]""", RegexOption.IGNORE_CASE))) {
            p.find(response)?.let { val t = it.groupValues[1]; if (t.isNotBlank()) return GuiAction.Type(t) }
        }
        return when {
            lower.contains("finish") || lower.contains("完成") || lower.contains("done") -> GuiAction.Finish(response.take(80))
            lower.contains("back") || lower.contains("返回") -> GuiAction.Back()
            lower.contains("home") || lower.contains("桌面") -> GuiAction.Home()
            lower.contains("scroll down") || lower.contains("下滑") -> GuiAction.Swipe(500, 700, 500, 300)
            lower.contains("scroll up") || lower.contains("上滑") -> GuiAction.Swipe(500, 300, 500, 700)
            else -> GuiAction.Wait(3000)
        }
    }
}
