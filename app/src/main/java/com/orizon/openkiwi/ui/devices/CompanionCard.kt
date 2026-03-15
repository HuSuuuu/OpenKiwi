package com.orizon.openkiwi.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.orizon.openkiwi.network.CompanionServer

@Composable
fun CompanionCard(server: CompanionServer) {
    var isRunning by remember { mutableStateOf(server.isRunning()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Computer, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("电脑连接", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))

            if (isRunning) {
                val url = server.getUrl()
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("浏览器打开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        Text(url, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("确保设备在同一网络", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { server.stop(); isRunning = false }) {
                    Text("停止", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Text("启动后可在电脑浏览器对话", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                Spacer(Modifier.height(10.dp))
                Button(onClick = { server.start(); isRunning = true }) {
                    Text("启动", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
