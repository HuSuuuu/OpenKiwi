package com.orizon.openkiwi.core.device

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class DiscoveredDevice(
    val name: String,
    val host: String,
    val port: Int,
    val type: String = "unknown",
    val discoveredAt: Long = System.currentTimeMillis()
)

class DeviceDiscovery(private val context: Context) {

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()
    private var nsdManager: NsdManager? = null
    private var companionNsd: NsdManager? = null
    private var companionRegistrationListener: NsdManager.RegistrationListener? = null

    fun startDiscovery(serviceType: String = "_ssh._tcp.") {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, code: Int) {}
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        val device = DiscoveredDevice(
                            name = si.serviceName,
                            host = si.host.hostAddress ?: "",
                            port = si.port,
                            type = si.serviceType
                        )
                        val current = _devices.value.toMutableList()
                        if (current.none { it.host == device.host && it.port == device.port }) {
                            current.add(device)
                            _devices.value = current
                        }
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val current = _devices.value.toMutableList()
                current.removeAll { it.name == serviceInfo.serviceName }
                _devices.value = current
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, code: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, code: Int) {}
        })
    }

    fun addManualDevice(name: String, host: String, port: Int = 22) {
        val device = DiscoveredDevice(name = name, host = host, port = port, type = "manual")
        val current = _devices.value.toMutableList()
        current.add(device)
        _devices.value = current
    }

    fun stopDiscovery() {
        runCatching { nsdManager?.stopServiceDiscovery(null) }
    }

    /**
     * Advertises this phone's Companion WebSocket port so the PC app can discover it via mDNS (_openkiwi._tcp).
     */
    fun registerOpenKiwiCompanionService(port: Int) {
        unregisterOpenKiwiCompanionService()
        val mgr = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        companionNsd = mgr
        val serviceName = (Build.MODEL?.ifBlank { null } ?: "OpenKiwi").take(28)
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = "_openkiwi._tcp."
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        companionRegistrationListener = listener
        runCatching {
            mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun unregisterOpenKiwiCompanionService() {
        val listener = companionRegistrationListener ?: return
        runCatching { companionNsd?.unregisterService(listener) }
        companionRegistrationListener = null
        companionNsd = null
    }

    fun pingHost(host: String, timeoutMs: Int = 3000): Boolean =
        runCatching { InetAddress.getByName(host).isReachable(timeoutMs) }.getOrDefault(false)
}
