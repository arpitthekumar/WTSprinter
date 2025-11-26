package com.example.wts.bluetooth

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wts.permissions.PermissionUtils

@SuppressLint("MissingPermission") // Permissions are checked by the calling activity
class BluetoothDevicePickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PermissionUtils.hasBluetoothPermissions(this)) {
            Toast.makeText(this, "Bluetooth permissions not granted.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val pairedDevices = BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList() ?: emptyList()

        setContent {
            MaterialTheme {
                DevicePickerScreen(devices = pairedDevices) { device ->
                    val resultIntent = Intent().apply {
                        putExtra("device", device)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun DevicePickerScreen(devices: List<BluetoothDevice>, onDeviceSelected: (BluetoothDevice) -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Select a Paired Printer") }) }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            if (devices.isEmpty()) {
                item { Text("No paired devices found.", modifier = Modifier.padding(16.dp)) }
            } else {
                items(devices) { device ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDeviceSelected(device) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(device.name ?: "Unknown Device", style = MaterialTheme.typography.titleMedium)
                        Text(device.address, style = MaterialTheme.typography.bodyMedium)
                    }
                    Divider()
                }
            }
        }
    }
}