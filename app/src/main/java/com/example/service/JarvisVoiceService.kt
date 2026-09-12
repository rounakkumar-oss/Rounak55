package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.voice.SpeechRecognitionCallback
import com.example.voice.SpeechRecognizerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JarvisVoiceService : Service() {

    private var speechRecognizerManager: SpeechRecognizerManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        private const val TAG = "JarvisVoiceService"
        const val CHANNEL_ID = "jarvis_voice_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START_JARVIS_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_JARVIS_SERVICE"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _latestCommand = MutableStateFlow<String?>(null)
        val latestCommand: StateFlow<String?> = _latestCommand.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initSpeechRecognizer()
        _isServiceRunning.value = true
        Log.d(TAG, "JarvisVoiceService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundWithNotification()
                startListening()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JarvisVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("JARVIS Voice Assistant")
            .setContentText("Background voice listener active. Say a command.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Jarvis", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizerManager = SpeechRecognizerManager(
            this,
            object : SpeechRecognitionCallback {
                override fun onReadyForSpeech() {
                    Log.d(TAG, "Service: microphone ready for speech")
                }

                override fun onListeningStarted() {
                    Log.d(TAG, "Service: speech listening started")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onPartialResult(text: String) {}

                override fun onFinalResult(text: String) {
                    Log.d(TAG, "Service: final speech result: $text")
                    serviceScope.launch {
                        _latestCommand.value = text
                    }
                    // Auto re-listen if service is still running
                    if (_isServiceRunning.value) {
                        serviceScope.launch {
                            kotlinx.coroutines.delay(1000)
                            startListening()
                        }
                    }
                }

                override fun onError(errorCode: Int, message: String) {
                    Log.w(TAG, "Service: Speech error ($errorCode): $message")
                    // If service running, quietly restart after timeout
                    if (_isServiceRunning.value) {
                        serviceScope.launch {
                            kotlinx.coroutines.delay(2000)
                            startListening()
                        }
                    }
                }

                override fun onListeningStopped() {
                    Log.d(TAG, "Service: speech listening stopped")
                }
            }
        )
    }

    private fun startListening() {
        if (!_isServiceRunning.value) return
        try {
            speechRecognizerManager?.startListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition in service", e)
        }
    }

    private fun stopForegroundService() {
        _isServiceRunning.value = false
        speechRecognizerManager?.stopListening()
        speechRecognizerManager?.destroy()
        speechRecognizerManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "JarvisVoiceService stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Background Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service notification for Jarvis Voice Assistant"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        speechRecognizerManager?.destroy()
        speechRecognizerManager = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
