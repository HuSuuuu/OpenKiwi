package com.orizon.openkiwi.ui.permissions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PermissionRequest(
    val toolName: String,
    val permissionLevel: String,
    val actionDetail: String,
    val params: Map<String, Any?> = emptyMap()
)

data class PermissionResponse(
    val toolName: String,
    val granted: Boolean
)

object PermissionDialogManager {
    private val _requests = MutableSharedFlow<PermissionRequest>(extraBufferCapacity = 5)
    val requests: SharedFlow<PermissionRequest> = _requests.asSharedFlow()

    private val _responses = MutableSharedFlow<PermissionResponse>(extraBufferCapacity = 5)
    val responses: SharedFlow<PermissionResponse> = _responses.asSharedFlow()

    suspend fun requestPermission(request: PermissionRequest) { _requests.emit(request) }
    suspend fun respond(response: PermissionResponse) { _responses.emit(response) }
}

@Composable
fun PermissionConfirmDialog(
    request: PermissionRequest,
    onConfirm: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("权限确认") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("以下操作需要确认：", style = MaterialTheme.typography.bodyMedium)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("工具: ${request.toolName}", style = MaterialTheme.typography.labelMedium)
                        Text("级别: ${request.permissionLevel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(Modifier.height(4.dp))
                        Text(request.actionDetail, style = MaterialTheme.typography.bodySmall)
                        if (request.params.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            request.params.forEach { (k, v) ->
                                Text("$k: $v", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("允许") }
        },
        dismissButton = { TextButton(onClick = onDeny) { Text("拒绝") } }
    )
}
