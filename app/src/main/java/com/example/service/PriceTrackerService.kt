package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.example.data.repository.PriceMonitorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PriceTrackerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var monitorManager: PriceMonitorManager

    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == Intent.ACTION_SCREEN_OFF) {
                Log.d("PriceTrackerService", "Screen OFF received. Stopping sound/vibration and setting battery saver rates.")
                PriceMonitorManager.getInstance(context).setScreenState(false)
                
                // Stop audio playback and vibration
                AlertSoundPlayer.stopPlayback()
                NotificationHelper.cancelVibration(context)
                
                // Clear active alerts notifications to avoid drawer clutter
                cancelAlertNotifications()
            } else if (action == Intent.ACTION_SCREEN_ON) {
                Log.d("PriceTrackerService", "Screen ON received. Restoring standard price trackers.")
                PriceMonitorManager.getInstance(context).setScreenState(true)
            } else if (action == "android.media.VOLUME_CHANGED_ACTION") {
                Log.d("PriceTrackerService", "Alert silencing volume gesture received. Muting active sounds.")
                AlertSoundPlayer.stopPlayback()
                NotificationHelper.cancelVibration(context)
            }
        }

        private fun cancelAlertNotifications() {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    for (activeNotif in nm.activeNotifications) {
                        val id = activeNotif.id
                        if (id != NotificationHelper.FGS_NOTIFICATION_ID && id != NotificationHelper.TICKER_NOTIFICATION_ID) {
                            nm.cancel(id)
                        }
                    }
                } else {
                    for (id in 10000..10100) {
                        nm.cancel(id)
                    }
                    nm.cancel(NotificationHelper.DEMO_NOTIFICATION_ID)
                }
            } catch (e: Exception) {
                Log.e("PriceTrackerService", "Error canceling alerts notifications: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        monitorManager = PriceMonitorManager.getInstance(this)

        // Initialize screen state
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            // Initialize current power screen state in manager
            val isScreenInteractive = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) pm.isInteractive else pm.isScreenOn
            monitorManager.setScreenState(isScreenInteractive)
        } catch (e: Exception) {
            Log.e("PriceTrackerService", "Failed to initialize screen state: ${e.message}")
        }

        // Register dynamic receiver for physical key silencing gestures and screen state alterations
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction("android.media.VOLUME_CHANGED_ACTION")
            }
            registerReceiver(screenOffReceiver, filter)
        } catch (e: Exception) {
            Log.e("PriceTrackerService", "Failed to register screenReceiver: ${e.message}")
        }

        Log.d("PriceTrackerService", "Foreground service created successfully")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground with FGS Notification (ID 1001)
        val activeSymbols = monitorManager.activeSymbols.value
        val notification = NotificationHelper.buildFgsNotification(this, activeSymbols)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.FGS_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.FGS_NOTIFICATION_ID, notification)
        }

        scope.launch {
            monitorManager.activeSymbols.collect { active ->
                val fgsNotif = NotificationHelper.buildFgsNotification(this@PriceTrackerService, active)
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(NotificationHelper.FGS_NOTIFICATION_ID, fgsNotif)
            }
        }

        monitorManager.startMonitoringLoop()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            Log.e("PriceTrackerService", "Failed to unregister screenOffReceiver: ${e.message}")
        }
        monitorManager.stopMonitoring()
        scope.cancel()
        Log.d("PriceTrackerService", "Foreground service destroyed")
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, PriceTrackerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PriceTrackerService::class.java)
            context.stopService(intent)
        }
    }
}
