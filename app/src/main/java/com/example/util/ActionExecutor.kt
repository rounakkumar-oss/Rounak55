package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.net.URLEncoder

sealed class ParsedAction {
    object OpenYouTube : ParsedAction()
    data class PlayYouTube(val songQuery: String) : ParsedAction()
    data class OpenApp(val appType: AppType) : ParsedAction()
    data class MakeCall(val target: String) : ParsedAction()
    data class Chat(val prompt: String) : ParsedAction()
}

enum class AppType {
    DIALER,
    CAMERA,
    SETTINGS,
    WHATSAPP
}

data class ActionResult(
    val spokenResponse: String,
    val success: Boolean,
    val actionType: String
)

object ActionExecutor {

    /**
     * Parses the spoken input strictly based on explicit keywords.
     * General talk or questions fall back to ParsedAction.Chat.
     */
    fun parseCommand(input: String): ParsedAction {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ParsedAction.Chat("")

        val lower = trimmed.lowercase()

        // 1. YouTube Commands (ONLY if the input explicitly mentions youtube / यूट्यूब)
        val hasYouTubeWord = lower.contains("youtube") ||
                lower.contains("यूट्यूब") ||
                lower.contains("युटुब") ||
                lower.contains("yt")

        if (hasYouTubeWord) {
            // Check for play music/video search command
            // English: "play [song] on youtube", "search [query] on youtube", "play on youtube [song]"
            // Hindi: "youtube par [song] chalao", "यूट्यूब पर [गाने का नाम] चलाओ", "youtube pe [song] bajao"

            val isPlayIntent = lower.contains("play") ||
                    lower.contains("chalao") ||
                    lower.contains("chala do") ||
                    lower.contains("bajao") ||
                    lower.contains("baja do") ||
                    lower.contains("search") ||
                    lower.contains("सर्च") ||
                    lower.contains("चलाओ") ||
                    lower.contains("बजाओ")

            if (isPlayIntent) {
                val extractedQuery = extractSongQuery(trimmed, lower)
                if (extractedQuery.isNotBlank()) {
                    return ParsedAction.PlayYouTube(extractedQuery)
                }
            }

            // Otherwise, it's an explicit command to open YouTube
            // e.g. "open youtube", "youtube kholo", "यूट्यूब खोलो", "launch youtube", "youtube"
            return ParsedAction.OpenYouTube
        }

        // 2. Standard Apps - ONLY when explicitly named
        // Camera
        if (isCameraCommand(lower)) {
            return ParsedAction.OpenApp(AppType.CAMERA)
        }

        // Phone / Dialer
        if (isDialerCommand(lower)) {
            return ParsedAction.OpenApp(AppType.DIALER)
        }

        // Settings
        if (isSettingsCommand(lower)) {
            return ParsedAction.OpenApp(AppType.SETTINGS)
        }

        // WhatsApp
        if (isWhatsAppCommand(lower)) {
            return ParsedAction.OpenApp(AppType.WHATSAPP)
        }

        // Phone call to contact (e.g. "call mom", "राहुल को कॉल करो")
        if (isCallCommand(lower)) {
            val target = extractCallTarget(trimmed, lower)
            if (target.isNotBlank()) {
                return ParsedAction.MakeCall(target)
            }
        }

        // 3. Casual talk, questions, or general conversation -> Chat with TTS
        return ParsedAction.Chat(trimmed)
    }

