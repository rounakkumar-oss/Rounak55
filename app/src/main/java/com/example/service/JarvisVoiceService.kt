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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.voice.SpeechRecognitionCallback
import com.example.voice.SpeechRecognizerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class JarvisVoiceService : Service() {

    private var speechRecognizerManager: SpeechRecognizerManager? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var partialWakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "JarvisVoiceService"
        const val CHANNEL_ID = "jarvis_voice_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START_JARVIS_SERVICE"
        const val ACTION_STOP = "ACTION_STOP_JARVIS_SERVICE"
        const val ACTION_PAUSE_LISTENING = "ACTION_PAUSE_LISTENING"
        const val ACTION_RESUME_LISTENING = "ACTION_RESUME_LISTENING"

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

        fun pauseListening(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_PAUSE_LISTENING
            }
            context.startService(intent)
        }

        fun resumeListening(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java).apply {
                action = ACTION_RESUME_LISTENING
            }
            context.startService(intent)
        }
    }

    private var isListeningPaused = false

    override fun onCreate() {
        super.onCreate()
        acquirePartialWakeLock()
        createNotificationChannel()
        initSpeechRecognizer()
        _isServiceRunning.value = true
        Log.d(TAG, "JarvisVoiceService created with PARTIAL_WAKE_LOCK")
    }

    private fun acquirePartialWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            partialWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Jarvis::BackgroundWakeWordLock"
            ).apply {
                setReferenceCounted(false)
            }
            // 2-hour timeout as safety guardrail against perpetual battery drain if service killed abnormally
            partialWakeLock?.acquire(2 * 60 * 60 * 1000L)
            Log.d(TAG, "PARTIAL_WAKE_LOCK acquired successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire partial wake lock", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_LISTENING -> {
                isListeningPaused = true
                speechRecognizerManager?.stopListening()
            }
            ACTION_RESUME_LISTENING -> {
                isListeningPaused = false
                startWakeWordListening()
            }
            ACTION_START, null -> {
                isListeningPaused = false
                startForegroundWithNotification()
                startWakeWordListening()
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
            .setContentTitle("JARVIS Always-Listening")
            .setContentText("Say \"Jarvis\" or \"जार्विस\" anytime to wake.")
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
                    Log.d(TAG, "Service: background recognizer ready for wake word")
                }

                override fun onListeningStarted() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onPartialResult(text: String) {
                    if (containsWakeWord(text)) {
                        handleWakeWordTrigger(text)
                    }
                }

                override fun onFinalResult(text: String) {
                    Log.d(TAG, "Service: speech result: $text")
                    if (containsWakeWord(text)) {
                        handleWakeWordTrigger(text)
                    } else {
                        // Restart recognition loop for continuous wake word listening
                        restartWakeWordListening(500)
                    }
                }

                override fun onError(errorCode: Int, message: String) {
                    Log.d(TAG, "Service: Speech error ($errorCode): $message")
                    // If service running and not paused, quietly restart recognition loop
                    restartWakeWordListening(1000)
                }

                override fun onListeningStopped() {
                    if (_isServiceRunning.value && !isListeningPaused) {
                        restartWakeWordListening(600)
                    }
                }
            }
        )
    }

    private fun restartWakeWordListening(delayMillis: Long) {
        if (!_isServiceRunning.value || isListeningPaused) return
        serviceScope.launch {
            delay(delayMillis)
            startWakeWordListening()
        }
    }

    private fun startWakeWordListening() {
        if (!_isServiceRunning.value || isListeningPaused) return
        try {
            speechRecognizerManager?.startListening(preferHindi = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting wake word listener", e)
        }
    }

    private fun containsWakeWord(text: String): Boolean {
        val lower = text.lowercase().trim()
        return lower.contains("jarvis") ||
                lower.contains("जार्विस") ||
                lower.contains("जार्विश") ||
                lower.contains("जाविस") ||
                lower.contains("jarvish")
    }

    private fun extractCommandAfterWakeWord(text: String): String {
        var cleaned = text
            .replace(Regex("""(?i)\b(?:hey\s+|ok\s+|hello\s+)?jarvis\b"""), "")
            .replace(Regex("""(?:हे\s+|ओके\s+)?(?:जार्विस|जार्विश|जाविस)"""), "")
            .trim()

        if (cleaned.startsWith(",") || cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1).trim()
        }
        return cleaned
    }

    /**
     * Executes when the wake word "Jarvis" / "जार्विस" is detected:
     * 1. Acquires a temporary SCREEN_BRIGHT_WAKE_LOCK to wake screen immediately.
     * 2. Triggers a subtle, soft haptic vibration.
     * 3. Brings MainActivity to the foreground using Intent.FLAG_ACTIVITY_NEW_TASK.
     * 4. Passes any subsequent command directly to be executed.
     */
    private fun handleWakeWordTrigger(fullSpokenText: String) {
        Log.i(TAG, "WAKE WORD TRIGGERED: $fullSpokenText")
        isListeningPaused = true
        speechRecognizerManager?.stopListening()

        // 1. Wake the display up immediately
        wakeUpDeviceScreen()

        // 2. Subtle soft haptic feedback
        triggerSubtleHaptic()

        // 3. Extract command if user said "Jarvis open Spotify"
        val remainingCommand = extractCommandAfterWakeWord(fullSpokenText)

        // 4. Launch / bring MainActivity to front
        val bringToFrontIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("WAKE_WORD_DETECTED", true)
            if (remainingCommand.isNotBlank()) {
                putExtra("PENDING_COMMAND", remainingCommand)
            }
        }
        startActivity(bringToFrontIntent)

        // Post updates to latestCommand
        serviceScope.launch {
            _latestCommand.value = fullSpokenText
        }
    }

    private fun wakeUpDeviceScreen() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val screenLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "Jarvis::ScreenWakeUpLock"
            )
            screenLock.acquire(8000L /* 8 seconds */)
        } catch (e: Exception) {
            Log.w(TAG, "Failed waking up screen", e)
        }
    }

    private fun triggerSubtleHaptic() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed triggering haptic feedback", e)
        }
    }

    private fun stopForegroundService() {
        _isServiceRunning.value = false
        isListeningPaused = true
        speechRecognizerManager?.stopListening()
        speechRecognizerManager?.destroy()
        speechRecognizerManager = null

        try {
            partialWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            partialWakeLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing partial wake lock", e)
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "JarvisVoiceService stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "JARVIS Wake Word Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Always-listening background service for the Jarvis trigger phrase."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundService()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
