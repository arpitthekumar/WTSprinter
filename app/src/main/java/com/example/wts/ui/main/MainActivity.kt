package com.example.wts.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.wts.ui.choice.PrintChoiceActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The main activity's only job is to launch the main screen of the app.
        // All deep link and share logic is handled by other, dedicated components.
        val intent = Intent(this, PrintChoiceActivity::class.java)
        startActivity(intent)
        finish()
    }
}
