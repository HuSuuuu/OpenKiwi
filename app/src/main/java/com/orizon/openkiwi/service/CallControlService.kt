package com.orizon.openkiwi.service

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Full telephony control: answer, hangup, hold, DTMF.
 * Requires ANSWER_PHONE_CALLS, CALL_PHONE permissions.
 */
class CallControlService(private val context: Context) {

    companion object {
        private const val TAG = "CallControl"
    }

    fun answerCall(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecom.acceptRingingCall()
                true
            } else {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                @Suppress("DEPRECATION")
                audio.setMode(AudioManager.MODE_IN_CALL)
                val intent = android.content.Intent("android.intent.action.MEDIA_BUTTON")
                val event = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_HEADSETHOOK)
                intent.putExtra(android.content.Intent.EXTRA_KEY_EVENT, event)
                context.sendOrderedBroadcast(intent, "android.permission.CALL_PRIVILEGED")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "answerCall failed", e)
            false
        }
    }

    fun hangupCall(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                telecom.endCall()
            } else {
                Runtime.getRuntime().exec(arrayOf("input", "keyevent", "6"))
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "hangupCall failed", e)
            false
        }
    }

    fun sendDTMF(tone: Char): Boolean {
        return try {
            val toneGenerator = android.media.ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100)
            val toneType = when (tone) {
                '0' -> android.media.ToneGenerator.TONE_DTMF_0
                '1' -> android.media.ToneGenerator.TONE_DTMF_1
                '2' -> android.media.ToneGenerator.TONE_DTMF_2
                '3' -> android.media.ToneGenerator.TONE_DTMF_3
                '4' -> android.media.ToneGenerator.TONE_DTMF_4
                '5' -> android.media.ToneGenerator.TONE_DTMF_5
                '6' -> android.media.ToneGenerator.TONE_DTMF_6
                '7' -> android.media.ToneGenerator.TONE_DTMF_7
                '8' -> android.media.ToneGenerator.TONE_DTMF_8
                '9' -> android.media.ToneGenerator.TONE_DTMF_9
                '*' -> android.media.ToneGenerator.TONE_DTMF_S
                '#' -> android.media.ToneGenerator.TONE_DTMF_P
                else -> return false
            }
            toneGenerator.startTone(toneType, 200)
            toneGenerator.release()
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendDTMF failed", e)
            false
        }
    }

    fun getCallState(): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return when (tm.callState) {
            TelephonyManager.CALL_STATE_IDLE -> "idle"
            TelephonyManager.CALL_STATE_RINGING -> "ringing"
            TelephonyManager.CALL_STATE_OFFHOOK -> "offhook"
            else -> "unknown"
        }
    }

    fun toggleSpeakerphone(enable: Boolean) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.isSpeakerphoneOn = enable
    }

    fun toggleMute(mute: Boolean) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.isMicrophoneMute = mute
    }
}