    private fun extractSongQuery(original: String, lower: String): String {
        // Regex patterns for extracting song names
        val patterns = listOf(
            Regex("""(?:play|search)\s+(.+?)\s+(?:on\s+youtube|in\s+youtube)""", RegexOption.IGNORE_CASE),
            Regex("""(?:play|search)\s+on\s+youtube\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:youtube\s+(?:par|pe|me|mein))\s+(.+?)\s+(?:chalao|bajao|search\s+karo|play\s+karo)""", RegexOption.IGNORE_CASE),
            Regex("""(?:यूट्यूब\s+(?:पर|में))\s+(.+?)\s+(?:चलाओ|बजाओ|सर्च\s+करो)""", RegexOption.IGNORE_CASE),
            Regex("""(.+?)\s+(?:on\s+youtube|youtube\s+pe|youtube\s+par)""", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(original)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                if (extracted.isNotBlank()) {
                    return cleanSongQuery(extracted)
                }
            }
        }

        // Fallback cleanup by stripping trigger words
        var cleaned = lower
            .replace("play on youtube", "")
            .replace("play in youtube", "")
            .replace("on youtube", "")
            .replace("in youtube", "")
            .replace("youtube par", "")
            .replace("youtube pe", "")
            .replace("यूट्यूब पर", "")
            .replace("यूट्यूब में", "")
            .replace("youtube", "")
            .replace("यूट्यूब", "")
            .replace("play", "")
            .replace("chalao", "")
            .replace("bajao", "")
            .replace("चलाओ", "")
            .replace("बजाओ", "")
            .replace("kholo", "")
            .replace("खोलो", "")
            .trim()

        return cleaned
    }

    private fun cleanSongQuery(query: String): String {
        return query
            .replace(Regex("""(?i)^(song|gana|video|music)\s+"""), "")
            .replace(Regex("""(?i)\s+(song|gana|video|music)$"""), "")
            .trim()
    }

    private fun isCameraCommand(lower: String): Boolean {
        return (lower.contains("camera") || lower.contains("कैमरा")) &&
                (lower.contains("open") || lower.contains("kholo") || lower.contains("start") ||
                        lower.contains("launch") || lower.contains("खोलो") || lower.contains("चालू")) ||
                lower == "open camera" || lower == "कैमरा खोलो" || lower == "take a picture" ||
                lower == "take photo" || lower == "photo khincho" || lower == "फोटो खींचो"
    }

    private fun isDialerCommand(lower: String): Boolean {
        return (lower.contains("dialer") || lower.contains("डायलर") || lower.contains("dial pad")) ||
                ((lower.contains("phone") || lower.contains("फ़ोन") || lower.contains("फोन")) &&
                        (lower.contains("open") || lower.contains("kholo") || lower.contains("खोलो"))) ||
                lower == "open dialer" || lower == "open phone" || lower == "डायलर खोलो"
    }

    private fun isSettingsCommand(lower: String): Boolean {
        return (lower.contains("setting") || lower.contains("सेटिंग")) &&
                (lower.contains("open") || lower.contains("kholo") || lower.contains("launch") ||
                        lower.contains("खोलो") || lower == "settings" || lower == "open settings")
    }

    private fun isWhatsAppCommand(lower: String): Boolean {
        return (lower.contains("whatsapp") || lower.contains("व्हाट्सएप") || lower.contains("व्हाट्सऐप")) &&
                (lower.contains("open") || lower.contains("kholo") || lower.contains("खोलो") ||
                        lower.contains("launch") || lower.contains("start") || lower == "open whatsapp")
    }

    private fun isCallCommand(lower: String): Boolean {
        return lower.startsWith("call ") ||
                lower.startsWith("कॉल ") ||
                (lower.contains("ko call") || lower.contains("को कॉल") || lower.contains("ko phone") || lower.contains("को फोन"))
    }

    private fun extractCallTarget(original: String, lower: String): String {
        val hindiMatch = Regex("""(.+?)\s+(?:ko|को)\s+(?:call|phone|कॉल|फोन)\s*(?:karo|lagao|करो|लगाओ)?""", RegexOption.IGNORE_CASE).find(original)
        if (hindiMatch != null) {
            return hindiMatch.groupValues[1].trim()
        }

        if (lower.startsWith("call ")) {
            return original.substring(5).trim()
        }
        if (lower.startsWith("कॉल ")) {
            return original.substring(4).trim()
        }
        return ""
    }

    /**
     * Executes the parsed command and produces a spoken confirmation in Hindi or English.
     */
    fun executeAction(context: Context, action: ParsedAction, isHindi: Boolean): ActionResult {
        return when (action) {
            is ParsedAction.OpenYouTube -> executeOpenYouTube(context, isHindi)
            is ParsedAction.PlayYouTube -> executePlayYouTube(context, action.songQuery, isHindi)
            is ParsedAction.OpenApp -> executeOpenApp(context, action.appType, isHindi)
            is ParsedAction.MakeCall -> executeMakeCall(context, action.target, isHindi)
            is ParsedAction.Chat -> ActionResult("", false, "CHAT")
        }
    }

