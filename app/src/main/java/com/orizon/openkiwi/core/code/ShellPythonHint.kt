package com.orizon.openkiwi.core.code

/**
 * Android shell has no `python` / `python3` on PATH; models often try shell_command first and mis-report.
 */
object ShellPythonHint {

    private val invokeSystemPython = Regex(
        """(^|[;&|]|&&|\n)\s*python3?\b"""
    )

    fun commandInvokesSystemPython(command: String): Boolean =
        invokeSystemPython.containsMatchIn(command.trim())

    const val USE_CODE_EXECUTION_ZH =
        "Android 的 shell 里没有 python/python3 可执行文件，不要在这里跑 Python。" +
            "请改用工具 **code_execution**：参数 language=python，code=要执行的源码。" +
            "Python 由应用内嵌的 Chaquopy 提供，与系统终端无关。"

    const val CHAQUOPY_INIT_FAILED_ZH =
        "应用内嵌 Python（Chaquopy）启动失败。本机同样没有独立的 python 命令。" +
            "可尝试：使用 arm64 真机/模拟器、清除应用数据后重装、确认安装完整 APK。" +
            "执行代码请仍使用 code_execution + language=python。"
}
