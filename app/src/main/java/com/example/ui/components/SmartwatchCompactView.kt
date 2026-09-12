package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.JarvisUiState
import com.example.ui.VoiceState
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGlowListening
import com.example.ui.theme.JarvisGlowSpeaking
import com.example.ui.theme.JarvisGlowThinking
import com.example.ui.theme.JarvisNavyCard
import com.example.ui.theme.JarvisNavyDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

@Composable
fun SmartwatchCompactView(
    uiState: JarvisUiState,
    onReactorClick: () -> Unit,
    onExitSmartwatchMode: () -> Unit,
    onReplaySpeech: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val stateBadgeColor = when (uiState.voiceState) {
        is VoiceState.Listening -> JarvisGlowListening
        is VoiceState.Thinking -> JarvisGlowThinking
        is VoiceState.Speaking -> JarvisGlowSpeaking
        is VoiceState.Idle -> JarvisCyan
    }

    val stateText = when (uiState.voiceState) {
        is VoiceState.Listening -> "LISTENING..."
        is VoiceState.Thinking -> "PROCESSING..."
        is VoiceState.Speaking -> "SPEAKING..."
        is VoiceState.Idle -> "TAP TO SPEAK"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisNavyDark)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer decorative smartwatch circular bezel indicator
        Box(
            modifier = Modifier
                .size(310.dp)
                .clip(CircleShape)
                .border(2.dp, JarvisCyan.copy(alpha = 0.25f), CircleShape)
                .background(Color.Black.copy(alpha = 0.6f))
        )

        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Watch Mode Label & Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "JARVIS WATCH",
                    color = JarvisCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                IconButton(
                    onClick = onExitSmartwatchMode,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("exit_smartwatch_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit Watch Mode",
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // State Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = stateBadgeColor.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, stateBadgeColor.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(stateBadgeColor)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = stateText,
                        color = stateBadgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Central Arc Reactor Button (ONLY activates Android's SpeechRecognizer)
            ArcReactorVisualizer(
                voiceState = uiState.voiceState,
                size = 126.dp,
                onClick = onReactorClick
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Speech & Response Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = JarvisNavyCard.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.lastUserQuery.isNotBlank()) {
                        Text(
                            text = "\"${uiState.lastUserQuery}\"",
                            color = JarvisCyan.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Text(
                        text = uiState.lastAssistantResponse,
                        color = TextPrimaryDark,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    IconButton(
                        onClick = onReplaySpeech,
                        modifier = Modifier
                            .size(36.dp)
                            .padding(top = 2.dp)
                            .testTag("watch_replay_audio")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Replay Speech",
                            tint = JarvisCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
