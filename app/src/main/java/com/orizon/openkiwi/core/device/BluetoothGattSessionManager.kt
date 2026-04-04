package com.orizon.openkiwi.core.device

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class GattSession(
    val address: String,
    val gatt: BluetoothGatt,
    @Volatile var state: Int = BluetoothProfile.STATE_DISCONNECTED,
    @Volatile var services: List<BluetoothGattService> = emptyList(),
    @Volatile var lastReadValue: ByteArray? = null,
    @Volatile var lastNotifyValue: ByteArray? = null
)

@SuppressLint("MissingPermission")
class BluetoothGattSessionManager(private val context: Context) {

    companion object {
        private const val TAG = "BleSessionMgr"
        private const val OPERATION_TIMEOUT_MS = 10_000L
    }

    private val sessions = ConcurrentHashMap<String, GattSession>()
    private val operationMutex = Mutex()

    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter

    fun getSession(address: String): GattSession? = sessions[address]
    fun getAllSessions(): Map<String, GattSession> = sessions.toMap()

    suspend fun connect(address: String): Result<GattSession> = operationMutex.withLock {
        val existing = sessions[address]
        if (existing != null && existing.state == BluetoothProfile.STATE_CONNECTED) {
            return Result.success(existing)
        }

        val adapter = adapter ?: return Result.failure(IllegalStateException("Bluetooth not available"))
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return Result.failure(e)
        }

        return suspendCancellableCoroutine { cont ->
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    val session = sessions[address]
                    if (session != null) session.state = newState

                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        gatt.discoverServices()
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        if (cont.isActive) {
                            if (status != BluetoothGatt.GATT_SUCCESS) {
                                cont.resume(Result.failure(
                                    Exception("Connection failed with status $status")), null)
                            }
                        }
                        sessions.remove(address)
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    val session = sessions[address] ?: return
                    session.services = gatt.services ?: emptyList()
                    if (cont.isActive) {
                        cont.resume(Result.success(session), null)
                    }
                }

                override fun onCharacteristicRead(
                    gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
                ) {
                    val session = sessions[address] ?: return
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        @Suppress("DEPRECATION")
                        session.lastReadValue = characteristic.value?.clone()
                    }
                }

                @Suppress("DEPRECATION")
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
                ) {
                    val session = sessions[address] ?: return
                    session.lastNotifyValue = characteristic.value?.clone()
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
                ) {
                    Log.d(TAG, "Write to ${characteristic.uuid} status=$status")
                }
            }

            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, callback)
            }

            if (gatt == null) {
                cont.resume(Result.failure(Exception("connectGatt returned null")), null)
                return@suspendCancellableCoroutine
            }

            val session = GattSession(address, gatt, BluetoothProfile.STATE_CONNECTING)
            sessions[address] = session

            cont.invokeOnCancellation {
                gatt.disconnect()
                gatt.close()
                sessions.remove(address)
            }
        }
    }

    fun disconnect(address: String): Boolean {
        val session = sessions.remove(address) ?: return false
        session.gatt.disconnect()
        session.gatt.close()
        return true
    }

    fun disconnectAll() {
        sessions.keys.toList().forEach { disconnect(it) }
    }

    @Suppress("DEPRECATION")
    suspend fun readCharacteristic(
        address: String, serviceUuid: UUID, charUuid: UUID
    ): Result<ByteArray> = operationMutex.withLock {
        val session = sessions[address]
            ?: return Result.failure(Exception("Not connected to $address"))
        val service = session.gatt.getService(serviceUuid)
            ?: return Result.failure(Exception("Service $serviceUuid not found"))
        val char = service.getCharacteristic(charUuid)
            ?: return Result.failure(Exception("Characteristic $charUuid not found"))

        session.lastReadValue = null
        if (!session.gatt.readCharacteristic(char)) {
            return Result.failure(Exception("readCharacteristic initiation failed"))
        }

        return withTimeoutOrNull(OPERATION_TIMEOUT_MS) {
            while (session.lastReadValue == null) delay(50)
            Result.success(session.lastReadValue!!)
        } ?: Result.failure(Exception("Read timed out"))
    }

    @Suppress("DEPRECATION")
    fun writeCharacteristic(
        address: String, serviceUuid: UUID, charUuid: UUID,
        value: ByteArray, writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ): Result<Unit> {
        val session = sessions[address]
            ?: return Result.failure(Exception("Not connected to $address"))
        val service = session.gatt.getService(serviceUuid)
            ?: return Result.failure(Exception("Service $serviceUuid not found"))
        val char = service.getCharacteristic(charUuid)
            ?: return Result.failure(Exception("Characteristic $charUuid not found"))

        char.writeType = writeType
        char.value = value
        return if (session.gatt.writeCharacteristic(char)) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("writeCharacteristic initiation failed"))
        }
    }

    @Suppress("DEPRECATION")
    fun setNotification(
        address: String, serviceUuid: UUID, charUuid: UUID, enable: Boolean
    ): Result<Unit> {
        val session = sessions[address]
            ?: return Result.failure(Exception("Not connected to $address"))
        val service = session.gatt.getService(serviceUuid)
            ?: return Result.failure(Exception("Service $serviceUuid not found"))
        val char = service.getCharacteristic(charUuid)
            ?: return Result.failure(Exception("Characteristic $charUuid not found"))

        if (!session.gatt.setCharacteristicNotification(char, enable)) {
            return Result.failure(Exception("setCharacteristicNotification failed"))
        }

        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val descriptor = char.getDescriptor(cccdUuid)
        if (descriptor != null) {
            descriptor.value = if (enable)
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            session.gatt.writeDescriptor(descriptor)
        }

        if (enable) session.lastNotifyValue = null
        return Result.success(Unit)
    }
}
