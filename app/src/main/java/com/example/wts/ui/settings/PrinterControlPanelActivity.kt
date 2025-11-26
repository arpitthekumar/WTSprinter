package com.example.wts.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.wts.bluetooth.AppBluetoothManager
import com.example.wts.ui.common.PrinterStatusIcon
import com.example.wts.utils.EscPosUtils
import com.example.wts.utils.TsplUtils

@SuppressLint("MissingPermission")
class PrinterControlPanelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrinterControlPanelScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterControlPanelScreen() {
    val logs = remember { mutableStateListOf<String>() }
    val isConnected by AppBluetoothManager.isConnected.collectAsState()
    val deviceName by AppBluetoothManager.deviceName.collectAsState()

    Scaffold(
        topBar = { TopAppBar(
            title = { Text("Printer Control Panel") },
            actions = { PrinterStatusIcon() }
        ) }
    ) {
        LazyColumn(modifier = Modifier.padding(it).padding(16.dp)) {
            item {
                Text("Status: ${if (isConnected) "Connected" else "Disconnected"}", style = MaterialTheme.typography.titleMedium)
                Text("Device: ${deviceName ?: "N/A"}")
            }

            item { Spacer(Modifier.height(24.dp)) }
            item { Text("TSPL (Label) Test Commands", style = MaterialTheme.typography.titleMedium) }
            item { Row {
                Button(onClick = { AppBluetoothManager.printerHelper.sendText(TsplUtils.getGapDetectCommand()) { logs.add("TSPL GAPDETECT sent.") } }) { Text("GAPDETECT") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { AppBluetoothManager.printerHelper.sendText(TsplUtils.getFormFeedCommand()) { logs.add("TSPL FORMFEED sent.") } }) { Text("FORMFEED") }
            } }

            item { Spacer(Modifier.height(24.dp)) }
            item { Text("ESC/POS (Receipt) Test Commands", style = MaterialTheme.typography.titleMedium) }
            item { Row {
                Button(onClick = { AppBluetoothManager.printerHelper.sendBytes(EscPosUtils.getPrintAndFeed(1)) { logs.add("ESC/POS FEED sent.") } }) { Text("Feed Line") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { AppBluetoothManager.printerHelper.sendBytes(EscPosUtils.getCutCommand()) { logs.add("ESC/POS CUT sent.") } }) { Text("Cut Paper") }
            } }

            item { Spacer(Modifier.height(16.dp)) }
            item { Text("Logs", style = MaterialTheme.typography.titleMedium) }
            items(logs.size) { index ->
                Text(logs[index], style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}