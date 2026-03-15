package com.orizon.openkiwi.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Transparent Activity solely for requesting MediaProjection permission.
 * Avoids MIUI / HyperOS closing the main Activity when granting permission.
 */
class ScreenCaptureRequestActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ScreenCaptureReq"

        @Volatile
        var pendingManager: ScreenCaptureManager? = null

        fun start(context: Context, manager: ScreenCaptureManager) {
            pendingManager = manager
            context.startActivity(
                Intent(context, ScreenCaptureRequestActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            pendingManager?.handleActivityResult(result.resultCode, result.data)
            Log.d(TAG, "Permission granted")
        } else {
            Log.w(TAG, "Permission denied")
        }
        pendingManager = null
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(pm.createScreenCaptureIntent())
    }
}
