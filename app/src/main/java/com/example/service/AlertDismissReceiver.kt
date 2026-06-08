package com.example.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlertDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.ACTION_DISMISS_ALERTS") {
            Log.d("AlertDismissReceiver", "Received ACTION_DISMISS_ALERTS. Stopping sound and vibration.")
            
            // 1. Stop alert sounds
            AlertSoundPlayer.stopPlayback()
            
            // 2. Stop vibration
            NotificationHelper.cancelVibration(context)
            
            // 3. Dismiss targeted notification
            val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)
            if (notificationId != -1) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notificationId)
                Log.d("AlertDismissReceiver", "Canceled notification ID: $notificationId")
            }
        }
    }
}
