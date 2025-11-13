package com.example.wts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    Column {
        Button(onClick = { context.startActivity(Intent(context, ManualPrintActivity::class.java)) }) {
            Text("Manual Print")
        }
        Button(onClick = { context.startActivity(Intent(context, AutoPrintActivity::class.java)) }) {
            Text("Auto Print")
        }
        Button(onClick = { context.startActivity(Intent(context, PrinterControlPanelActivity::class.java)) }) {
            Text("Printer Control Panel")
        }
    }
}
