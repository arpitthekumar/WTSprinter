package com.example.wts.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A singleton object to manage the global state of the Bluetooth printer connection.
 */
@SuppressLint("MissingPermission")
object AppBluetoothManager {

    val printerHelper = BluetoothPrinterHelper()

    private val _deviceName = MutableStateFlow<String?>("No Printer Connected")
    val deviceName = _deviceName.asStateFlow()

    val isConnected = printerHelper.isConnected

    fun connect(context: Context, device: BluetoothDevice) {
        if (isConnected.value) {
            printerHelper.disconnect()
        }
        _deviceName.value = "Connecting..."
        printerHelper.connect(context, device) { success, message ->
            _deviceName.value = if (success) device.name else "Connection Failed"
        }
    }

    fun autoConnect(context: Context) {
        if (isConnected.value) return
        val lastPrinterAddress = BluetoothPrinterHelper.getLastPrinterAddress(context)
        if (lastPrinterAddress != null) {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) return

            val device = adapter.getRemoteDevice(lastPrinterAddress)
            device?.let { connect(context, it) }
        }
    }
}