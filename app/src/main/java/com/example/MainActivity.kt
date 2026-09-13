package com.example

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.example.service.JarvisVoiceService
import com.example.ui.JarvisScreen
import com.example.ui.JarvisViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.voice.TextToSpeechManager

class MainActivity : ComponentActivity() {
    private val viewModel: JarvisViewModel by viewModels()
    private var ttsManager: TextToSpeechManager? = null

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening()
        } else {
            Toast.makeText(this, "Microphone permission is required for voice commands", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureLockscreenAndWakeFlags()

        // 5. Initialize TextToSpeech engine in onCreate
        ttsManager = TextToSpeechManager(this) { success ->
            if (!success) {
                android.util.Log.w("MainActivity", "TTS failed initialization")
            }
        }
        viewModel.setTtsManager(ttsManager!!)

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    JarvisScreen(
                        viewModel = viewModel,
                        onRequestAudioPermission = {
                            requestMicrophonePermission()
                        },
                        onRequestBatteryExemption = {
                            requestBatteryOptimizationExemption()
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        handleIntentExtras(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureLockscreenAndWakeFlags()
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent?) {
        if (intent == null) return
        val isWakeWord = intent.getBooleanExtra("WAKE_WORD_DETECTED", false)
        val pendingCommand = intent.getStringExtra("PENDING_COMMAND")

        if (isWakeWord) {
            if (!pendingCommand.isNullOrBlank()) {
                // User said "Jarvis open Spotify"
                viewModel.processCommand(this, pendingCommand)
            } else {
                // User simply said "Jarvis" -> start listening for speech command immediately
                requestMicrophonePermission()
            }
        }
    }

    /**
     * Configures display to wake up over lock screen when triggered by wake word
     */
    private fun configureLockscreenAndWakeFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun requestMicrophonePermission() {
        val currentPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        )
        if (currentPermission != PackageManager.PERMISSION_GRANTED) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            viewModel.startListening()
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Battery optimization is already disabled for Jarvis", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Fallback to general battery settings
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (ignored: Exception) {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkOverlayPermission(this)

        // Pause background service listening while UI is active so they don't fight over mic
        if (JarvisVoiceService.isServiceRunning.value) {
            JarvisVoiceService.pauseListening(this)
        }
    }

    override fun onPause() {
        super.onPause()
        // Resume background service listening when user minimizes app or screen turns off
        if (JarvisVoiceService.isServiceRunning.value) {
            JarvisVoiceService.resumeListening(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsManager?.shutdown()
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme { Greeting("Android") }
}
