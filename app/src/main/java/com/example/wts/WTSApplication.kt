package com.example.wts

import android.app.Application
import com.example.wts.bluetooth.AppBluetoothManager

class WTSApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Auto-connect to the last printer as soon as the app starts.
        AppBluetoothManager.autoConnect(this)
    }
}
