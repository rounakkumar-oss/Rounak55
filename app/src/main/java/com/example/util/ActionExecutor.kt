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
    data class OpenDynamicApp(val appQuery: String) : ParsedAction()
    data class MakeCall(val target: String) : ParsedAction()
    data class Chat(val prompt: String) : ParsedAction()
}

enum class AppType {
    DIALER,
    CAMERA,
    SETTINGS,
    WHATSAPP
}

data class InstalledAppInfo(
    val packageName: String,
    val appLabel: String
)

data class ActionResult(
    val spokenResponse: String,
    val success: Boolean,
    val actionType: String
)

object ActionExecutor {

    /**
     * Parses spoken input into structured actions:
     * - Dynamic app launches for ANY installed app (Spotify, YouTube, WhatsApp, Instagram, etc.)
     * - YouTube song play/search commands
     * - Phone call commands
     * - Questions / conversational queries routed to Gemini AI Brain
     */
    fun parseCommand(input: String): ParsedAction {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ParsedAction.Chat("")

        val lower = trimmed.lowercase()

        // 1. Check if input is a conversational question or casual banter
        // (Must NOT trigger app launching for questions like "What is Spotify?")
        if (isQuestionOrChat(lower)) {
            return ParsedAction.Chat(trimmed)
        }

        // 2. YouTube Play Search Commands (e.g., "Play Kesariya on YouTube", "YouTube pe song chalao")
        val hasYouTubeWord = lower.contains("youtube") ||
                lower.contains("यूट्यूब") ||
                lower.contains("युटुब") ||
                lower.contains("yt")

        if (hasYouTubeWord) {
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

            // If it's simply "open youtube", "youtube kholo", "यूट्यूब खोलो"
            val isOpenYouTube = lower.contains("open") ||
                    lower.contains("kholo") ||
                    lower.contains("खोलो") ||
                    lower.contains("launch") ||
                    lower == "youtube" ||
                    lower == "यूट्यूब"

            if (isOpenYouTube) {
                return ParsedAction.OpenYouTube
            }
        }

        // 3. Known System Apps for convenience & specific backwards compatibility
        if (isCameraCommand(lower)) {
            return ParsedAction.OpenApp(AppType.CAMERA)
        }
        if (isDialerCommand(lower)) {
            return ParsedAction.OpenApp(AppType.DIALER)
        }
        if (isSettingsCommand(lower)) {
            return ParsedAction.OpenApp(AppType.SETTINGS)
        }
        if (isWhatsAppCommand(lower)) {
            return ParsedAction.OpenApp(AppType.WHATSAPP)
        }

        // 4. Phone call commands (e.g. "Call Mom", "राहुल को कॉल करो")
        if (isCallCommand(lower)) {
            val target = extractCallTarget(trimmed, lower)
            if (target.isNotBlank()) {
                return ParsedAction.MakeCall(target)
            }
        }

        // 5. Dynamic App Launcher for ANY Installed App (e.g., "Open Spotify", "Spotify kholo", "Open Calculator")
        val dynamicAppQuery = extractDynamicAppQuery(trimmed, lower)
        if (!dynamicAppQuery.isNullOrBlank()) {
            // Check if user specifically requested YouTube
            if (dynamicAppQuery.equals("youtube", ignoreCase = true) || dynamicAppQuery.contains("यूट्यूब")) {
                return ParsedAction.OpenYouTube
            }
            return ParsedAction.OpenDynamicApp(dynamicAppQuery)
        }

        // 6. Non-launcher queries (general questions, banter, jokes) -> route to Gemini Conversational AI
        return ParsedAction.Chat(trimmed)
    }

    /**
     * Identifies questions or conversational prompts that must route to Gemini AI
     */
    fun isQuestionOrChat(lower: String): Boolean {
        val starters = listOf(
            "what", "who", "where", "when", "why", "how", "which", "whose", "whom",
            "tell me", "explain", "describe", "can you", "could you", "will you",
            "kya", "kaun", "kahan", "kaise", "kyun", "kitna", "kitne", "kab",
            "batao", "bataiye", "samjhao", "namaste", "hello", "hi", "hey",
            "joke", "chutkula", "time", "samay", "date", "tarikh", "mausam", "weather"
        )
        for (starter in starters) {
            if (lower.startsWith("$starter ") || lower == starter) {
                return true
            }
        }
        return lower.endsWith("?") || lower.contains("kya hai") || lower.contains("kaun hai")
    }

