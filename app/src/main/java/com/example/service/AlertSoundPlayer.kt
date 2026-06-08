package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.data.repository.PriceMonitorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

object AlertSoundPlayer {
    private var currentRingtone: Ringtone? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    fun initTts(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.let {
                        val result = it.setLanguage(Locale.US)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e("AlertSoundPlayer", "TTS Language not supported or missing data")
                        } else {
                            isTtsReady = true
                        }
                    }
                } else {
                    Log.e("AlertSoundPlayer", "TTS Initialization failed")
                }
            }
        }
    }

    fun playAlertSound(context: Context, textToSpeak: String, priority: String) {
        handler.post {
            try {
                stopPlayback()

                val monitor = PriceMonitorManager.getInstance(context)
                val scope = CoroutineScope(Dispatchers.IO)
                
                scope.launch {
                    val suffix = priority.lowercase(Locale.US)
                    val pSoundUriStr = monitor.getSetting("alert_sound_uri_$suffix")
                    val pDurationSec = monitor.getSetting("alert_ring_duration_sec_$suffix")?.toIntOrNull()
                    val pSoundMode = monitor.getSetting("alert_sound_mode_$suffix")

                    val soundUriStr = if (!pSoundUriStr.isNullOrEmpty()) pSoundUriStr else (monitor.getSetting("alert_sound_uri") ?: "")
                    val durationSec = pDurationSec ?: monitor.getSetting("alert_ring_duration_sec")?.toIntOrNull() ?: 5
                    val soundMode = if (!pSoundMode.isNullOrEmpty()) pSoundMode else (monitor.getSetting("alert_sound_mode") ?: "Both Tone and Voice")

                    withContext(Dispatchers.Main) {
                        // Play TTS voice
                        if (soundMode == "Both Tone and Voice" || soundMode == "TTS voice only") {
                            initTts(context)
                            if (isTtsReady) {
                                tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "ALERT_TTS")
                            } else {
                                // Initialize and play upon initialization later
                                tts = TextToSpeech(context.applicationContext) { status ->
                                    if (status == TextToSpeech.SUCCESS) {
                                        tts?.setLanguage(Locale.US)
                                        isTtsReady = true
                                        tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "ALERT_TTS")
                                    }
                                }
                            }
                        }

                        // Play selected Tone / Ringtone
                        if (soundMode == "Both Tone and Voice" || soundMode == "Tone alert only") {
                            val uri = if (soundUriStr.isNotEmpty()) {
                                Uri.parse(soundUriStr)
                            } else {
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            }

                            if (uri != null) {
                                val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
                                if (ringtone != null) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                        val aa = AudioAttributes.Builder()
                                            .setUsage(AudioAttributes.USAGE_ALARM)
                                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                            .build()
                                        ringtone.audioAttributes = aa
                                    }
                                    ringtone.play()
                                    currentRingtone = ringtone

                                    // Schedule to stop ringing after the chosen duration
                                    val runnable = Runnable {
                                        stopPlayback()
                                    }
                                    stopRunnable = runnable
                                    handler.postDelayed(runnable, durationSec * 1000L)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AlertSoundPlayer", "Failed to play alert sound: ${e.message}")
            }
        }
    }

    fun stopPlayback() {
        try {
            stopRunnable?.let {
                handler.removeCallbacks(it)
                stopRunnable = null
            }
            currentRingtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                currentRingtone = null
            }
            tts?.let {
                if (it.isSpeaking) {
                    it.stop()
                }
            }
        } catch (e: Exception) {
            Log.e("AlertSoundPlayer", "Error stopping playback: ${e.message}")
        }
    }

    fun shutdown() {
        stopPlayback()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }
}
