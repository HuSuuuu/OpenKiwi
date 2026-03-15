package com.orizon.openkiwi.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openBatterySettings(context)
        }
    }

    fun openBatterySettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (_: Exception) {
            openAppDetailSettings(context)
        }
    }

    fun openAppDetailSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}
    }

    fun isMiui(): Boolean = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod("get", String::class.java)
        val value = method.invoke(null, "ro.miui.ui.version.name") as? String
        value.isNullOrEmpty().not()
    } catch (e: Exception) { false }

    fun getSettingsInstructions(context: Context): String = if (isMiui()) {
        "请在设置中将本应用设为[无限制]后台运行：\n\n设置 → 应用设置 → 应用管理 → 找到\"OpenKiwi\" → 省电策略 → 无限制\n\n或：长按应用图标 → 应用信息 → 省电策略 → 无限制"
    } else {
        "请在设置中关闭本应用的电池优化：\n\n设置 → 电池 → 电池优化 → 所有应用 → 找到\"OpenKiwi\" → 不优化"
    }
}