    /**
     * Extracts dynamic app name from spoken phrases:
     * "Open [App Name]", "Launch [App Name]", "[App Name] kholo", "[App Name] open karo", etc.
     */
    fun extractDynamicAppQuery(original: String, lower: String): String? {
        // "open spotify", "launch instagram", "start calculator"
        val prefixRegex = Regex("""^(?:open|launch|start|run)\s+([a-zA-Z0-9\s._-]+)$""", RegexOption.IGNORE_CASE)
        prefixRegex.find(original.trim())?.let { match ->
            val app = match.groupValues[1].trim()
            if (app.isNotBlank() && !isSystemNoiseWord(app.lowercase())) {
                return app
            }
        }

        // "spotify kholo", "whatsapp open karo", "instagram khol do"
        val suffixRegex = Regex("""^(.+?)\s+(?:kholo|khol\s+do|खोलो|खोल\s+दो|open\s+karo|open\s+kardo|ओपन\s+करो|chalu\s+karo|चालू\s+करो)$""", RegexOption.IGNORE_CASE)
        suffixRegex.find(original.trim())?.let { match ->
            val app = match.groupValues[1].trim()
            if (app.isNotBlank() && !isSystemNoiseWord(app.lowercase())) {
                return app
            }
        }

        return null
    }

    private fun isSystemNoiseWord(word: String): Boolean {
        return word in listOf("the", "app", "application", "a", "an", "kuch", "something")
    }

    private fun extractSongQuery(original: String, lower: String): String {
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
        val cleaned = lower
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

        return cleanSongQuery(cleaned)
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
                lower == "open camera" || lower == "कैमरा खोलो" || lower == "take photo" || lower == "फोटो खींचो"
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
     * Dynamically searches all installed packages on the device matching the query.
     */
    fun findInstalledApp(context: Context, appNameQuery: String): InstalledAppInfo? {
        val pm = context.packageManager
        val query = appNameQuery.trim().lowercase()
        if (query.isBlank()) return null

        // System shortcut targets
        if (query == "camera" || query == "कैमरा") {
            return InstalledAppInfo("system.camera", "Camera")
        }
        if (query == "dialer" || query == "phone" || query == "फोन" || query == "डायलर") {
            return InstalledAppInfo("system.dialer", "Phone")
        }
        if (query == "settings" || query == "setting" || query == "सेटिंग" || query == "सेटिंग्स") {
            return InstalledAppInfo("system.settings", "Settings")
        }

        // Query all launchable apps
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

        // 1. Exact label match (e.g. "Spotify", "YouTube")
        for (info in resolveInfos) {
            val label = info.loadLabel(pm).toString()
            if (label.equals(query, ignoreCase = true)) {
                return InstalledAppInfo(info.activityInfo.packageName, label)
            }
        }

        // 2. Starts with query
        for (info in resolveInfos) {
            val label = info.loadLabel(pm).toString()
            if (label.lowercase().startsWith(query)) {
                return InstalledAppInfo(info.activityInfo.packageName, label)
            }
        }

        // 3. Label contains query
        for (info in resolveInfos) {
            val label = info.loadLabel(pm).toString()
            if (label.lowercase().contains(query)) {
                return InstalledAppInfo(info.activityInfo.packageName, label)
            }
        }

        // 4. Package name contains query
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName.lowercase()
            val label = info.loadLabel(pm).toString()
            if (pkg.contains(query)) {
                return InstalledAppInfo(info.activityInfo.packageName, label)
            }
        }

        return null
    }

    /**
     * Executes the parsed command and produces a spoken confirmation in Hindi or English.
     */
    fun executeAction(context: Context, action: ParsedAction, isHindi: Boolean): ActionResult {
        return when (action) {
            is ParsedAction.OpenYouTube -> executeOpenYouTube(context, isHindi)
            is ParsedAction.PlayYouTube -> executePlayYouTube(context, action.songQuery, isHindi)
            is ParsedAction.OpenApp -> executeOpenApp(context, action.appType, isHindi)
            is ParsedAction.OpenDynamicApp -> executeOpenDynamicApp(context, action.appQuery, isHindi)
            is ParsedAction.MakeCall -> executeMakeCall(context, action.target, isHindi)
            is ParsedAction.Chat -> ActionResult("", false, "CHAT")
        }
    }

