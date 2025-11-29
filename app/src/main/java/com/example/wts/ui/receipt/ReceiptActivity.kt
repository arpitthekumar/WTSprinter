package com.example.wts.ui.receipt

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
import com.example.wts.model.ItemData
import com.example.wts.model.ReceiptData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ReceiptActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ReceiptSelectionScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptSelectionScreen() {
    val context = LocalContext.current
    Scaffold(
        topBar = { TopAppBar(title = { Text("Receipt Options") }) }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(it).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Choose Receipt Type")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { 
                context.startActivity(Intent(context, ReceiptPrintActivity::class.java))
            }) {
                Text("Print from Image")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { 
                val dummyReceipt = ReceiptData(
                    invoiceNumber = "INV-12345",
                    customerName = "Walk-in Customer",
                    customerPhone = "0000000000",
                    date = "01/01/2025",
                    time = "12:00",
                    paymentMethod = "Cash",
                    items = listOf(
                        ItemData(name="Sample Item 1", qty=1, price="100", total="100"),
                        ItemData(name="Sample Item 2", qty=2, price="50", total="100")
                    ),
                    subtotal = "200",
                    discount = "0",
                    total = "200",
                    barcode = ""
                )
                val intent = Intent(context, EditableReceiptActivity::class.java).apply {
                    putExtra("receipt_json", Json.encodeToString(dummyReceipt))
                }
                context.startActivity(intent)
            }) {
                Text("Custom Text Receipt")
            }
        }
    }
}
