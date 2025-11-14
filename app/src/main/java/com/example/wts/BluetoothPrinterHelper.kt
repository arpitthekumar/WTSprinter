package com.example.wts

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    val isConnected: Boolean
        get() = bluetoothSocket?.isConnected == true

    fun connect(context: Context, device: BluetoothDevice, onResult: (Boolean, String) -> Unit) {
        coroutineScope.launch {
            try {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream
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
        }
    }

    fun sendText(command: String, onResult: (Boolean) -> Unit = {}) {
        if (!isConnected) { onResult(false); return }
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

    fun printImage(bitmapCmd: ByteArray, printCmd: String, onResult: (Boolean) -> Unit) {
        coroutineScope.launch {
            sendBinaryChunked(bitmapCmd) { success ->
                if (!success) { onResult(false); return@sendBinaryChunked }
                sendText(printCmd, onResult)
            }
        }
    }

    private fun sendBinaryChunked(command: ByteArray, onResult: (Boolean) -> Unit) {
        if (!isConnected) { onResult(false); return }
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
}
