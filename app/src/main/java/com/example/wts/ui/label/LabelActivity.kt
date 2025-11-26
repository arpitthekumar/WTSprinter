package com.example.wts.ui.label

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

class LabelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val imageUri = intent.data
        setContent {
            LabelChoiceScreen(imageUri)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelChoiceScreen(imageUri: Uri?) {
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(
            title = { Text("Choose Label Print Mode") },
            actions = { PrinterStatusIcon() }
        ) }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(it).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                val intent = Intent(context, ManualLabelPrintActivity::class.java).apply {
                    data = imageUri
                }
                context.startActivity(intent)
            }) {
                Text("Manual Label Print")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                val intent = Intent(context, AutoLabelPrintActivity::class.java).apply {
                    data = imageUri
                }
                context.startActivity(intent)
            }) {
                Text("Auto Label Print")
            }
        }
    }
}