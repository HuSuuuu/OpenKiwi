package com.orizon.openkiwi.core.device

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class USBDeviceInfo(
    val name: String,
    val vendorId: Int,
    val productId: Int,
    val deviceId: Int,
    val deviceClass: Int,
    val interfaceCount: Int,
    val hasPermission: Boolean
)

class USBHostManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val _devices = MutableStateFlow<List<USBDeviceInfo>>(emptyList())
    val devices: StateFlow<List<USBDeviceInfo>> = _devices.asStateFlow()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> refreshDevices()
                ACTION_USB_PERMISSION -> refreshDevices()
            }
        }
    }

    fun startMonitoring() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        refreshDevices()
    }

    fun refreshDevices() {
        val deviceList = usbManager.deviceList.values.map { d ->
            USBDeviceInfo(
                name = d.deviceName,
                vendorId = d.vendorId,
                productId = d.productId,
                deviceId = d.deviceId,
                deviceClass = d.deviceClass,
                interfaceCount = d.interfaceCount,
                hasPermission = usbManager.hasPermission(d)
            )
        }
        _devices.value = deviceList
    }

    fun requestPermission(device: UsbDevice) {
        val intent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
        usbManager.requestPermission(device, intent)
    }

    fun getUsbDevice(deviceId: Int): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { it.deviceId == deviceId }

    fun stopMonitoring() {
        runCatching { context.unregisterReceiver(usbReceiver) }
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.orizon.openkiwi.USB_PERMISSION"
    }
}
