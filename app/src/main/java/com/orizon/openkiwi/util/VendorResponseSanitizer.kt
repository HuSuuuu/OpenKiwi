package com.orizon.openkiwi.util

/**
 * Some gateways stream **pseudo** tool JSON inside `content`:
 * `<|FunctionCallBegin|>…<|FunctionCallEnd|>` instead of OpenAI `tool_calls`.
 * Markers may be split across SSE chunks; spacing inside `<| … |>` may vary.
 */
object VendorResponseSanitizer {

    /** Allows spaces: `<| FunctionCallBegin |>` */
    private val beginMarker = Regex("""<\|\s*FunctionCallBegin\s*\|>""", RegexOption.IGNORE_CASE)
    private val endMarker = Regex("""<\|\s*FunctionCallEnd\s*\|>""", RegexOption.IGNORE_CASE)

    private val extraBlocks = listOf(
        Regex("""<function_calls>[\s\S]*?</function_calls>""", RegexOption.IGNORE_CASE),
        Regex("""<tool_call>[\s\S]*?</tool_call>""", RegexOption.IGNORE_CASE),
    )

    /** Strip pseudo blocks but keep trailing spaces (for streaming prefix / delta math). */
    fun stripPseudoFunctionCallBlocksRaw(text: String): String {
        if (text.isEmpty()) return text
        var s = stripBalancedPseudoBlocks(text)
        for (r in extraBlocks) {
            s = r.replace(s, "")
        }
        return s
    }

    /**
     * Remove complete pseudo-call blocks; if a Begin has no matching End yet, drop from Begin to EOF
     * (streaming-safe: hides partial garbage until End arrives).
     */
    fun stripPseudoFunctionCallBlocks(text: String): String =
        stripPseudoFunctionCallBlocksRaw(text).trimEnd()

    private fun stripBalancedPseudoBlocks(input: String): String {
        var s = input
        while (true) {
            val bm = beginMarker.find(s) ?: break
            val em = endMarker.find(s, startIndex = bm.range.last + 1)
            if (em == null) {
                return s.substring(0, bm.range.first)
            }
            s = s.removeRange(bm.range.first, em.range.last + 1)
        }
        return s
    }

    /**
     * Streaming: after each raw append to the assistant buffer, emit only the **new** visible suffix
     * so split `<|FunctionCallBegin|>` / `End` across chunks still scrubs correctly.
     */
    class StreamScrubber {
        private var lastClean: String = ""

        fun deltaForCurrentBuffer(rawBuffer: StringBuilder): String {
            val clean = stripPseudoFunctionCallBlocksRaw(rawBuffer.toString())
            if (!clean.startsWith(lastClean)) {
                lastClean = ""
            }
            val d = clean.substring(lastClean.length)
            lastClean = clean
            return d
        }

        fun reset() {
            lastClean = ""
        }
    }
}
