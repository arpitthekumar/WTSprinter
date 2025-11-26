package com.example.wts.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothPrinterHelper {

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    fun connect(context: Context, device: BluetoothDevice, onResult: (Boolean, String) -> Unit) {
        coroutineScope.launch {
            try {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
                _isConnected.value = true
                saveLastPrinter(context, device.address)
                withContext(Dispatchers.Main) {
                    onResult(true, "Connected to ${device.name}")
                }
            } catch (e: Exception) {
                disconnect()
                withContext(Dispatchers.Main) {
                    onResult(false, "Connection failed: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) { /* Ignore */ } finally {
            outputStream = null
            bluetoothSocket = null
            _isConnected.value = false
        }
    }

    fun sendText(command: String, onResult: (Boolean) -> Unit = {}) {
        if (!isConnected.value) { onResult(false); return }
        coroutineScope.launch {
             try {
                outputStream?.write(command.toByteArray(Charsets.US_ASCII))
                outputStream?.flush()
                onResult(true)
            } catch (e: IOException) {
                onResult(false)
            }
        }
    }
    
    fun sendBytes(command: ByteArray, onResult: (Boolean) -> Unit = {}) {
        if (!isConnected.value) { onResult(false); return }
        coroutineScope.launch {
            try {
                outputStream?.write(command)
                outputStream?.flush()
                onResult(true)
            } catch (e: IOException) {
                onResult(false)
            }
        }
    }

    fun printImage(bitmapCmd: ByteArray, printCmd: String, onResult: (Boolean) -> Unit) {
        coroutineScope.launch {
            sendBinaryChunked(bitmapCmd) { success ->
                if (!success) { onResult(false); return@sendBinaryChunked }
                sendText(printCmd, onResult)
            }
        }
    }

    private fun sendBinaryChunked(command: ByteArray, onResult: (Boolean) -> Unit) {
        if (!isConnected.value) { onResult(false); return }
        try {
            command.inputStream().buffered().iterator().forEach { outputStream?.write(it.toInt()) }
            outputStream?.flush()
            onResult(true)
        } catch (e: IOException) {
            onResult(false)
        }
    }

    private fun saveLastPrinter(context: Context, address: String) {
        val sharedPref = context.getSharedPreferences("PrinterPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("last_printer_address", address)
            apply()
        }
    }

    companion object {
        fun getLastPrinterAddress(context: Context): String? {
            val sharedPref = context.getSharedPreferences("PrinterPrefs", Context.MODE_PRIVATE)
            return sharedPref.getString("last_printer_address", null)
        }
    }
}
