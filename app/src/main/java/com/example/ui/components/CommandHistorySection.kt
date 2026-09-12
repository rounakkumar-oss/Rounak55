package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.CommandHistoryEntity
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGlowListening
import com.example.ui.theme.JarvisNavyCard
import com.example.ui.theme.JarvisNavySurface
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommandHistorySection(
    history: List<CommandHistoryEntity>,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("command_history_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisNavySurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RECENT INTERACTIONS",
                        color = JarvisCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                if (history.isNotEmpty()) {
                    IconButton(
                        onClick = onClearHistory,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent voice commands. Tap the core or say a command to begin!",
                        color = TextSecondaryDark,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    history.take(5).forEach { item ->
                        CommandHistoryRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandHistoryRow(item: CommandHistoryEntity) {
    val timeString = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(item.timestamp))

    val (badgeIcon, badgeColor, badgeLabel) = when (item.actionType) {
        "OPEN_YOUTUBE" -> Triple(Icons.Default.SmartDisplay, Color(0xFFFF4444), "YOUTUBE")
        "PLAY_YOUTUBE" -> Triple(Icons.Default.PlayArrow, Color(0xFFFF6666), "PLAY MUSIC")
        "OPEN_APP" -> Triple(Icons.Default.Widgets, JarvisCyan, "APP INTENT")
        "CALL_PHONE" -> Triple(Icons.Default.Call, JarvisGlowListening, "CALL")
        else -> Triple(Icons.AutoMirrored.Filled.Chat, JarvisCyan, "AI CHAT")
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = JarvisNavyCard,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = badgeLabel,
                            tint = badgeColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = badgeLabel,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = timeString,
                    color = TextSecondaryDark,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "“${item.userQuery}”",
                color = TextPrimaryDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            if (item.assistantResponse.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.assistantResponse,
                    color = JarvisCyan.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
