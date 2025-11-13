package com.example.wts

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class BluetoothDevicePickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices

        setContent {
            DevicePickerScreen(devices = pairedDevices?.toList() ?: emptyList()) {
                val resultIntent = Intent()
                resultIntent.putExtra("device", it)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        }
    }
}

@Composable
fun DevicePickerScreen(devices: List<BluetoothDevice>, onDeviceSelected: (BluetoothDevice) -> Unit) {
    Column {
        Text("Paired Devices", modifier = Modifier.padding(16.dp))
        LazyColumn {
            items(devices) { device ->
                Text(
                    text = device.name,
                    modifier = Modifier.padding(16.dp).clickable { onDeviceSelected(device) }
                )
            }
        }
    }
}
