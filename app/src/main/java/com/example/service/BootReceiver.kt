package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.repository.PriceMonitorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.MY_PACKAGE_REPLACED") {
            Log.d("BootReceiver", "Reboot or update broadcast received: ${intent.action}")

            val monitor = PriceMonitorManager.getInstance(context)
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                val autoStart = monitor.getSetting("auto_start_on_boot") ?: "true"
                if (autoStart == "true") {
                    Log.d("BootReceiver", "Starting FinTrace price service on boot trigger...")
                    PriceTrackerService.start(context)
                }
            }
        }
    }
}
