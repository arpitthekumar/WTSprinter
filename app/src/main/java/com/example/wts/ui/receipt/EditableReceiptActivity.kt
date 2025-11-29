package com.example.wts.ui.receipt

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wts.bluetooth.AppBluetoothManager
import com.example.wts.model.ReceiptData
import com.example.wts.utils.EscPosUtils
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
class EditableReceiptActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val json = intent.getStringExtra("receipt_json")
        if (json == null) {
            finish()
            return
        }
        val receipt = Json.decodeFromString<ReceiptData>(json)
        setContent {
            EditableReceiptScreen(receipt)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableReceiptScreen(initialReceipt: ReceiptData) {
    var receipt by remember { mutableStateOf(initialReceipt) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Editable Receipt") }) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .background(Color.LightGray)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { ReceiptPreview(receipt = receipt) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                Column(modifier = Modifier.background(Color.White).padding(16.dp).fillMaxWidth()) {
                    Text("Edit Receipt Data", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(value = receipt.customerName, onValueChange = { receipt = receipt.copy(customerName = it) }, label = { Text("Customer Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = receipt.customerPhone, onValueChange = { receipt = receipt.copy(customerPhone = it) }, label = { Text("Customer Phone") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = receipt.subtotal, onValueChange = { receipt = receipt.copy(subtotal = it) }, label = { Text("Subtotal") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = receipt.discount, onValueChange = { receipt = receipt.copy(discount = it) }, label = { Text("Discount") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = receipt.total, onValueChange = { receipt = receipt.copy(total = it) }, label = { Text("Total") }, modifier = Modifier.fillMaxWidth())
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                Button(
                    onClick = {
                        val commands = EscPosUtils.formatReceipt(receipt)
                        AppBluetoothManager.printerHelper.sendBytes(commands)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Print Receipt", fontSize = 16.sp)
                }
            }
        }
    }
}


@Composable
fun ReceiptPreview(receipt: ReceiptData) {
    Column(
        modifier = Modifier
            .width(300.dp) // Fixed width to simulate 58mm receipt paper
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("BhootiyaFabric", fontWeight = FontWeight.Bold, fontSize = 26.sp, fontFamily = FontFamily.Monospace)
        Text("Collection", fontWeight = FontWeight.Bold, fontSize = 26.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))
        Text("Moti Ganj, BakebarRoad, Bharthana", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Text("Ph: +91 8273689065", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Row {
                Text("Invoice: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(receipt.invoiceNumber, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Row {
                Text("Customer: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(receipt.customerName, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Row {
                Text("Phone: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(receipt.customerPhone, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Row {
                Text("Date: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("${receipt.date} ", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("Time: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text(receipt.time, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Text("Item", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("Qty", modifier = Modifier.width(40.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("Rate", modifier = Modifier.width(50.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("Amt", modifier = Modifier.width(50.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        receipt.items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, modifier = Modifier.weight(1f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                Text(item.qty.toString(), modifier = Modifier.width(40.dp), textAlign = TextAlign.End, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("₹${item.price}", modifier = Modifier.width(50.dp), textAlign = TextAlign.End, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("₹${item.total}", modifier = Modifier.width(50.dp), textAlign = TextAlign.End, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Subtotal", fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            Text(receipt.subtotal, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
            Text(receipt.total, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Payment", fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            Text(receipt.paymentMethod, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(12.dp))

        if (receipt.barcode.isNotBlank()) {
            val pureBase64 = receipt.barcode.substringAfter(",")
            val imageBytes = Base64.decode(pureBase64, Base64.DEFAULT)
            val imageBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            Image(
                bitmap = imageBitmap.asImageBitmap(),
                contentDescription = "Barcode",
                modifier = Modifier.height(50.dp).fillMaxWidth()
            )
            Text(receipt.invoiceNumber, textAlign = TextAlign.Center, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(12.dp))

        Text("Thankyou, VisitAgain!", fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
    }
}
