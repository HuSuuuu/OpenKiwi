package com.orizon.openkiwi.core.security

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Manages device authentication (PIN/pattern/fingerprint/face) for sensitive operations.
 * Uses Android's native KeyguardManager – no extra dependencies required.
 */
class BiometricAuthManager(private val context: Context) {

    companion object {
        private const val TAG = "BiometricAuth"
    }

    fun canAuthenticate(): BiometricStatus {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return when {
            !km.isDeviceSecure -> BiometricStatus.NOT_ENROLLED
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && km.isDeviceSecure -> BiometricStatus.AVAILABLE
            else -> BiometricStatus.AVAILABLE
        }
    }

    /**
     * Creates a confirm-credential intent for the calling Activity to launch.
     * Returns null if the device has no secure lock screen.
     *
     * Usage in an Activity / Composable:
     * ```
     * val intent = biometricAuthManager.createAuthIntent("身份验证", "请验证以继续")
     * if (intent != null) launcher.launch(intent)   // ActivityResultLauncher
     * ```
     */
    fun createAuthIntent(
        title: String = "身份验证",
        description: String = "请验证您的身份以继续敏感操作"
    ): android.content.Intent? {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isDeviceSecure) {
            Log.w(TAG, "Device has no secure lock screen")
            return null
        }
        @Suppress("DEPRECATION")
        return km.createConfirmDeviceCredentialIntent(title, description)
    }

    fun isDeviceSecure(): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return km.isDeviceSecure
    }
}

enum class BiometricStatus {
    AVAILABLE, NO_HARDWARE, UNAVAILABLE, NOT_ENROLLED, UNKNOWN
}
