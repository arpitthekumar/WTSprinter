package com.example.wts.ui.common

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.wts.bluetooth.AppBluetoothManager
import com.example.wts.ui.settings.PrinterControlPanelActivity

@Composable
fun PrinterStatusIcon() {
    val context = LocalContext.current
    val deviceName by AppBluetoothManager.deviceName.collectAsState()
    val isConnected by AppBluetoothManager.isConnected.collectAsState()

    val modifier = if (isConnected) {
        Modifier.clickable { context.startActivity(Intent(context, PrinterControlPanelActivity::class.java)) }
    } else {
        Modifier
    }

    Row(modifier = modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isConnected) {
            Box(modifier = Modifier.size(8.dp).background(Color.Green, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = deviceName ?: "Printer")
        } else {
             Box(modifier = Modifier.size(8.dp).background(Color.Red, CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Not Connected")
        }
    }
}