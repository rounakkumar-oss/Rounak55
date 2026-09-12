package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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

        // 4. Initialize Android's TextToSpeech engine in onCreate
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
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
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

    override fun onResume() {
        super.onResume()
        viewModel.checkOverlayPermission(this)
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
