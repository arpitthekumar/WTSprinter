package com.example.wts.ui.choice

import android.content.Intent
import android.net.Uri
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
import com.example.wts.ui.common.PrinterStatusIcon
import com.example.wts.ui.label.LabelActivity
import com.example.wts.ui.receipt.ReceiptPrintActivity

class PrintChoiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageUri = intent.data
        setContent {
            PrintChoiceScreen(imageUri)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintChoiceScreen(imageUri: Uri?) {
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(
            title = { Text("Choose Print Format") },
            actions = { PrinterStatusIcon() }
        ) }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(it).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("What would you like to print?")
            Spacer(modifier = Modifier.height(32.dp))

            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                val intent = Intent(context, LabelActivity::class.java).apply {
                    data = imageUri
                }
                context.startActivity(intent)
            }) {
                Text("Print as Label (TSPL)")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                val intent = Intent(context, ReceiptPrintActivity::class.java).apply {
                    data = imageUri
                }
                context.startActivity(intent)
            }) {
                Text("Print as Receipt (ESC/POS)")
            }
        }
    }
}
