package com.orizon.openkiwi.core.device

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.Charset

data class NfcTagInfo(
    val id: String,
    val techList: List<String>,
    val ndefMessages: List<String>,
    val rawNdefRecords: List<NdefRecordInfo> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class NdefRecordInfo(
    val tnf: Short,
    val type: String,
    val payload: String,
    val payloadHex: String
)

class NfcSessionManager {

    companion object {
        private const val TAG = "NfcSessionMgr"
    }

    private val _lastTag = MutableStateFlow<NfcTagInfo?>(null)
    val lastTag: StateFlow<NfcTagInfo?> = _lastTag.asStateFlow()

    @Volatile
    private var lastRawTag: Tag? = null

    @Volatile
    private var foregroundEnabled = false

    fun enableForegroundDispatch(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        val intent = Intent(activity, activity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(activity, 0, intent, flags)
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply { addDataType("*/*") },
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )
        val techLists = arrayOf(
            arrayOf(Ndef::class.java.name),
            arrayOf(NdefFormatable::class.java.name),
            arrayOf(MifareClassic::class.java.name),
            arrayOf(MifareUltralight::class.java.name)
        )
        try {
            adapter.enableForegroundDispatch(activity, pendingIntent, filters, techLists)
            foregroundEnabled = true
        } catch (e: Exception) {
            Log.e(TAG, "enableForegroundDispatch failed", e)
        }
    }

    fun disableForegroundDispatch(activity: Activity) {
        if (!foregroundEnabled) return
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        try {
            adapter.disableForegroundDispatch(activity)
            foregroundEnabled = false
        } catch (e: Exception) {
            Log.e(TAG, "disableForegroundDispatch failed", e)
        }
    }

    fun handleIntent(intent: Intent): Boolean {
        val action = intent.action ?: return false
        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_TAG_DISCOVERED) return false

        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        } ?: return false

        lastRawTag = tag
        _lastTag.value = parseTag(tag, intent)
        Log.i(TAG, "NFC tag detected: ${_lastTag.value?.id}")
        return true
    }

    fun getLastRawTag(): Tag? = lastRawTag

    private fun parseTag(tag: Tag, intent: Intent): NfcTagInfo {
        val id = tag.id?.joinToString("") { "%02X".format(it) } ?: "unknown"
        val techList = tag.techList?.toList() ?: emptyList()

        val ndefMessages = mutableListOf<String>()
        val recordInfos = mutableListOf<NdefRecordInfo>()

        val rawMsgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }

        rawMsgs?.forEach { parcel ->
            val msg = parcel as? NdefMessage ?: return@forEach
            msg.records.forEach { record ->
                val payloadStr = parseNdefPayload(record)
                ndefMessages.add(payloadStr)
                recordInfos.add(NdefRecordInfo(
                    tnf = record.tnf,
                    type = String(record.type, Charsets.US_ASCII),
                    payload = payloadStr,
                    payloadHex = record.payload.joinToString("") { "%02x".format(it) }
                ))
            }
        }

        if (ndefMessages.isEmpty()) {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                try {
                    ndef.connect()
                    ndef.cachedNdefMessage?.records?.forEach { record ->
                        val payloadStr = parseNdefPayload(record)
                        ndefMessages.add(payloadStr)
                        recordInfos.add(NdefRecordInfo(
                            tnf = record.tnf,
                            type = String(record.type, Charsets.US_ASCII),
                            payload = payloadStr,
                            payloadHex = record.payload.joinToString("") { "%02x".format(it) }
                        ))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read NDEF from tag", e)
                } finally {
                    runCatching { ndef.close() }
                }
            }
        }

        return NfcTagInfo(id, techList, ndefMessages, recordInfos)
    }

    fun writeNdefText(text: String): Result<Unit> {
        val tag = lastRawTag ?: return Result.failure(Exception("No tag detected — hold a tag near the device first"))
        val record = NdefRecord.createTextRecord("en", text)
        val message = NdefMessage(arrayOf(record))
        return writeNdefMessage(tag, message)
    }

    fun writeNdefUri(uri: String): Result<Unit> {
        val tag = lastRawTag ?: return Result.failure(Exception("No tag detected — hold a tag near the device first"))
        val record = NdefRecord.createUri(uri)
        val message = NdefMessage(arrayOf(record))
        return writeNdefMessage(tag, message)
    }

    private fun writeNdefMessage(tag: Tag, message: NdefMessage): Result<Unit> {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                if (!ndef.isWritable) {
                    ndef.close()
                    return Result.failure(Exception("Tag is read-only"))
                }
                if (ndef.maxSize < message.toByteArray().size) {
                    ndef.close()
                    return Result.failure(Exception("Message too large (${message.toByteArray().size} > ${ndef.maxSize})"))
                }
                ndef.writeNdefMessage(message)
                ndef.close()
                Result.success(Unit)
            } catch (e: Exception) {
                runCatching { ndef.close() }
                Result.failure(e)
            }
        }

        val formatable = NdefFormatable.get(tag)
            ?: return Result.failure(Exception("Tag does not support NDEF"))
        return try {
            formatable.connect()
            formatable.format(message)
            formatable.close()
            Result.success(Unit)
        } catch (e: Exception) {
            runCatching { formatable.close() }
            Result.failure(e)
        }
    }

    private fun parseNdefPayload(record: NdefRecord): String {
        return when (record.tnf) {
            NdefRecord.TNF_WELL_KNOWN -> {
                if (record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                    val payload = record.payload
                    val encoding = if ((payload[0].toInt() and 0x80) == 0) Charsets.UTF_8 else Charsets.UTF_16
                    val langLen = payload[0].toInt() and 0x3F
                    String(payload, langLen + 1, payload.size - langLen - 1, encoding)
                } else if (record.type.contentEquals(NdefRecord.RTD_URI)) {
                    record.toUri()?.toString() ?: String(record.payload, Charsets.UTF_8)
                } else {
                    String(record.payload, Charsets.UTF_8)
                }
            }
            NdefRecord.TNF_ABSOLUTE_URI -> String(record.payload, Charsets.UTF_8)
            NdefRecord.TNF_MIME_MEDIA -> String(record.payload, Charsets.UTF_8)
            else -> record.payload.joinToString("") { "%02x".format(it) }
        }
    }
}