    private fun executeOpenYouTube(context: Context, isHindi: Boolean): ActionResult {
        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage("com.google.android.youtube")
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)

            val msg = if (isHindi) "यूट्यूब खोल रहा हूँ" else "Opening YouTube"
            ActionResult(msg, true, "OPEN_YOUTUBE")
        } catch (e: Exception) {
            val msg = if (isHindi) "यूट्यूब नहीं खुल पाया" else "Unable to open YouTube"
            ActionResult(msg, false, "OPEN_YOUTUBE")
        }
    }

    private fun executePlayYouTube(context: Context, songQuery: String, isHindi: Boolean): ActionResult {
        return try {
            val queryToSearch = if (songQuery.isBlank()) "Music" else songQuery
            val encodedQuery = URLEncoder.encode(queryToSearch, "UTF-8")

            val appIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", queryToSearch)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            if (context.packageManager.resolveActivity(appIntent, 0) != null) {
                context.startActivity(appIntent)
            } else {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=$encodedQuery")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
            }

            val msg = if (isHindi) {
                "यूट्यूब पर $queryToSearch चला रहा हूँ"
            } else {
                "Playing $queryToSearch on YouTube"
            }
            ActionResult(msg, true, "PLAY_YOUTUBE")
        } catch (e: Exception) {
            val msg = if (isHindi) "यूट्यूब पर नहीं चला पाया" else "Could not play on YouTube"
            ActionResult(msg, false, "PLAY_YOUTUBE")
        }
    }

    private fun executeOpenApp(context: Context, appType: AppType, isHindi: Boolean): ActionResult {
        return try {
            val intent: Intent = when (appType) {
                AppType.CAMERA -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                AppType.DIALER -> Intent(Intent.ACTION_DIAL)
                AppType.SETTINGS -> Intent(Settings.ACTION_SETTINGS)
                AppType.WHATSAPP -> {
                    context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                        ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com"))
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            val msg = when (appType) {
                AppType.CAMERA -> if (isHindi) "कैमरा खोल रहा हूँ" else "Opening Camera"
                AppType.DIALER -> if (isHindi) "फ़ोन डायलर खोल रहा हूँ" else "Opening Phone Dialer"
                AppType.SETTINGS -> if (isHindi) "सेटिंग्स खोल रहा हूँ" else "Opening Settings"
                AppType.WHATSAPP -> if (isHindi) "व्हाट्सएप खोल रहा हूँ" else "Opening WhatsApp"
            }
            ActionResult(msg, true, "OPEN_APP")
        } catch (e: Exception) {
            val msg = if (isHindi) "ऐप खोलने में विफल रहा" else "Failed to open app"
            ActionResult(msg, false, "OPEN_APP")
        }
    }

    private fun executeMakeCall(context: Context, target: String, isHindi: Boolean): ActionResult {
        val cleanTarget = target.trim()
        val isNumeric = cleanTarget.replace("+", "").replace("-", "").replace(" ", "").all { it.isDigit() }

        var phoneNumber = ""
        var resolvedName = cleanTarget

        if (isNumeric) {
            phoneNumber = cleanTarget
        } else {
            val match = ContactHelper.searchContact(context, cleanTarget)
            if (match != null) {
                phoneNumber = match.phoneNumber
                resolvedName = match.name
            }
        }

        if (phoneNumber.isBlank()) {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            val msg = if (isHindi) {
                "$cleanTarget का नंबर नहीं मिला, डायलर खोल दिया है"
            } else {
                "Contact for $cleanTarget not found, opened dialer"
            }
            return ActionResult(msg, false, "CALL_PHONE")
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            val intent = if (hasCallPermission) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            val msg = if (isHindi) {
                "$resolvedName को कॉल कर रहा हूँ"
            } else {
                "Calling $resolvedName"
            }
            ActionResult(msg, true, "CALL_PHONE")
        } catch (e: Exception) {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
            val msg = if (isHindi) "$resolvedName के लिए डायलर खोल दिया है" else "Opening dialer for $resolvedName"
            ActionResult(msg, true, "CALL_PHONE")
        }
    }
}
