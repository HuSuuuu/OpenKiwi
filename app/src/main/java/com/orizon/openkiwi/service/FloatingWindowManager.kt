package com.orizon.openkiwi.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

class FloatingWindowManager(private val context: Context) {

    companion object {
        fun requestOverlayPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
    }

    fun getOverlayPermissionIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
    }

    fun showFloatingWindow() {
        if (!hasOverlayPermission()) return
        FloatingWindowService.start(context)
    }

    fun hideFloatingWindow() {
        FloatingWindowService.stop(context)
    }

    fun isFloatingWindowVisible(): Boolean = FloatingWindowService.isRunning()

    fun toggleFloatingWindow() {
        if (isFloatingWindowVisible()) hideFloatingWindow() else showFloatingWindow()
    }
}
