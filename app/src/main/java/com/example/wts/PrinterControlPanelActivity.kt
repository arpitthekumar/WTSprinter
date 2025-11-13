package com.example.wts

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class PrinterControlPanelActivity : ComponentActivity() {
    private val printerHelper = BluetoothPrinterHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrinterControlPanelScreen(printerHelper)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        printerHelper.disconnect()
    }
}

@Composable
fun PrinterControlPanelScreen(printerHelper: BluetoothPrinterHelper) {
    val context = LocalContext.current
    val debugLog = remember { mutableStateListOf<String>() }
    var density by remember { mutableStateOf(6f) }
    var speed by remember { mutableStateOf(3f) }
    var direction by remember { mutableStateOf(1f) }
    var feed by remember { mutableStateOf(10f) }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var connectionStatus by remember { mutableStateOf("Not Connected") }
    val coroutineScope = rememberCoroutineScope()

    val devicePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val device = result.data?.getParcelableExtra<BluetoothDevice>("device")
            if (device != null) {
                selectedDevice = device
                coroutineScope.launch {
                    connectionStatus = if (printerHelper.connect(device)) "Connected" else "Connection Failed"
                }
            }
        }
    }

    fun sendCommand(command: String) {
        if (connectionStatus == "Connected") {
            coroutineScope.launch {
                printerHelper.sendCommand(command)
                debugLog.add(command)
                Toast.makeText(context, "Command Sent", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Printer Not Connected", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Button(onClick = { devicePickerLauncher.launch(Intent(context, BluetoothDevicePickerActivity::class.java)) }) {
            Text("Select Printer")
        }
        Text("Connection Status: $connectionStatus")

        // ... other buttons ...

        Button(onClick = { sendCommand(TsplUtils.selfTest()) }) {
            Text("SELFTEST")
        }
        Button(onClick = { sendCommand(TsplUtils.status()) }) {
            Text("?STATUS")
        }
        Button(onClick = { sendCommand(TsplUtils.version()) }) {
            Text("?VERSION")
        }
        LazyColumn {
            items(debugLog) { log ->
                Text(log)
            }
        }
    }
}
