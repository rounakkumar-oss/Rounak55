package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeminiAssistantService
import com.example.data.db.CommandHistoryEntity
import com.example.data.db.JarvisDatabase
import com.example.service.JarvisVoiceService
import com.example.util.ActionExecutor
import com.example.util.ParsedAction
import com.example.voice.SpeechRecognitionCallback
import com.example.voice.SpeechRecognizerManager
import com.example.voice.TextToSpeechManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VoiceState {
    object Idle : VoiceState
    data class Listening(val rmsLevel: Float = 0f, val partialText: String = "") : VoiceState
    object Thinking : VoiceState
    data class Speaking(val text: String) : VoiceState
}

enum class LanguagePreference {
    BILINGUAL,
    ENGLISH,
    HINDI
}

data class JarvisUiState(
    val voiceState: VoiceState = VoiceState.Idle,
    val lastUserQuery: String = "",
    val lastAssistantResponse: String = "Jarvis online. Tap the mic or say a command.",
    val isAlwaysListening: Boolean = false,
    val isSmartwatchMode: Boolean = false,
    val languagePreference: LanguagePreference = LanguagePreference.BILINGUAL,
    val isBackgroundServiceRunning: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val errorMessage: String? = null
)

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val db = JarvisDatabase.getInstance(application)
    private val commandDao = db.commandHistoryDao()
    private val geminiService = GeminiAssistantService()

    val commandHistory: StateFlow<List<CommandHistoryEntity>> = commandDao.getAllCommands()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _uiState = MutableStateFlow(JarvisUiState())
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    private var speechManager: SpeechRecognizerManager? = null
    private var ttsManager: TextToSpeechManager? = null

    init {
        checkOverlayPermission(application)
        initSpeechRecognizer(application)

        // Observe background service latest command
        viewModelScope.launch {
            JarvisVoiceService.latestCommand.collect { command ->
                if (!command.isNullOrBlank()) {
                    processCommand(application, command)
                }
            }
        }

        // Observe background service status
        viewModelScope.launch {
            JarvisVoiceService.isServiceRunning.collect { running ->
                _uiState.value = _uiState.value.copy(
                    isBackgroundServiceRunning = running,
                    isAlwaysListening = running
                )
            }
        }
    }

    fun setTtsManager(manager: TextToSpeechManager) {
        this.ttsManager = manager
        // Speak initial greeting once TTS is ready
        manager.speak(
            text = "Jarvis online, sir. At your service.",
            onDone = {
                _uiState.value = _uiState.value.copy(voiceState = VoiceState.Idle)
            }
        )
    }

    fun checkOverlayPermission(context: Context) {
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        _uiState.value = _uiState.value.copy(hasOverlayPermission = hasOverlay)
    }

    private fun initSpeechRecognizer(context: Context) {
        speechManager = SpeechRecognizerManager(
            context,
            object : SpeechRecognitionCallback {
                override fun onReadyForSpeech() {
                    _uiState.value = _uiState.value.copy(
                        voiceState = VoiceState.Listening(rmsLevel = 0f),
                        errorMessage = null
                    )
                }

                override fun onListeningStarted() {
                    val current = _uiState.value.voiceState
                    if (current !is VoiceState.Listening) {
                        _uiState.value = _uiState.value.copy(
                            voiceState = VoiceState.Listening(rmsLevel = 0f),
                            errorMessage = null
                        )
                    }
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val current = _uiState.value.voiceState
                    if (current is VoiceState.Listening) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                        _uiState.value = _uiState.value.copy(
                            voiceState = current.copy(rmsLevel = normalized)
                        )
                    }
                }

                override fun onPartialResult(text: String) {
                    val current = _uiState.value.voiceState
                    if (current is VoiceState.Listening) {
                        _uiState.value = _uiState.value.copy(
                            voiceState = current.copy(partialText = text)
                        )
                    }
                }

                override fun onFinalResult(text: String) {
                    _uiState.value = _uiState.value.copy(
                        lastUserQuery = text,
                        voiceState = VoiceState.Thinking
                    )
                    processCommand(context, text)
                }

                override fun onError(errorCode: Int, message: String) {
                    _uiState.value = _uiState.value.copy(
                        voiceState = VoiceState.Idle,
                        errorMessage = if (errorCode != 7) message else null
                    )
                    if (_uiState.value.isAlwaysListening) {
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(1500)
                            startListening()
                        }
                    }
                }

                override fun onListeningStopped() {
                    val current = _uiState.value.voiceState
                    if (current is VoiceState.Listening) {
                        _uiState.value = _uiState.value.copy(voiceState = VoiceState.Thinking)
                    }
                }
            }
        )
    }

    /**
     * Central Mic button handler: ONLY controls SpeechRecognizer, NEVER launches apps!
     */
    fun onMicClicked(hasAudioPermission: Boolean, onRequestPermission: () -> Unit) {
        if (!hasAudioPermission) {
            onRequestPermission()
            return
        }
        toggleListening()
    }

    fun toggleListening() {
        when (_uiState.value.voiceState) {
            is VoiceState.Listening -> stopListening()
            is VoiceState.Speaking -> {
                ttsManager?.stop()
                _uiState.value = _uiState.value.copy(voiceState = VoiceState.Idle)
            }
            is VoiceState.Thinking -> {
                // Currently processing; ignore
            }
            is VoiceState.Idle -> startListening()
        }
    }

    fun startListening() {
        ttsManager?.stop()
        _uiState.value = _uiState.value.copy(
            voiceState = VoiceState.Listening(0f),
            errorMessage = null
        )
        val preferHindi = _uiState.value.languagePreference == LanguagePreference.HINDI
        speechManager?.startListening(preferHindi)
    }

    fun stopListening() {
        speechManager?.stopListening()
        _uiState.value = _uiState.value.copy(voiceState = VoiceState.Idle)
    }

    fun processCommand(context: Context, commandText: String) {
        val query = commandText.trim()
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(voiceState = VoiceState.Idle)
            return
        }

        _uiState.value = _uiState.value.copy(
            lastUserQuery = query,
            voiceState = VoiceState.Thinking,
            errorMessage = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            val isHindi = when (_uiState.value.languagePreference) {
                LanguagePreference.HINDI -> true
                LanguagePreference.ENGLISH -> false
                LanguagePreference.BILINGUAL -> isHindiQuery(query)
            }

            val parsedAction = ActionExecutor.parseCommand(query)
            var responseText = ""
            var actionType = "CHAT"
            var success = true

            when (parsedAction) {
                is ParsedAction.Chat -> {
                    actionType = "AI_CONVERSATION"
                    responseText = geminiService.getResponse(query)
                    success = true
                }
                else -> {
                    val result = ActionExecutor.executeAction(context, parsedAction, isHindi)
                    responseText = result.spokenResponse
                    actionType = result.actionType
                    success = result.success
                }
            }

            // Save interaction to Room database
            commandDao.insertCommand(
                CommandHistoryEntity(
                    userQuery = query,
                    assistantResponse = responseText,
                    actionType = actionType,
                    language = if (isHindi) "hi" else "en",
                    success = success
                )
            )

            // Speak audio confirmation or conversational response back to user
            viewModelScope.launch(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    lastAssistantResponse = responseText,
                    voiceState = VoiceState.Speaking(responseText)
                )

                ttsManager?.speak(
                    text = responseText,
                    onStart = {
                        _uiState.value = _uiState.value.copy(
                            voiceState = VoiceState.Speaking(responseText)
                        )
                    },
                    onDone = {
                        _uiState.value = _uiState.value.copy(voiceState = VoiceState.Idle)
                        if (_uiState.value.isAlwaysListening) {
                            startListening()
                        }
                    },
                    onError = {
                        _uiState.value = _uiState.value.copy(voiceState = VoiceState.Idle)
                    }
                )
            }
        }
    }

    fun toggleAlwaysListening(context: Context) {
        val newState = !_uiState.value.isAlwaysListening
        _uiState.value = _uiState.value.copy(isAlwaysListening = newState)

        if (newState) {
            JarvisVoiceService.start(context)
            startListening()
        } else {
            JarvisVoiceService.stop(context)
            stopListening()
        }
    }

    fun toggleSmartwatchMode() {
        _uiState.value = _uiState.value.copy(isSmartwatchMode = !_uiState.value.isSmartwatchMode)
    }

    fun cycleLanguagePreference() {
        val next = when (_uiState.value.languagePreference) {
            LanguagePreference.BILINGUAL -> LanguagePreference.HINDI
            LanguagePreference.HINDI -> LanguagePreference.ENGLISH
            LanguagePreference.ENGLISH -> LanguagePreference.BILINGUAL
        }
        _uiState.value = _uiState.value.copy(languagePreference = next)
    }

    fun replayLastResponse() {
        val text = _uiState.value.lastAssistantResponse
        if (text.isNotBlank()) {
            _uiState.value = _uiState.value.copy(voiceState = VoiceState.Speaking(text))
            ttsManager?.speak(
                text = text,
                onDone = { _uiState.value = _uiState.value.copy(voiceState = VoiceState.Idle) }
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            commandDao.clearHistory()
        }
    }

    private fun isHindiQuery(text: String): Boolean {
        for (char in text) {
            if (Character.UnicodeBlock.of(char) == Character.UnicodeBlock.DEVANAGARI) {
                return true
            }
        }
        val lower = text.lowercase()
        val hindiKeywords = listOf(
            "kholo", "chalao", "lagao", "karo", "batao", "kaise", "kaisa", "namaste",
            "kya", "hai", "kaun", "mera", "meri", "aap", "tum", "dhanyawad", "shukriya",
            "samay", "tarikh", "baje", "khol", "chala", "baja", "bhai"
        )
        return hindiKeywords.any { lower.contains(it) }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager?.destroy()
    }
}
