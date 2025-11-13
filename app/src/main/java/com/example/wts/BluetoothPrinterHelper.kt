package com.example.wts

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class BluetoothPrinterHelper {

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    fun connect(device: BluetoothDevice): Boolean {
        return try {
            val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SerialPortService ID
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun sendCommand(command: String) {
        outputStream?.let { stream ->
            try {
                stream.write(command.toByteArray())
                stream.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
    fun sendCommand(command: ByteArray) {
        outputStream?.let { stream ->
            try {
                stream.write(command)
                stream.flush()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}
