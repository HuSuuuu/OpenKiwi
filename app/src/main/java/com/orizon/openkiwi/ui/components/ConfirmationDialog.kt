package com.orizon.openkiwi.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Confirmation dialog for dangerous/sensitive tool operations.
 * Shows tool name, parameters, and permission level.
 */
@Composable
fun OperationConfirmDialog(
    toolName: String,
    parameters: Map<String, Any?>,
    permissionLevel: String,
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    val isHighRisk = permissionLevel == "SENSITIVE"

    AlertDialog(
        onDismissRequest = onDeny,
        icon = {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (isHighRisk) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                if (isHighRisk) "敏感操作确认" else "危险操作确认",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                Text(
                    "智能体请求执行以下操作:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "工具: $toolName",
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (parameters.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            parameters.forEach { (key, value) ->
                                Text(
                                    "$key: ${value?.toString()?.take(100) ?: "null"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = if (isHighRisk) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                "权限等级: $permissionLevel",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isHighRisk) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isHighRisk) ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ) else ButtonDefaults.buttonColors()
            ) {
                Text("允许执行")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDeny) {
                Text("拒绝")
            }
        }
    )
}