    /**
     * Dynamic App Launcher implementation: launches any installed app by matching app label.
     */
    fun executeOpenDynamicApp(context: Context, appQuery: String, isHindi: Boolean): ActionResult {
        val appInfo = findInstalledApp(context, appQuery)
        if (appInfo == null) {
            val notFoundMsg = if (isHindi) {
                "$appQuery ऐप इस डिवाइस पर नहीं मिला"
            } else {
                "$appQuery is not installed on this device"
            }
            return ActionResult(notFoundMsg, false, "OPEN_DYNAMIC_APP_NOT_FOUND")
        }

        return try {
            val pm = context.packageManager
            when (appInfo.packageName) {
                "system.camera" -> {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
                "system.dialer" -> {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
                "system.settings" -> {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
                else -> {
                    val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                    } else {
                        val notFoundMsg = if (isHindi) "${appInfo.appLabel} नहीं खुल पाया" else "Unable to launch ${appInfo.appLabel}"
                        return ActionResult(notFoundMsg, false, "OPEN_DYNAMIC_APP_ERROR")
                    }
                }
            }

            val successMsg = if (isHindi) "${appInfo.appLabel} खोल रहा हूँ" else "Opening ${appInfo.appLabel}"
            ActionResult(successMsg, true, "OPEN_DYNAMIC_APP")
        } catch (e: Exception) {
            val errorMsg = if (isHindi) "${appInfo.appLabel} नहीं खुल पाया" else "Unable to open ${appInfo.appLabel}"
            ActionResult(errorMsg, false, "OPEN_DYNAMIC_APP_ERROR")
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

            val msg = if (isHindi) "यूट्यूब पर $queryToSearch चला रहा हूँ" else "Playing $queryToSearch on YouTube"
            ActionResult(msg, true, "PLAY_YOUTUBE")
        } catch (e: Exception) {
            val msg = if (isHindi) "गाना नहीं चला पाया" else "Could not play song on YouTube"
            ActionResult(msg, false, "PLAY_YOUTUBE")
        }
    }

    private fun executeOpenApp(context: Context, appType: AppType, isHindi: Boolean): ActionResult {
        return try {
            val intent = when (appType) {
                AppType.CAMERA -> Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                AppType.DIALER -> Intent(Intent.ACTION_DIAL).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                AppType.SETTINGS -> Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                AppType.WHATSAPP -> {
                    val pm = context.packageManager
                    val whatsappIntent = pm.getLaunchIntentForPackage("com.whatsapp")
                        ?: pm.getLaunchIntentForPackage("com.whatsapp.w4b")
                    if (whatsappIntent != null) {
                        whatsappIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        whatsappIntent
                    } else {
                        val notFoundMsg = if (isHindi) "व्हाट्सएप इस डिवाइस पर नहीं मिला" else "WhatsApp is not installed on this device"
                        return ActionResult(notFoundMsg, false, "OPEN_APP_NOT_INSTALLED")
                    }
                }
            }

            context.startActivity(intent)

            val appName = when (appType) {
                AppType.CAMERA -> if (isHindi) "कैमरा" else "Camera"
                AppType.DIALER -> if (isHindi) "डायलर" else "Phone dialer"
                AppType.SETTINGS -> if (isHindi) "सेटिंग्स" else "Settings"
                AppType.WHATSAPP -> "WhatsApp"
            }

            val msg = if (isHindi) "$appName खोल रहा हूँ" else "Opening $appName"
            ActionResult(msg, true, "OPEN_APP")
        } catch (e: Exception) {
            val msg = if (isHindi) "ऐप नहीं खुल पाया" else "Unable to open application"
            ActionResult(msg, false, "OPEN_APP")
        }
    }

    private fun executeMakeCall(context: Context, target: String, isHindi: Boolean): ActionResult {
        return try {
            val hasCallPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            val isPhoneNumber = target.all { it.isDigit() || it == '+' || it == ' ' || it == '-' }

            if (isPhoneNumber && hasCallPermission) {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${target.trim()}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                val msg = if (isHindi) "$target को कॉल कर रहा हूँ" else "Calling $target"
                ActionResult(msg, true, "CALL_PHONE")
            } else {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$target")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                val msg = if (isHindi) "$target के लिए डायलर खोल रहा हूँ" else "Opening dialer for $target"
                ActionResult(msg, true, "OPEN_DIALER")
            }
        } catch (e: Exception) {
            val msg = if (isHindi) "कॉल नहीं लग पाया" else "Unable to place call"
            ActionResult(msg, false, "CALL_PHONE")
        }
    }
}
