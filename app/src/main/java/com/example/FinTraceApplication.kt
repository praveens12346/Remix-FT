package com.example

import android.app.Application
import android.util.Log
import com.example.data.repository.PriceMonitorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class FinTraceApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Setup a global Uncaught Exception Handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val crashMsg = "CRASH on thread [${thread.name}]: ${throwable.localizedMessage ?: "Unknown Reason"}\n" +
                    throwable.stackTrace.take(8).joinToString("\n") { it.toString() }
            Log.e("FinTraceApplication", "Unhandled Exception Caught globally: $crashMsg", throwable)

            // Log event to the offline Room Database synchronously/blocking to guarantee persistence before shutdown
            try {
                // Set shared preference flag for next startup self-healing
                val prefs = applicationContext.getSharedPreferences("fintrace_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("last_session_crashed", true).commit()

                val monitor = PriceMonitorManager.getInstance(applicationContext)
                runBlocking(Dispatchers.IO) {
                    monitor.db.appLogDao().insertLog(
                        com.example.data.model.AppLog(
                            type = "CRASH",
                            symbol = null,
                            message = "CRASH ENCOUNTERED: $crashMsg"
                        )
                    )
                }
                Log.d("FinTraceApplication", "Crash event logged to Room securely.")
            } catch (e: Exception) {
                Log.e("FinTraceApplication", "Could not log crash to database: ${e.message}")
            }

            // Let the default android handler crash or restart the app neatly
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Log.d("FinTraceApplication", "Global Crash Protection and Exception Handler initialized.")
    }
}
