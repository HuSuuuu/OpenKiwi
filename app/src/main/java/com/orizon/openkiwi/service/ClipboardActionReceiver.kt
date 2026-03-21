package com.orizon.openkiwi.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.orizon.openkiwi.core.clipboard.ClipboardQuickBus

class ClipboardActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_CLIP_ACTION) ?: return
        val text = intent.getStringExtra(EXTRA_CLIP_TEXT) ?: return
        if (text.isBlank()) return
        ClipboardQuickBus.emit(action, text)
    }

    companion object {
        const val ACTION = "com.orizon.openkiwi.CLIPBOARD_QUICK"
        const val EXTRA_CLIP_ACTION = "clip_action"
        const val EXTRA_CLIP_TEXT = "clip_text"
    }
}
