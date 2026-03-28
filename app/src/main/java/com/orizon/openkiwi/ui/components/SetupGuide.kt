package com.orizon.openkiwi.ui.components

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.orizon.openkiwi.service.KiwiAccessibilityService
import com.orizon.openkiwi.service.FloatingWindowManager

data class PermissionItem(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    val isRequired: Boolean = true,
    val onRequest: () -> Unit
)

@Composable
fun SetupGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }
    var notificationEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var runtimePermsGranted by remember { mutableStateOf(checkRuntimePermissions(context)) }
    var batteryOptimized by remember { mutableStateOf(!isBatteryOptimizationIgnored(context)) }
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = isAccessibilityServiceEnabled(context)
                notificationEnabled = isNotificationListenerEnabled(context)
                runtimePermsGranted = checkRuntimePermissions(context)
                batteryOptimized = !isBatteryOptimizationIgnored(context)
                overlayGranted = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val runtimePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        runtimePermsGranted = checkRuntimePermissions(context)
    }

    val items = listOf(
        PermissionItem(
            name = "无障碍服务",
            description = "核心能力，找到 OpenKiwi 并开启",
            icon = Icons.Outlined.Accessibility,
            isGranted = accessibilityEnabled,
            isRequired = true,
            onRequest = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        ),
        PermissionItem(
            name = "快捷方式",
            description = "音量键快速恢复无障碍，强烈建议开启",
            icon = Icons.Outlined.VolumeUp,
            isGranted = false,
            isRequired = false,
            onRequest = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        ),
        PermissionItem(
            name = "电池优化",
            description = "防止系统杀后台，MIUI 必须",
            icon = Icons.Outlined.BatteryAlert,
            isGranted = !batteryOptimized,
            isRequired = false,
            onRequest = {
                try {
                    @Suppress("BatteryLife")
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            }
        ),
        PermissionItem(
            name = "自启动",
            description = "小米/MIUI 防止无障碍被反复关闭",
            icon = Icons.Outlined.RestartAlt,
            isGranted = false,
            isRequired = false,
            onRequest = { openMiuiAutoStartSettings(context) }
        ),
        PermissionItem(
            name = "悬浮窗",
            description = "任务状态、触摸指示器、思考气泡",
            icon = Icons.Outlined.PictureInPicture,
            isGranted = overlayGranted,
            isRequired = false,
            onRequest = { FloatingWindowManager.requestOverlayPermission(context) }
        ),
        PermissionItem(
            name = "通知监听",
            description = "让 Agent 读取通知",
            icon = Icons.Outlined.Notifications,
            isGranted = notificationEnabled,
            isRequired = false,
            onRequest = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        ),
        PermissionItem(
            name = "基础权限",
            description = "麦克风、位置、通讯录等",
            icon = Icons.Outlined.Shield,
            isGranted = runtimePermsGranted,
            isRequired = false,
            onRequest = {
                val perms = mutableListOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.CAMERA,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    perms.add(Manifest.permission.READ_MEDIA_IMAGES)
                    perms.add(Manifest.permission.READ_MEDIA_VIDEO)
                    perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                }
                runtimePermLauncher.launch(perms.toTypedArray())
            }
        )
    )

    val allRequired = items.filter { it.isRequired }.all { it.isGranted }

    AlertDialog(
        onDismissRequest = { if (allRequired) onDismiss() },
        title = { Text("初始设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!allRequired) {
                    Text(
                        "\u8BF7\u5148\u5F00\u542F\u65E0\u969C\u788D\u670D\u52A1",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                items.forEach { item -> SetupItem(item) }

                Text(
                    "建议开启「快捷方式」，长按音量键即可恢复无障碍",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, enabled = allRequired) {
                Text(if (allRequired) "开始使用" else "请先开启必需权限")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } }
    )
}

@Composable
private fun SetupItem(item: PermissionItem) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (item.isGranted) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                item.icon, null,
                modifier = Modifier.size(22.dp),
                tint = if (item.isGranted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    if (item.isRequired) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "\u00B7 \u5FC5\u9700",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            }
            Spacer(Modifier.width(6.dp))
            if (item.isGranted) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = item.onRequest, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("开启", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
    val myComponent = ComponentName(context, KiwiAccessibilityService::class.java)
    return enabledServices.any {
        it.resolveInfo.serviceInfo.let { si ->
            ComponentName(si.packageName, si.name) == myComponent
        }
    }
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: ""
    val myComponent = ComponentName(context, com.orizon.openkiwi.service.KiwiNotificationListener::class.java)
    return flat.contains(myComponent.flattenToString())
}

fun checkRuntimePermissions(context: Context): Boolean {
    val critical = listOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.READ_CONTACTS,
    )
    return critical.all {
        ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun openMiuiAutoStartSettings(context: Context) {
    val intents = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")),
        Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            putExtra("extra_pkgname", context.packageName)
        },
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    )
    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) { }
    }
}
