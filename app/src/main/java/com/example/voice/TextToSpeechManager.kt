package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

class TextToSpeechManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit = {}
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val hindiLocale = Locale.forLanguageTag("hi-IN")
    private val englishLocale = Locale.forLanguageTag("en-IN")

    private var pendingUtterance: PendingSpeech? = null

    private data class PendingSpeech(
        val text: String,
        val onStart: () -> Unit,
        val onDone: () -> Unit,
        val onError: () -> Unit
    )

    companion object {
        private const val TAG = "TTSManager"
    }

    init {
        initializeTts()
    }

    private fun initializeTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                try {
                    tts?.setSpeechRate(0.95f)
                    tts?.setPitch(1.0f)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set rate/pitch", e)
                }
                onInitComplete(true)
                Log.d(TAG, "TextToSpeech initialized successfully")

                // Flush pending speech if queued during startup
                pendingUtterance?.let { pending ->
                    pendingUtterance = null
                    speak(pending.text, pending.onStart, pending.onDone, pending.onError)
                }
            } else {
                isInitialized = false
                onInitComplete(false)
                Log.e(TAG, "TextToSpeech initialization failed with status $status")
            }
        }
    }

    fun speak(
        text: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            onDone()
            return
        }

        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS not yet initialized, queuing speech: $cleanText")
            pendingUtterance = PendingSpeech(cleanText, onStart, onDone, onError)
            return
        }

        val utteranceId = UUID.randomUUID().toString()

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                if (id == utteranceId) {
                    onStart()
                }
            }

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    onDone()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    onError()
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) {
                    onError()
                }
            }
        })

        // Choose best locale based on text content
        val localeToUse = if (isHindiText(cleanText)) {
            val availability = tts?.isLanguageAvailable(hindiLocale) ?: TextToSpeech.LANG_NOT_SUPPORTED
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                hindiLocale
            } else {
                englishLocale
            }
        } else {
            englishLocale
        }

        try {
            tts?.language = localeToUse
        } catch (e: Exception) {
            Log.w(TAG, "Failed setting language $localeToUse, falling back to default", e)
        }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        try {
            pendingUtterance = null
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }

    fun shutdown() {
        try {
            pendingUtterance = null
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
        tts = null
        isInitialized = false
    }

    private fun isHindiText(text: String): Boolean {
        for (char in text) {
            if (Character.UnicodeBlock.of(char) == Character.UnicodeBlock.DEVANAGARI) {
                return true
            }
        }
        val lower = text.lowercase()
        val hindiKeywords = listOf(
            "kholo", "chalao", "lagao", "karo", "batao", "kaise", "kaisa", "namaste",
            "kya", "hai", "kaun", "mera", "meri", "aap", "tum", "dhanyawad", "shukriya",
            "samay", "tarikh", "baje", "raha", "rahe", "nahi", "theek", "hoon", "kar",
            "रहा", "हूँ", "खोल", "चला", "कॉल"
        )
        return hindiKeywords.any { lower.contains(it) }
    }
}
