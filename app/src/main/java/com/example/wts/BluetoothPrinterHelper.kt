package com.example.wts

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothPrinterHelper {

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    val isConnected: Boolean
        get() = bluetoothSocket?.isConnected == true

    fun connect(device: BluetoothDevice, onResult: (Boolean, String) -> Unit) {
        coroutineScope.launch {
            try {
                // Standard UUID for SPP (Serial Port Profile)
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect() // This is a blocking call
                outputStream = bluetoothSocket?.outputStream
                onResult(true, "Connected to ${device.name}")
            } catch (e: IOException) {
                e.printStackTrace()
                disconnect()
                onResult(false, "Connection failed: ${e.message}")
            } catch (e: SecurityException) {
                e.printStackTrace()
                disconnect()
                onResult(false, "Permission denied: ${e.message}")
            }
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            outputStream = null
            bluetoothSocket = null
        }
    }

    fun sendCommand(command: String, onResult: (Boolean) -> Unit = {}) {
        if (!isConnected) {
            onResult(false)
            return
        }
        coroutineScope.launch {
            try {
                outputStream?.write(command.toByteArray(Charsets.US_ASCII))
                outputStream?.flush()
                onResult(true)
            } catch (e: IOException) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }

    fun sendCommand(command: ByteArray, onResult: (Boolean) -> Unit = {}) {
        if (!isConnected) {
            onResult(false)
            return
        }
        coroutineScope.launch {
            try {
                outputStream?.write(command)
                outputStream?.flush()
                onResult(true)
            } catch (e: IOException) {
                e.printStackTrace()
                onResult(false)
            }
        }
    }
}
