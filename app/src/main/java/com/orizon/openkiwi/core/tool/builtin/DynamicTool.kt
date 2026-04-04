package com.orizon.openkiwi.core.tool.builtin

import com.orizon.openkiwi.core.code.CodeSandbox
import com.orizon.openkiwi.core.tool.*
import com.orizon.openkiwi.data.local.entity.CustomToolEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class DynamicTool(
    entity: CustomToolEntity,
    private val codeSandbox: CodeSandbox? = null
) : Tool {

    private val script = entity.script
    private val lang = entity.language

    override val definition: ToolDefinition

    init {
        val paramMap = runCatching {
            val raw: Map<String, Map<String, String>> =
                Json.decodeFromString(entity.paramsJson)
            raw.mapValues { (_, v) ->
                ToolParamDef(
                    type = v["type"] ?: "string",
                    description = v["description"] ?: "",
                    required = v["required"]?.toBooleanStrictOrNull() ?: false
                )
            }
        }.getOrDefault(emptyMap())

        val required = runCatching {
            Json.decodeFromString<List<String>>(entity.requiredParamsJson)
        }.getOrDefault(emptyList())

        definition = ToolDefinition(
            name = entity.name,
            description = entity.description,
            category = ToolCategory.CUSTOM.name,
            permissionLevel = PermissionLevel.NORMAL.name,
            parameters = paramMap,
            requiredParams = required,
            timeoutMs = 60_000
        )
    }

    override suspend fun execute(params: Map<String, Any?>): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            var expandedScript = script
            params.forEach { (k, v) ->
                expandedScript = expandedScript.replace("\${$k}", v?.toString() ?: "")
                expandedScript = expandedScript.replace("\$$k", v?.toString() ?: "")
            }

            when (lang.lowercase()) {
                "python", "py" -> executePython(expandedScript, params)
                else -> executeShell(expandedScript, params)
            }
        }.getOrElse { e ->
            ToolResult(toolName = definition.name, success = false, output = "", error = e.message)
        }
    }

    private suspend fun executeShell(expandedScript: String, params: Map<String, Any?>): ToolResult {
        val env = params.entries.associate { (k, v) -> "TOOL_$k" to (v?.toString() ?: "") }
        val pb = ProcessBuilder("sh", "-c", expandedScript)
            .redirectErrorStream(true)
        pb.environment().putAll(env)
        val process = pb.start()

        val completed = process.waitFor(60, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            return ToolResult(
                toolName = definition.name, success = false,
                output = "", error = "Script timed out after 60s"
            )
        }

        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        val exitCode = process.exitValue()

        return ToolResult(
            toolName = definition.name,
            success = exitCode == 0,
            output = output.take(10_000),
            error = if (exitCode != 0) "Exit code: $exitCode" else null
        )
    }

    private suspend fun executePython(expandedScript: String, params: Map<String, Any?>): ToolResult {
        val sandbox = codeSandbox
            ?: return ToolResult(definition.name, false, "", "Python runtime (Chaquopy) not available")

        val preamble = buildString {
            appendLine("import os")
            params.forEach { (k, v) ->
                val escaped = (v?.toString() ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
                appendLine("$k = \"$escaped\"")
                appendLine("os.environ['TOOL_$k'] = \"$escaped\"")
            }
        }
        val fullCode = preamble + expandedScript

        val execResult = sandbox.executePythonLocal(fullCode)
        return ToolResult(
            toolName = definition.name,
            success = execResult.exitCode == 0,
            output = execResult.stdout.take(10_000),
            error = if (execResult.exitCode != 0) execResult.stderr.take(2000) else null,
            executionTimeMs = execResult.executionTimeMs
        )
    }
}
