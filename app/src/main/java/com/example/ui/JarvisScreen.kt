package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.components.ArcReactorVisualizer
import com.example.ui.components.CommandHistorySection
import com.example.ui.components.PermissionsCard
import com.example.ui.components.SmartwatchCompactView
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGlowListening
import com.example.ui.theme.JarvisGlowSpeaking
import com.example.ui.theme.JarvisGlowThinking
import com.example.ui.theme.JarvisNavyCard
import com.example.ui.theme.JarvisNavyDark
import com.example.ui.theme.JarvisNavySurface
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

@Composable
fun JarvisScreen(
    viewModel: JarvisViewModel,
    onRequestAudioPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val commandHistory by viewModel.commandHistory.collectAsState()
    val context = LocalContext.current

    val hasAudioPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    // Central Mic Click Action: ONLY controls SpeechRecognizer, NEVER launches apps
    val onMicAction: () -> Unit = {
        if (!hasAudioPermission) {
            onRequestAudioPermission()
        } else {
            viewModel.toggleListening()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(JarvisNavyDark)) {
        val isCompactScreen = maxWidth < 340.dp || maxHeight < 480.dp || uiState.isSmartwatchMode

        if (isCompactScreen) {
            SmartwatchCompactView(
                uiState = uiState,
                onReactorClick = onMicAction,
                onExitSmartwatchMode = { viewModel.toggleSmartwatchMode() },
                onReplaySpeech = { viewModel.replayLastResponse() }
            )
        } else {
            JarvisStandardView(
                uiState = uiState,
                commandHistory = commandHistory,
                onMicClick = onMicAction,
                onAlwaysListenToggle = {
                    if (!hasAudioPermission) {
                        onRequestAudioPermission()
                    } else {
                        viewModel.toggleAlwaysListening(context)
                    }
                },
                onSmartwatchToggle = { viewModel.toggleSmartwatchMode() },
                onLanguageToggle = { viewModel.cycleLanguagePreference() },
                onSendTypedCommand = { command -> viewModel.processCommand(context, command) },
                onReplaySpeech = { viewModel.replayLastResponse() },
                onClearHistory = { viewModel.clearHistory() },
                onRefreshOverlay = { viewModel.checkOverlayPermission(context) }
            )
        }
    }
}

@Composable
private fun JarvisStandardView(
    uiState: JarvisUiState,
    commandHistory: List<com.example.data.db.CommandHistoryEntity>,
    onMicClick: () -> Unit,
    onAlwaysListenToggle: () -> Unit,
    onSmartwatchToggle: () -> Unit,
    onLanguageToggle: () -> Unit,
    onSendTypedCommand: (String) -> Unit,
    onReplaySpeech: () -> Unit,
    onClearHistory: () -> Unit,
    onRefreshOverlay: () -> Unit
) {
    val scrollState = rememberScrollState()
    var isKeyboardInputOpen by remember { mutableStateOf(false) }
    var textInputQuery by remember { mutableStateOf("") }

    val quickCommands = listOf(
        "YouTube kholo",
        "Play Kesariya on YouTube",
        "Open WhatsApp",
        "Call Mom",
        "Open Camera",
        "Open Settings",
        "Time kya hua?",
        "Tell me a joke"
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = JarvisNavyDark,
        bottomBar = {
            Surface(
                color = JarvisNavySurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Keyboard input button
                    IconButton(
                        onClick = { isKeyboardInputOpen = !isKeyboardInputOpen },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(JarvisNavyCard)
                            .testTag("keyboard_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Type Command",
                            tint = if (isKeyboardInputOpen) JarvisCyan else TextSecondaryDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Main Glowing Voice FAB (Minimum 48.dp touch target)
                    val isListening = uiState.voiceState is VoiceState.Listening
                    val isSpeaking = uiState.voiceState is VoiceState.Speaking
                    val fabColor = when {
                        isListening -> JarvisGlowListening
                        isSpeaking -> JarvisGlowSpeaking
                        else -> JarvisCyan
                    }

                    FloatingActionButton(
                        onClick = onMicClick,
                        containerColor = fabColor,
                        contentColor = Color(0xFF041320),
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("voice_speak_fab")
                    ) {
                        Icon(
                            imageVector = when {
                                isListening -> Icons.Default.Hearing
                                isSpeaking -> Icons.Default.Stop
                                else -> Icons.Default.Mic
                            },
                            contentDescription = if (isListening) "Stop Listening" else "Speak Command",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Replay Audio button
                    IconButton(
                        onClick = onReplaySpeech,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(JarvisNavyCard)
                            .testTag("replay_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Replay Speech",
                            tint = JarvisCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title and Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(JarvisGlowListening)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "JARVIS",
                        color = JarvisCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VOICE AI",
                        color = TextSecondaryDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Header Action Chips: Watch Mode & Language
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onSmartwatchToggle,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("smartwatch_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Watch,
                            contentDescription = "Watch Mode",
                            tint = JarvisCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = JarvisNavyCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onLanguageToggle)
                            .testTag("language_toggle_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Language",
                                tint = JarvisCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (uiState.languagePreference) {
                                    LanguagePreference.BILINGUAL -> "EN/HI"
                                    LanguagePreference.HINDI -> "HINDI"
                                    LanguagePreference.ENGLISH -> "ENGLISH"
                                },
                                color = TextPrimaryDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Background Service Mode Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = JarvisNavySurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Always-Listen Background Service",
                            color = TextPrimaryDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (uiState.isAlwaysListening) "Jarvis active in foreground notification" else "Tap to enable background voice listening",
                            color = TextSecondaryDark,
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = uiState.isAlwaysListening,
                        onCheckedChange = { onAlwaysListenToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF041320),
                            checkedTrackColor = JarvisCyan,
                            uncheckedThumbColor = TextSecondaryDark,
                            uncheckedTrackColor = JarvisNavyCard
                        ),
                        modifier = Modifier.testTag("always_listen_switch")
                    )
                }
            }

            // Keyboard text input field
            AnimatedVisibility(
                visible = isKeyboardInputOpen,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = textInputQuery,
                        onValueChange = { textInputQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("command_text_input"),
                        placeholder = { Text("Type voice command (e.g. YouTube kholo)...", color = TextSecondaryDark, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisCyan.copy(alpha = 0.4f),
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        ),
                        trailingIcon = {
                            if (textInputQuery.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        onSendTypedCommand(textInputQuery)
                                        textInputQuery = ""
                                        isKeyboardInputOpen = false
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send Command",
                                        tint = JarvisCyan
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (textInputQuery.isNotBlank()) {
                                onSendTypedCommand(textInputQuery)
                                textInputQuery = ""
                                isKeyboardInputOpen = false
                            }
                        }),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Central Animated Arc Reactor Core (ONLY activates Android's SpeechRecognizer)
            ArcReactorVisualizer(
                voiceState = uiState.voiceState,
                size = 170.dp,
                onClick = onMicClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            // State Badge Pill
            val (badgeLabel, badgeColor) = when (uiState.voiceState) {
                is VoiceState.Listening -> Pair("LISTENING FOR COMMAND...", JarvisGlowListening)
                is VoiceState.Thinking -> Pair("PROCESSING COMMAND...", JarvisGlowThinking)
                is VoiceState.Speaking -> Pair("SPEAKING RESPONSE...", JarvisGlowSpeaking)
                is VoiceState.Idle -> Pair("JARVIS STANDBY • TAP TO SPEAK", JarvisCyan)
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = badgeColor.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.voiceState is VoiceState.Thinking) {
                        CircularProgressIndicator(
                            color = JarvisGlowThinking,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = badgeLabel,
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Error banner if any
            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Response & Query Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_speech_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JarvisNavySurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (uiState.lastUserQuery.isNotBlank() || (uiState.voiceState is VoiceState.Listening && (uiState.voiceState as VoiceState.Listening).partialText.isNotBlank())) {
                        val displayText = if (uiState.voiceState is VoiceState.Listening && (uiState.voiceState as VoiceState.Listening).partialText.isNotBlank()) {
                            (uiState.voiceState as VoiceState.Listening).partialText
                        } else {
                            uiState.lastUserQuery
                        }
                        Text(
                            text = "YOU SAID:",
                            color = TextSecondaryDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "“$displayText”",
                            color = JarvisCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Text(
                        text = "JARVIS:",
                        color = TextSecondaryDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = uiState.lastAssistantResponse,
                        color = TextPrimaryDark,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Voice Command Suggestions (Tapping populates the input field or executes)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "VOICE COMMAND EXAMPLES:",
                    color = TextSecondaryDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(quickCommands) { command ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = JarvisNavyCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    textInputQuery = command
                                    isKeyboardInputOpen = true
                                }
                                .testTag("quick_chip_${command.take(8).lowercase().replace(" ", "_")}")
                        ) {
                            Text(
                                text = command,
                                color = JarvisCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Permissions Card
            PermissionsCard(
                hasOverlayPermission = uiState.hasOverlayPermission,
                onOverlayRefreshed = onRefreshOverlay
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Command History Section
            CommandHistorySection(
                history = commandHistory,
                onClearHistory = onClearHistory
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
