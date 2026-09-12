package com.example.data.ai

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeminiAssistantService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "GeminiAssistant"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"
    }

    suspend fun getResponse(userPrompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        // If key is empty or default placeholder, use smart local conversational response
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "No valid Gemini API key found, utilizing smart local engine")
            return@withContext LocalConversationalEngine.generateReply(userPrompt)
        }

        try {
            val systemInstruction = "You are JARVIS, a sophisticated, loyal, and concise AI voice assistant created to assist the user. " +
                    "Keep your responses short, natural, and conversational (1 to 2 sentences max) so they sound great when read aloud via Text-to-Speech. " +
                    "You fluently understand and speak both English and Hindi/Hinglish. " +
                    "If the user asks in Hindi or Hinglish, answer in clear, polite Hindi or Hinglish. " +
                    "If the user asks in English, answer in English. " +
                    "Never output markdown symbols, asterisks, or bullet points as they degrade voice speech."

            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", userPrompt)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 120)
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text", "").trim()
                        if (text.isNotBlank()) {
                            // Strip any accidental markdown formatting
                            return@withContext text.replace("*", "").replace("#", "").trim()
                        }
                    }
                }
            } else {
                Log.w(TAG, "Gemini API returned code: ${response.code}, falling back to local engine")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini API call failed: ${e.message}", e)
        }

        // Fallback to local intelligence if API call fails or times out
        return@withContext LocalConversationalEngine.generateReply(userPrompt)
    }
}

object LocalConversationalEngine {
    fun generateReply(prompt: String): String {
        val lower = prompt.lowercase().trim()
        val isHindi = isHindiQuery(lower)

        return when {
            // Greetings
            lower.contains("hello") || lower.contains("hi jarvis") || lower.contains("hey jarvis") -> {
                "Hello sir! All systems are online. How may I assist you today?"
            }
            lower.contains("namaste") || lower.contains("namaskar") -> {
                "Namaste sir! Main aapki seva mein hazir hoon. Batayein kya sahayata chahiye?"
            }
            lower.contains("kaise ho") || lower.contains("kaisa chal raha") -> {
                "Main theek hoon sir, fully operational aur aapki madad ke liye taiyar hoon!"
            }
            lower.contains("how are you") || lower.contains("how are you doing") -> {
                "I am functioning at peak efficiency, sir. Ready for your command."
            }

            // Identity
            lower.contains("who are you") || lower.contains("what is your name") -> {
                "I am JARVIS, your intelligent personal voice assistant."
            }
            lower.contains("tum kaun ho") || lower.contains("naam kya hai") -> {
                "Mera naam JARVIS hai, aapka personal voice assistant. Main aapke phone controls aur sawaalon mein madad karta hoon."
            }

            // Time & Date
            lower.contains("time") || lower.contains("samay") || lower.contains("kitne baje") -> {
                val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                if (isHindi) "Abhi samay hai $time." else "The current time is $time, sir."
            }
            lower.contains("date") || lower.contains("tarikh") || lower.contains("taarikh") || lower.contains("today") -> {
                val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
                if (isHindi) "Aaj $date hai." else "Today is $date, sir."
            }

            // Capabilities
            lower.contains("what can you do") || lower.contains("help") || lower.contains("madad") || lower.contains("kya kar sakte ho") -> {
                if (isHindi) {
                    "Aap mujhse YouTube kholne, gana chalane, WhatsApp ya Camera kholne, ya kisi ko call lagane ke liye bol sakte hain."
                } else {
                    "I can open YouTube, search and play songs, open WhatsApp or Camera, make phone calls, or answer your questions."
                }
            }

            // Jokes
            lower.contains("joke") || lower.contains("chutkula") || lower.contains("hasao") -> {
                if (isHindi) {
                    "Ek baar computer doctor ke paas gaya aur bola: Doctor sahab, mujhe lagta hai mujhe virus ho gaya hai!"
                } else {
                    "Why do programmers prefer dark mode? Because light attracts bugs, sir!"
                }
            }

            // Appreciation
            lower.contains("thank you") || lower.contains("thanks") || lower.contains("shukriya") || lower.contains("dhanyawad") -> {
                if (isHindi) "Shukriya ki koi baat nahi sir, yeh toh mera farz tha." else "Always at your service, sir."
            }

            // Good morning / night
            lower.contains("good morning") || lower.contains("shubh prabhat") -> {
                if (isHindi) "Shubh prabhat sir! Aapka din shandar rahe." else "Good morning sir. All systems are initialized and ready."
            }
            lower.contains("good night") || lower.contains("shubh ratri") -> {
                if (isHindi) "Shubh ratri sir. Aaram kijiye." else "Good night, sir. Standing by whenever you need me."
            }

            // Fallback general responses
            else -> {
                if (isHindi) {
                    "Maine aapka aadesh suna: \"$prompt\". Main ispar dhyan de raha hoon."
                } else {
                    "Understood, sir. Processing: \"$prompt\". How else may I assist you?"
                }
            }
        }
    }

    private fun isHindiQuery(text: String): Boolean {
        // Check for Devanagari Unicode block
        for (char in text) {
            if (Character.UnicodeBlock.of(char) == Character.UnicodeBlock.DEVANAGARI) {
                return true
            }
        }
        val hindiKeywords = listOf(
            "kholo", "chalao", "lagao", "karo", "batao", "kaise", "kaisa", "namaste",
            "kya", "hai", "kaun", "mera", "meri", "aap", "tum", "dhanyavaad", "shukriya",
            "samay", "tarikh", "baje"
        )
        return hindiKeywords.any { text.contains(it) }
    }
}
