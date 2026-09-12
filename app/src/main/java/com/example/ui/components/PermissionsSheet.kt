package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGlowListening
import com.example.ui.theme.JarvisNavyCard
import com.example.ui.theme.JarvisNavySurface
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

@Composable
fun PermissionsCard(
    hasOverlayPermission: Boolean,
    onOverlayRefreshed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var audioGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var callGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var contactsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        audioGranted = results[Manifest.permission.RECORD_AUDIO] ?: audioGranted
        callGranted = results[Manifest.permission.CALL_PHONE] ?: callGranted
        contactsGranted = results[Manifest.permission.READ_CONTACTS] ?: contactsGranted
    }

    val allEssentialGranted = audioGranted && callGranted && contactsGranted

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("permissions_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = JarvisNavySurface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (allEssentialGranted && hasOverlayPermission) JarvisGlowListening.copy(alpha = 0.4f)
            else JarvisCyan.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYSTEM PERMISSIONS",
                    color = JarvisCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (allEssentialGranted) JarvisGlowListening.copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (allEssentialGranted) "READY" else "ACTION NEEDED",
                        color = if (allEssentialGranted) JarvisGlowListening else Color(0xFFF59E0B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Microphone
            PermissionItem(
                title = "Microphone (Audio Input)",
                description = "Required to listen to your voice commands.",
                icon = Icons.Default.Mic,
                isGranted = audioGranted,
                onRequest = {
                    permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Call Phone
            PermissionItem(
                title = "Phone Calling (CALL_PHONE)",
                description = "Enables placing hands-free phone calls.",
                icon = Icons.Default.Call,
                isGranted = callGranted,
                onRequest = {
                    permissionsLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Contacts
            PermissionItem(
                title = "Contacts Lookup (READ_CONTACTS)",
                description = "Allows calling contacts by name (e.g. \"Call Mom\").",
                icon = Icons.Default.Contacts,
                isGranted = contactsGranted,
                onRequest = {
                    permissionsLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // System Alert Window / Overlay
            PermissionItem(
                title = "Screen Overlay (SYSTEM_ALERT_WINDOW)",
                description = "Enables floating assistant widget over other apps.",
                icon = Icons.Default.PictureInPicture,
                isGranted = hasOverlayPermission,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                    onOverlayRefreshed()
                }
            )

            if (!allEssentialGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        permissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CALL_PHONE,
                                Manifest.permission.READ_CONTACTS
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_all_permissions_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan)
                ) {
                    Text("Grant Required Permissions", color = Color(0xFF041320), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(JarvisNavyCard)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) JarvisGlowListening.copy(alpha = 0.15f) else JarvisCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isGranted) JarvisGlowListening else JarvisCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    color = TextPrimaryDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    color = TextSecondaryDark,
                    fontSize = 10.sp,
                    lineHeight = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Granted",
                tint = JarvisGlowListening,
                modifier = Modifier.size(20.dp)
            )
        } else {
            OutlinedButton(
                onClick = onRequest,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = JarvisCyan),
                border = androidx.compose.foundation.BorderStroke(1.dp, JarvisCyan)
            ) {
                Text("Allow", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
