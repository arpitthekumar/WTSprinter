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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
    var connectionStatus by remember { mutableStateOf("Not Connected") }

    val devicePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getParcelableExtra<BluetoothDevice>("device")?.let { device ->
                connectionStatus = "Connecting..."
                printerHelper.connect(context, device) { success, message -> connectionStatus = message }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { 
        if (PermissionUtils.hasBluetoothPermissions(context)) {
             devicePickerLauncher.launch(Intent(context, BluetoothDevicePickerActivity::class.java))
        } else {
             Toast.makeText(context, "Bluetooth permissions are required.", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(modifier = Modifier.padding(16.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Button(onClick = { permissionLauncher.launch(PermissionUtils.getRequiredBluetoothPermissions()) }) { Text("Select Printer") } }
        item { Text("Status: $connectionStatus") }
        item { Spacer(Modifier.height(16.dp)) }
        
        item { Button(onClick = { printerHelper.sendText(TsplUtils.getInitCommand()) }) { Text("Init Printer (Defaults)") } }
        item { Button(onClick = { printerHelper.sendText(TsplUtils.getFormFeedCommand()) }) { Text("FORMFEED") } }
        item { Button(onClick = { printerHelper.sendText(TsplUtils.getGapDetectCommand()) }) { Text("GAPDETECT") } }
        item { Button(onClick = { printerHelper.sendText(TsplUtils.getFeedCommand(10)) }) { Text("FEED 10") } }
        item { Button(onClick = { printerHelper.sendText(TsplUtils.getClsCommand()) }) { Text("CLS") } }
        item { Button(onClick = { printerHelper.sendText(TsplUtils.getDensityCommand(8)) }) { Text("DENSITY 8") } }
        item { Button(onClick = { printerHelper.sendText(TsplUtils.getSpeedCommand(4)) }) { Text("SPEED 4") } }
        item { Button(onClick = { printerHelper.sendText(TsplUtils.getSelfTestCommand()) }) { Text("SELFTEST") } }
        item { Button(onClick = { printerHelper.sendText(TsplUtils.getStatusCommand()) }) { Text("?STATUS") } }
        item { Button(onClick = { printerHelper.sendText(TsplUtils.getVersionCommand()) }) { Text("?VERSION") } }
    }
}
