package com.orizon.openkiwi.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orizon.openkiwi.core.device.DeviceDiscovery
import com.orizon.openkiwi.core.device.DiscoveredDevice
import com.orizon.openkiwi.core.device.USBDeviceInfo
import com.orizon.openkiwi.core.device.USBHostManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DeviceUiState(
    val networkDevices: List<DiscoveredDevice> = emptyList(),
    val usbDevices: List<USBDeviceInfo> = emptyList(),
    val isScanning: Boolean = false
)

class DeviceViewModel(
    private val deviceDiscovery: DeviceDiscovery,
    private val usbHostManager: USBHostManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            deviceDiscovery.devices.collect { devices ->
                _uiState.value = _uiState.value.copy(networkDevices = devices)
            }
        }
        viewModelScope.launch {
            usbHostManager.devices.collect { devices ->
                _uiState.value = _uiState.value.copy(usbDevices = devices)
            }
        }
    }

    fun startScan() {
        _uiState.value = _uiState.value.copy(isScanning = true)
        deviceDiscovery.startDiscovery()
        usbHostManager.refreshDevices()
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            _uiState.value = _uiState.value.copy(isScanning = false)
        }
    }

    fun addManualDevice(name: String, host: String, port: Int) {
        deviceDiscovery.addManualDevice(name, host, port)
    }

    class Factory(private val dd: DeviceDiscovery, private val um: USBHostManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DeviceViewModel(dd, um) as T
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(viewModel: DeviceViewModel, onBack: () -> Unit, companionServer: com.orizon.openkiwi.network.CompanionServer? = null) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设备") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    if (uiState.isScanning) CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { viewModel.startScan() }) { Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (companionServer != null) {
                item { CompanionCard(server = companionServer) }
                item { Spacer(Modifier.height(4.dp)) }
            }

            item {
                Text("网络设备", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))
            }
            if (uiState.networkDevices.isEmpty()) {
                item {
                    Text("未发现设备", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
            items(uiState.networkDevices) { device ->
                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Computer, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(device.name, style = MaterialTheme.typography.titleMedium)
                            Text("${device.host}:${device.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                        }
                    }
                }
            }

            item {
                Text("USB 设备", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            }
            if (uiState.usbDevices.isEmpty()) {
                item {
                    Text("未检测到", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                }
            }
            items(uiState.usbDevices) { device ->
                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Usb, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(device.name, style = MaterialTheme.typography.titleMedium)
                            Text("VID=0x${device.vendorId.toString(16)} PID=0x${device.productId.toString(16)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (device.hasPermission) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        ) {
                            Text(
                                if (device.hasPermission) "已授权" else "未授权",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (device.hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var host by remember { mutableStateOf("") }
        var port by remember { mutableStateOf("22") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加设备") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                    OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("IP 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                    OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("端口") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.addManualDevice(name, host, port.toIntOrNull() ?: 22); showAddDialog = false },
                    enabled = name.isNotBlank() && host.isNotBlank()
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )
    }
}
