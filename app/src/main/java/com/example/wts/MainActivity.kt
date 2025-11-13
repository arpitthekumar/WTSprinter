package com.example.wts

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    Scaffold(
        topBar = { TopAppBar(title = { Text("TSPL Printer App") }) }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(it).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(modifier = Modifier.fillMaxWidth(), onClick = { context.startActivity(Intent(context, ManualPrintActivity::class.java)) }) {
                Text("Manual Print")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = { context.startActivity(Intent(context, AutoPrintActivity::class.java)) }) {
                Text("Auto Print")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = { context.startActivity(Intent(context, PrinterControlPanelActivity::class.java)) }) {
                Text("Printer Control Panel")
            }
        }
    }
}
