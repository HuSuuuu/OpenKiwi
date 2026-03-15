package com.orizon.openkiwi.core.gui

class TaskDecomposer {

    data class SubTask(val index: Int, val description: String, val isCompleted: Boolean = false)

    private val separators = listOf("然后", "之后", "接着", "再", "并且", "同时", "，", "；", ",", ";")

    private val actionPrefixes = listOf(
        "打开", "启动", "进入", "切换",
        "发送", "发", "写", "搜索", "查", "看", "查看", "下单", "买",
        "设置", "修改", "删除", "添加", "创建", "编辑"
    )

    fun decompose(task: String): List<SubTask> {
        val trimmed = task.trim()
        val bySepar = splitBySeparators(trimmed)
        if (bySepar.size > 1) {
            return bySepar.mapIndexed { i, d -> SubTask(i + 1, d.trim()) }.filter { it.description.isNotBlank() }
        }
        return listOf(SubTask(1, trimmed))
    }

    private fun splitBySeparators(task: String): List<String> {
        var result = listOf(task)
        for (sep in separators) {
            val newResult = mutableListOf<String>()
            for (part in result) {
                if (part.contains(sep)) newResult.addAll(part.split(sep)) else newResult.add(part)
            }
            result = newResult
        }
        return result.map { it.trim() }.filter { it.isNotBlank() }
    }

    fun formatSubTasks(subTasks: List<SubTask>): String {
        if (subTasks.size <= 1) return ""
        return subTasks.joinToString("\n") { t ->
            val status = if (t.isCompleted) "[x]" else "[ ]"
            "$status ${t.index}. ${t.description}"
        }
    }

    fun getCurrentTaskHint(subTasks: List<SubTask>, currentIndex: Int): String {
        if (subTasks.size <= 1) return ""
        val current = subTasks.getOrNull(currentIndex - 1) ?: return ""
        return "[${currentIndex}/${subTasks.size}] ${current.description}"
    }
}
