package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

interface SpeechRecognitionCallback {
    fun onReadyForSpeech()
    fun onListeningStarted()
    fun onRmsChanged(rmsdB: Float)
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onError(errorCode: Int, message: String)
    fun onListeningStopped()
}

class SpeechRecognizerManager(
    private val context: Context,
    private val callback: SpeechRecognitionCallback
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var originalSystemVolume = -1
    private var originalNotificationVolume = -1
    private var isStreamsMuted = false

    companion object {
        private const val TAG = "SpeechRecognizerManager"
    }

    init {
        runOnMainThread { initRecognizer() }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /**
     * Suppresses standard Google / Android SpeechRecognizer "ding/ton" start & stop beeps
     * by muting system and notification audio streams temporarily.
     */
    private fun suppressSystemSearchChime() {
        try {
            if (audioManager == null || isStreamsMuted) return

            if (originalSystemVolume == -1) {
                originalSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
            }
            if (originalNotificationVolume == -1) {
                originalNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true)
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true)
            }
            isStreamsMuted = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed suppressing recognizer chime", e)
        }
    }

    /**
     * Safely restores system sound levels once microphone has started recording.
     */
    private fun restoreSystemSounds() {
        try {
            if (audioManager == null || !isStreamsMuted) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false)
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false)
            }

            if (originalSystemVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, originalSystemVolume, 0)
                originalSystemVolume = -1
            }
            if (originalNotificationVolume >= 0) {
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalNotificationVolume, 0)
                originalNotificationVolume = -1
            }
            isStreamsMuted = false
        } catch (e: Exception) {
            Log.w(TAG, "Failed restoring audio streams", e)
        }
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition is not available on this device")
            callback.onError(-1, "Speech recognition not available on device")
            return
        }

        try {
            destroyInternal()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "onReadyForSpeech: microphone active (chime suppressed)")
                        isListening = true
                        callback.onReadyForSpeech()
                        callback.onListeningStarted()

                        // Unmute system chime safely after start beep window has elapsed
                        mainHandler.postDelayed({
                            restoreSystemSounds()
                        }, 350)
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "onBeginningOfSpeech: user started speaking")
                        callback.onListeningStarted()
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        callback.onRmsChanged(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "onEndOfSpeech: speech finished, processing")
                        isListening = false
                        restoreSystemSounds()
                        callback.onListeningStopped()
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        restoreSystemSounds()
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network error"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try speaking again."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy, resetting..."
                            SpeechRecognizer.ERROR_SERVER -> "Server error"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                            else -> "Speech recognition error ($error)"
                        }
                        Log.w(TAG, "onError: $error ($errorMsg)")
                        callback.onError(error, errorMsg)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        restoreSystemSounds()
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        Log.d(TAG, "onResults: $text")
                        if (text.isNotBlank()) {
                            callback.onFinalResult(text)
                        } else {
                            callback.onError(SpeechRecognizer.ERROR_NO_MATCH, "No speech detected")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotBlank()) {
                            callback.onPartialResult(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing SpeechRecognizer", e)
            callback.onError(-1, e.message ?: "Failed initializing speech recognizer")
        }
    }

    fun startListening(preferHindi: Boolean = false) {
        runOnMainThread {
            if (speechRecognizer == null) {
                initRecognizer()
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                // Support Hindi ("hi-IN") and English ("en-IN")
                if (preferHindi) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                } else {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                }
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN", "en-US"))
            }

            try {
                // Suppress Google "ding" sound before recognizer activation
                suppressSystemSearchChime()
                speechRecognizer?.startListening(intent)
                isListening = true
                callback.onListeningStarted()
            } catch (e: Exception) {
                restoreSystemSounds()
                Log.e(TAG, "Failed to start listening", e)
                callback.onError(-1, e.message ?: "Failed to start listening")
            }
        }
    }

    fun stopListening() {
        runOnMainThread {
            try {
                if (isListening) {
                    speechRecognizer?.stopListening()
                    isListening = false
                    restoreSystemSounds()
                    callback.onListeningStopped()
                }
            } catch (e: Exception) {
                restoreSystemSounds()
                Log.e(TAG, "Error stopping SpeechRecognizer", e)
            }
        }
    }

    fun cancel() {
        runOnMainThread {
            try {
                speechRecognizer?.cancel()
                isListening = false
                restoreSystemSounds()
                callback.onListeningStopped()
            } catch (e: Exception) {
                restoreSystemSounds()
                Log.e(TAG, "Error cancelling SpeechRecognizer", e)
            }
        }
    }

    private fun destroyInternal() {
        try {
            restoreSystemSounds()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying SpeechRecognizer", e)
        }
        speechRecognizer = null
        isListening = false
    }

    fun destroy() {
        runOnMainThread {
            destroyInternal()
        }
    }
}
