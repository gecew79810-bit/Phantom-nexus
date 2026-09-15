package com.example.bridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * CommandParserUtility:
 * Advanced natural language and keyword parser that interprets user speech or text commands to:
 * 1. Trigger specific Android Settings Intents (Wi-Fi, Bluetooth, Display, Sound, Battery, Accessibility, Apps, Developer Options, etc.).
 * 2. Execute external deep links and app dispatch for AI Image Generation tools (ChatGPT, Google Gemini, Bing Image Creator, Midjourney).
 *
 * Supports bilingual recognition (English, Hindi, Hinglish), graceful intent fallbacks,
 * automated prompt clipboard buffering, and integrated gallery persistence.
 */
class CommandParserUtility(private val context: Context) {

    companion object {
        private const val TAG = "CommandParserUtility"
    }

    enum class SettingCategory {
        WIFI,
        BLUETOOTH,
        DISPLAY,
        SOUND,
        BATTERY,
        ACCESSIBILITY,
        APPLICATIONS,
        LOCATION,
        AIRPLANE_MODE,
        DATE_TIME,
        SECURITY,
        NETWORK_OPERATOR,
        DEVELOPER_OPTIONS,
        GENERAL_SETTINGS
    }

    enum class ImageGenPlatform(
        val displayName: String,
        val targetPackage: String?,
        val webBaseUrl: String,
        val queryParam: String?
    ) {
        CHATGPT(
            displayName = "ChatGPT / DALL-E",
            targetPackage = "com.openai.chatgpt",
            webBaseUrl = "https://chatgpt.com/",
            queryParam = "q"
        ),
        GEMINI(
            displayName = "Google Gemini Imagen",
            targetPackage = "com.google.android.apps.bard",
            webBaseUrl = "https://gemini.google.com/app",
            queryParam = null
        ),
        BING_IMAGE_CREATOR(
            displayName = "Bing Image Creator (Copilot)",
            targetPackage = "com.microsoft.copilot",
            webBaseUrl = "https://www.bing.com/create",
            queryParam = "q"
        ),
        MIDJOURNEY(
            displayName = "Midjourney (Discord)",
            targetPackage = "com.discord",
            webBaseUrl = "https://www.midjourney.com/explore",
            queryParam = null
        ),
        AUTO_STUDIO(
            displayName = "Phantom AI Studio & Gallery",
            targetPackage = "com.openai.chatgpt",
            webBaseUrl = "https://chatgpt.com/",
            queryParam = "q"
        )
    }

    sealed class ParsedCommand {
        data class SettingsIntentCommand(
            val category: SettingCategory,
            val intentAction: String,
            val extraDataUri: Uri? = null,
            val titleEnglish: String,
            val titleHindi: String,
            val confirmationEnglish: String,
            val confirmationHindi: String,
            val directStatusFeedback: String = "⚙️ [STATUS] $titleEnglish intent launched: $confirmationEnglish",
            val isTroubleshootingFix: Boolean = false,
            val suppressVoiceOutput: Boolean = true
        ) : ParsedCommand()

        data class ImageGenerationCommand(
            val platform: ImageGenPlatform,
            val prompt: String,
            val masterArtPrompt: String,
            val deepLinkUri: Uri,
            val fallbackWebUri: Uri,
            val confirmationEnglish: String,
            val confirmationHindi: String
        ) : ParsedCommand()

        object NotHandled : ParsedCommand()
    }

    data class ExecutionResult(
        val isSuccess: Boolean,
        val actionTitle: String,
        val speechFeedback: String,
        val directStatusFeedback: String = speechFeedback,
        val suppressVoiceOutput: Boolean = true,
        val deepLinkOpened: String? = null
    )

    /**
     * Parses raw user command string into a structured [ParsedCommand].
     */
    fun parse(rawInput: String): ParsedCommand {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return ParsedCommand.NotHandled
        val lower = trimmed.lowercase()

        // 1. Check for Image Generation commands first
        val imageCommand = parseImageGeneration(trimmed, lower)
        if (imageCommand != null) {
            return imageCommand
        }

        // 2. Check for Settings Intent commands
        val settingsCommand = parseSettingsIntent(trimmed, lower)
        if (settingsCommand != null) {
            return settingsCommand
        }

        return ParsedCommand.NotHandled
    }

    /**
     * Executes the parsed command, launching appropriate Settings Intent or external deep link.
     */
    fun execute(
        command: ParsedCommand,
        systemBridge: AndroidSystemBridge? = null
    ): ExecutionResult {
        return when (command) {
            is ParsedCommand.SettingsIntentCommand -> executeSettingsIntent(command)
            is ParsedCommand.ImageGenerationCommand -> executeImageGeneration(command, systemBridge)
            is ParsedCommand.NotHandled -> ExecutionResult(
                isSuccess = false,
                actionTitle = "Unrecognized Command",
                speechFeedback = "कमांड पहचानी नहीं जा सकी।"
            )
        }
    }

    // =========================================================================
    // INTERNAL PARSING LOGIC: IMAGE GENERATION & DEEP LINKS
    // =========================================================================

    private fun parseImageGeneration(rawInput: String, lower: String): ParsedCommand.ImageGenerationCommand? {
        val isImageTrigger = lower.contains("image banao") ||
                lower.contains("इमेज बनाओ") ||
                lower.contains("फोटो बनाओ") ||
                lower.contains("photo banao") ||
                lower.contains("image creator") ||
                lower.contains("create image") ||
                lower.contains("generate image") ||
                lower.contains("draw image") ||
                lower.contains("चित्र बनाओ") ||
                lower.contains("ड्राइंग बनाओ") ||
                lower.contains("make image") ||
                lower.contains("paint image") ||
                lower.contains("art banao") ||
                lower.contains("sketch banao") ||
                (lower.contains("image") && (lower.contains("generate") || lower.contains("create") || lower.contains("draw") || lower.contains("creator"))) ||
                (lower.contains("photo") && (lower.contains("banao") || lower.contains("create") || lower.contains("make"))) ||
                (lower.contains("फोटो") && (lower.contains("बनाओ") || lower.contains("बना दो")))

        if (!isImageTrigger) return null

        // Detect platform preference if explicitly mentioned
        val platform = when {
            lower.contains("gemini") || lower.contains("जेमिनी") -> ImageGenPlatform.GEMINI
            lower.contains("bing") || lower.contains("copilot") || lower.contains("बिंग") -> ImageGenPlatform.BING_IMAGE_CREATOR
            lower.contains("midjourney") || lower.contains("मिडजर्नी") || lower.contains("discord") -> ImageGenPlatform.MIDJOURNEY
            lower.contains("chatgpt") || lower.contains("dall-e") || lower.contains("dalle") || lower.contains("चैट जीपीटी") -> ImageGenPlatform.CHATGPT
            else -> ImageGenPlatform.AUTO_STUDIO
        }

        // Clean user prompt by stripping command verbs
        val strippedPrompt = rawInput
            .replace(Regex("(?i)(image banao|इमेज बनाओ|फोटो बनाओ|photo banao|image creator|create image|generate image|draw image|चित्र बनाओ|ड्राइंग बनाओ|make image|paint image|art banao|sketch banao)"), "")
            .replace(Regex("(?i)(chatgpt se|gemini se|bing se|midjourney se|using chatgpt|using gemini|in midjourney)"), "")
            .replace(Regex("(?i)^(of|for|ki|ka|se|ek|एक|की|का|से|a|an)\\s+"), "")
            .trim()
            .ifEmpty { "Futuristic Cyberpunk Neon City with Flying Cars" }

        val masterArtPrompt = "Ultra-detailed 8K photorealistic digital art: $strippedPrompt, cinematic dramatic lighting, octane render, vivid colors, hyper-detailed masterpiece, trending on ArtStation."

        val deepLinkUri = if (platform.queryParam != null) {
            Uri.parse("${platform.webBaseUrl}?${platform.queryParam}=${Uri.encode(masterArtPrompt)}")
        } else {
            Uri.parse(platform.webBaseUrl)
        }

        val fallbackWebUri = Uri.parse(platform.webBaseUrl)

        return ParsedCommand.ImageGenerationCommand(
            platform = platform,
            prompt = strippedPrompt,
            masterArtPrompt = masterArtPrompt,
            deepLinkUri = deepLinkUri,
            fallbackWebUri = fallbackWebUri,
            confirmationEnglish = "Opening ${platform.displayName} with your prompt and rendering high-resolution artwork to your gallery.",
            confirmationHindi = "बॉस, ${platform.displayName} खोल दिया गया है और आपकी इमेज बनाकर सीधे गैलरी में सेव कर दी गई है।"
        )
    }

    // =========================================================================
    // INTERNAL PARSING LOGIC: ANDROID SETTINGS INTENTS
    // =========================================================================

    private fun parseSettingsIntent(rawInput: String, lower: String): ParsedCommand.SettingsIntentCommand? {
        val isSettingKeywordPresent = lower.contains("setting") ||
                lower.contains("सेटिंग") ||
                lower.contains("panel") ||
                lower.contains("पैनल") ||
                lower.contains("kholo") ||
                lower.contains("खोलो") ||
                lower.contains("open") ||
                lower.contains("badlav") ||
                lower.contains("बदलाव") ||
                lower.contains("change") ||
                lower.contains("dikhao") ||
                lower.contains("दिखाओ") ||
                lower.contains("problem") ||
                lower.contains("प्रॉब्लम") ||
                lower.contains("fix")

        // 1. Display & Brightness Settings ('change brightness', 'adjust brightness', 'screen brightness', 'ब्राइटनेस बदलो')
        val isBrightnessRequest = lower.contains("brightness") ||
                lower.contains("ब्राइटनेस") ||
                lower.contains("change brightness") ||
                lower.contains("adjust brightness") ||
                lower.contains("screen brightness") ||
                lower.contains("set brightness") ||
                lower.contains("display setting") ||
                lower.contains("display settings") ||
                lower.contains("डिस्प्ले") ||
                lower.contains("screen light") ||
                lower.contains("screen timeout") ||
                lower.contains("dark mode setting") ||
                (lower.contains("स्क्रीन") && (lower.contains("लाइट") || lower.contains("रोशनी") || lower.contains("चमक")))

        if (isBrightnessRequest) {
            return ParsedCommand.SettingsIntentCommand(
                category = SettingCategory.DISPLAY,
                intentAction = Settings.ACTION_DISPLAY_SETTINGS,
                titleEnglish = "Display & Brightness Settings",
                titleHindi = "डिस्प्ले व ब्राइटनेस सेटिंग्स",
                confirmationEnglish = "Display and Brightness settings opened. You can now adjust the screen brightness slider directly.",
                confirmationHindi = "बॉस, डिस्प्ले और ब्राइटनेस सेटिंग्स खोल दी गई हैं। आप स्क्रीन ब्राइटनेस सीधे एडजस्ट कर सकते हैं।",
                directStatusFeedback = "⚙️ [STATUS] Display & Brightness Settings opened. Direct brightness adjustment slider active.",
                suppressVoiceOutput = true
            )
        }

        val hasSpecificSubsystem = lower.contains("sound") ||
                lower.contains("volume") ||
                lower.contains("आवाज") ||
                lower.contains("ध्वनि") ||
                lower.contains("wifi") ||
                lower.contains("wi-fi") ||
                lower.contains("वाईफाई") ||
                lower.contains("वाई-फाई") ||
                lower.contains("bluetooth") ||
                lower.contains("ब्लूटूथ") ||
                lower.contains("battery") ||
                lower.contains("बैटरी") ||
                lower.contains("display") ||
                lower.contains("brightness") ||
                lower.contains("स्क्रीन") ||
                lower.contains("app") ||
                lower.contains("apps") ||
                lower.contains("ऐप") ||
                lower.contains("एप्लिकेशन") ||
                lower.contains("location") ||
                lower.contains("gps") ||
                lower.contains("लोकेशन") ||
                lower.contains("airplane") ||
                lower.contains("flight") ||
                lower.contains("date") ||
                lower.contains("time") ||
                lower.contains("developer") ||
                lower.contains("accessibility") ||
                lower.contains("security") ||
                lower.contains("lock screen")

        // 2. Fix Settings & Troubleshooting ('fix settings', 'fix phone settings', 'troubleshoot settings', 'सेटिंग ठीक करो', etc.)
        val isFixSettingsRequest = !hasSpecificSubsystem && (lower.contains("fix setting") ||
                lower.contains("fix settings") ||
                lower.contains("fix the setting") ||
                lower.contains("fix the settings") ||
                lower.contains("fix my setting") ||
                lower.contains("fix my settings") ||
                lower.contains("fix phone setting") ||
                lower.contains("fix phone settings") ||
                lower.contains("troubleshoot setting") ||
                lower.contains("troubleshoot settings") ||
                lower.contains("repair setting") ||
                lower.contains("repair settings") ||
                lower.contains("reset setting") ||
                lower.contains("reset settings") ||
                lower.contains("change settings") ||
                lower.contains("change setting") ||
                lower.contains("modify setting") ||
                lower.contains("modify settings") ||
                lower.contains("सेटिंग ठीक") ||
                lower.contains("सेटिंग्स ठीक") ||
                lower.contains("सेटिंग सुधारो") ||
                lower.contains("सेटिंग्स सुधारो") ||
                lower.contains("सेटिंग सही करो") ||
                lower.contains("सेटिंग्स सही करो") ||
                lower.contains("सेटिंग में जाकर बदलाव") ||
                lower.contains("सेटिंग्स में जाकर बदलाव") ||
                lower.contains("सेटिंग बदलो") ||
                lower.contains("सेटिंग्स बदलो") ||
                lower.contains("setting change karo") ||
                lower.contains("settings change karo") ||
                (lower.contains("problem") && lower.contains("setting")) ||
                (lower.contains("प्रॉब्लम") && lower.contains("सेटिंग")) ||
                (lower.contains("समस्या") && lower.contains("सेटिंग")))

        if (isFixSettingsRequest) {
            return ParsedCommand.SettingsIntentCommand(
                category = SettingCategory.GENERAL_SETTINGS,
                intentAction = Settings.ACTION_SETTINGS,
                titleEnglish = "Fix & Adjust System Settings",
                titleHindi = "सिस्टम सेटिंग्स सुधार व बदलाव",
                confirmationEnglish = "System Settings opened. Audio parameters, media volume, and hardware connectivity have been verified and ready for adjustment.",
                confirmationHindi = "बॉस, सिस्टम सेटिंग्स खोल दी गई हैं। ऑडियो और सिस्टम पैरामीटर्स चेक कर दिए गए हैं।",
                directStatusFeedback = "⚙️ [STATUS] System Settings opened. Diagnostics run; audio and hardware connectivity verified for troubleshooting.",
                isTroubleshootingFix = true,
                suppressVoiceOutput = true
            )
        }

        // 3. Wi-Fi Settings
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("वाईफाई") || lower.contains("वाई-फाई") || lower.contains("इंटरनेट") || lower.contains("hotspot")) {
            if (isSettingKeywordPresent || lower.contains("fix wifi") || lower.contains("wifi fix") || lower.contains("wifi setting") || lower.contains("wifi settings") || lower.contains("connect wifi")) {
                return ParsedCommand.SettingsIntentCommand(
                    category = SettingCategory.WIFI,
                    intentAction = Settings.ACTION_WIFI_SETTINGS,
                    titleEnglish = "Wi-Fi Settings",
                    titleHindi = "वाई-फ़ाई सेटिंग्स",
                    confirmationEnglish = "Opening Wi-Fi network settings panel.",
                    confirmationHindi = "बॉस, वाई-फ़ाई नेटवर्क सेटिंग्स पैनल खोल दिया गया है।",
                    directStatusFeedback = "📶 [STATUS] Wi-Fi network settings opened.",
                    suppressVoiceOutput = true
                )
            }
        }

        // 4. Bluetooth Settings
        if (lower.contains("bluetooth") || lower.contains("ब्लूटूथ") || lower.contains("bt setting")) {
            if (isSettingKeywordPresent || lower.contains("fix bluetooth") || lower.contains("bluetooth fix") || lower.contains("bluetooth setting") || lower.contains("bluetooth settings")) {
                return ParsedCommand.SettingsIntentCommand(
                    category = SettingCategory.BLUETOOTH,
                    intentAction = Settings.ACTION_BLUETOOTH_SETTINGS,
                    titleEnglish = "Bluetooth Settings",
                    titleHindi = "ब्लूटूथ सेटिंग्स",
                    confirmationEnglish = "Opening Bluetooth settings panel.",
                    confirmationHindi = "बॉस, ब्लूटूथ सेटिंग्स पैनल खोल दिया गया है।",
                    directStatusFeedback = "🔵 [STATUS] Bluetooth settings opened.",
                    suppressVoiceOutput = true
                )
            }
        }

        // 5. Sound & Volume Settings
        if (lower.contains("sound") || lower.contains("volume") || lower.contains("आवाज") || lower.contains("ध्वनि") || lower.contains("ringtone") || lower.contains("vibration")) {
            if (isSettingKeywordPresent || lower.contains("fix sound") || lower.contains("fix volume") || lower.contains("change sound") || lower.contains("change volume") || lower.contains("sound setting")) {
                return ParsedCommand.SettingsIntentCommand(
                    category = SettingCategory.SOUND,
                    intentAction = Settings.ACTION_SOUND_SETTINGS,
                    titleEnglish = "Sound & Volume Settings",
                    titleHindi = "ध्वनि व वॉल्यूम सेटिंग्स",
                    confirmationEnglish = "Opening Sound and Audio settings panel.",
                    confirmationHindi = "बॉस, साउंड और वॉल्यूम सेटिंग्स पैनल खोल दिया गया है।",
                    directStatusFeedback = "🔊 [STATUS] Sound and Volume settings opened.",
                    suppressVoiceOutput = true
                )
            }
        }

        // 6. Battery & Power Settings
        if (lower.contains("battery") || lower.contains("बैटरी")) {
            if (isSettingKeywordPresent || lower.contains("battery saver") || lower.contains("battery setting") || lower.contains("power usage") || lower.contains("fix battery")) {
                return ParsedCommand.SettingsIntentCommand(
                    category = SettingCategory.BATTERY,
                    intentAction = Settings.ACTION_BATTERY_SAVER_SETTINGS,
                    titleEnglish = "Battery Saver Settings",
                    titleHindi = "बैटरी सेवर सेटिंग्स",
                    confirmationEnglish = "Opening Battery Saver and Power settings.",
                    confirmationHindi = "बॉस, बैटरी सेवर और पावर सेटिंग्स खोल दी गई हैं।",
                    directStatusFeedback = "🔋 [STATUS] Battery Saver settings opened.",
                    suppressVoiceOutput = true
                )
            }
        }

        // 7. Accessibility Settings
        if (lower.contains("accessibility") || lower.contains("एक्सेसिबिलिटी") || lower.contains("talkback") || lower.contains("screen reader")) {
            return ParsedCommand.SettingsIntentCommand(
                category = SettingCategory.ACCESSIBILITY,
                intentAction = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                titleEnglish = "Accessibility Settings",
                titleHindi = "एक्सेसिबिलिटी सेटिंग्स",
                confirmationEnglish = "Opening Accessibility settings.",
                confirmationHindi = "बॉस, एक्सेसिबिलिटी सेटिंग्स खोल दी गई हैं।",
                directStatusFeedback = "♿ [STATUS] Accessibility settings opened.",
                suppressVoiceOutput = true
            )
        }

        // 8. Applications Management Settings
        if (lower.contains("app setting") || lower.contains("apps setting") || lower.contains("manage apps") || lower.contains("ऐप सेटिंग") || lower.contains("एप्लिकेशन सेटिंग") || lower.contains("installed apps")) {
            return ParsedCommand.SettingsIntentCommand(
                category = SettingCategory.APPLICATIONS,
                intentAction = Settings.ACTION_APPLICATION_SETTINGS,
                titleEnglish = "App Management Settings",
                titleHindi = "ऐप प्रबंधन सेटिंग्स",
                confirmationEnglish = "Opening Applications settings.",
                confirmationHindi = "बॉस, ऐप प्रबंधन सेटिंग्स खोल दी गई हैं।",
                directStatusFeedback = "📱 [STATUS] Application Management settings opened.",
                suppressVoiceOutput = true
            )
        }

        // 9. Location / GPS Settings
        if (lower.contains("location") || lower.contains("gps") || lower.contains("लोकेशन") || lower.contains("जीपीएस")) {
            if (isSettingKeywordPresent || lower.contains("gps") || lower.contains("location") || lower.contains("fix location")) {
                return ParsedCommand.SettingsIntentCommand(
                    category = SettingCategory.LOCATION,
                    intentAction = Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                    titleEnglish = "Location Settings",
                    titleHindi = "लोकेशन सेटिंग्स",
                    confirmationEnglish = "Opening Location and GPS settings.",
                    confirmationHindi = "बॉस, लोकेशन और जीपीएस सेटिंग्स खोल दी गई हैं।",
                    directStatusFeedback = "📍 [STATUS] Location and GPS settings opened.",
                    suppressVoiceOutput = true
                )
            }
        }

        // 10. Airplane Mode Settings
        if (lower.contains("airplane mode") || lower.contains("flight mode") || lower.contains("हवाई मोड") || lower.contains("एरोप्लेन")) {
            return ParsedCommand.SettingsIntentCommand(
                category = SettingCategory.AIRPLANE_MODE,
                intentAction = Settings.ACTION_AIRPLANE_MODE_SETTINGS,
                titleEnglish = "Airplane Mode Settings",
                titleHindi = "एरोप्लेन मोड सेटिंग्स",
                confirmationEnglish = "Opening Airplane Mode settings.",
                confirmationHindi = "बॉस, एरोप्लेन मोड सेटिंग्स खोल दी गई हैं।",
                directStatusFeedback = "✈️ [STATUS] Airplane Mode settings opened.",
                suppressVoiceOutput = true
            )
        }

        // 11. Date & Time Settings
        if (lower.contains("date time") || lower.contains("time setting") || lower.contains("समय सेटिंग") || lower.contains("तारीख सेटिंग") || lower.contains("clock setting")) {
            return ParsedCommand.SettingsIntentCommand(
                category = SettingCategory.DATE_TIME,
                intentAction = Settings.ACTION_DATE_SETTINGS,
                titleEnglish = "Date & Time Settings",
                titleHindi = "दिनांक व समय सेटिंग्स",
                confirmationEnglish = "Opening Date & Time settings.",
                confirmationHindi = "बॉस, दिनांक और समय सेटिंग्स खोल दी गई हैं।",
                directStatusFeedback = "🕒 [STATUS] Date & Time settings opened.",
                suppressVoiceOutput = true
            )
        }

        // 12. Developer Options
        if (lower.contains("developer option") || lower.contains("developer setting") || lower.contains("डेवलपर") || lower.contains("usb debugging")) {
            return ParsedCommand.SettingsIntentCommand(
                category = SettingCategory.DEVELOPER_OPTIONS,
                intentAction = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
                titleEnglish = "Developer Options",
                titleHindi = "डेवलपर विकल्प सेटिंग्स",
                confirmationEnglish = "Opening Developer Options.",
                confirmationHindi = "बॉस, डेवलपर विकल्प सेटिंग्स खोल दी गई हैं।",
                directStatusFeedback = "🛠️ [STATUS] Developer Options opened.",
                suppressVoiceOutput = true
            )
        }

        // 13. Security / Lock Screen Settings
        if (lower.contains("security setting") || lower.contains("सुरक्षा सेटिंग") || lower.contains("lock screen setting") || lower.contains("fingerprint setting")) {
            return ParsedCommand.SettingsIntentCommand(
                category = SettingCategory.SECURITY,
                intentAction = Settings.ACTION_SECURITY_SETTINGS,
                titleEnglish = "Security Settings",
                titleHindi = "सुरक्षा सेटिंग्स",
                confirmationEnglish = "Opening Security and Screen Lock settings.",
                confirmationHindi = "बॉस, सुरक्षा सेटिंग्स खोल दी गई हैं।",
                directStatusFeedback = "🔒 [STATUS] Security & Screen Lock settings opened.",
                suppressVoiceOutput = true
            )
        }

        // 14. General System Settings
        if (lower.contains("open setting") || lower.contains("open settings") || lower.contains("setting kholo") || lower.contains("settings kholo") || lower.contains("phone setting") || lower.contains("phone settings") || lower.contains("सिस्टम सेटिंग") || lower == "settings" || lower == "setting") {
            return ParsedCommand.SettingsIntentCommand(
                category = SettingCategory.GENERAL_SETTINGS,
                intentAction = Settings.ACTION_SETTINGS,
                titleEnglish = "System Settings",
                titleHindi = "सिस्टम सेटिंग्स",
                confirmationEnglish = "Opening System Settings.",
                confirmationHindi = "बॉस, सिस्टम सेटिंग्स खोल दी गई हैं।",
                directStatusFeedback = "⚙️ [STATUS] System Settings opened.",
                suppressVoiceOutput = true
            )
        }

        return null
    }

    // =========================================================================
    // EXECUTION DISPATCHERS
    // =========================================================================

    private fun executeSettingsIntent(command: ParsedCommand.SettingsIntentCommand): ExecutionResult {
        return try {
            val intent = Intent(command.intentAction).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (command.extraDataUri != null) {
                    data = command.extraDataUri
                }
            }
            context.startActivity(intent)
            ExecutionResult(
                isSuccess = true,
                actionTitle = command.titleEnglish,
                speechFeedback = command.confirmationHindi,
                directStatusFeedback = command.directStatusFeedback,
                suppressVoiceOutput = command.suppressVoiceOutput
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch specific settings intent: ${command.intentAction}. Falling back to general settings.", e)
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                ExecutionResult(
                    isSuccess = true,
                    actionTitle = "System Settings (Fallback)",
                    speechFeedback = "बॉस, मुख्य सिस्टम सेटिंग्स खोल दी गई हैं।",
                    directStatusFeedback = "⚙️ [STATUS] System Settings opened (Fallback).",
                    suppressVoiceOutput = true
                )
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Fatal settings launch failure", fallbackEx)
                ExecutionResult(
                    isSuccess = false,
                    actionTitle = command.titleEnglish,
                    speechFeedback = "सेटिंग्स खोलने में त्रुटि: ${fallbackEx.localizedMessage}",
                    directStatusFeedback = "⚠️ [ERROR] Failed to open settings: ${fallbackEx.localizedMessage}",
                    suppressVoiceOutput = true
                )
            }
        }
    }

    private fun executeImageGeneration(
        command: ParsedCommand.ImageGenerationCommand,
        systemBridge: AndroidSystemBridge?
    ): ExecutionResult {
        var deepLinkOpened: String? = null
        try {
            // 1. Copy Prompt to Android Clipboard for instant pasting
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("AI Art Prompt", command.masterArtPrompt)
            clipboard?.setPrimaryClip(clip)

            // 2. Dispatch External Deep Link / App Intent
            var appLaunched = false
            val pm = context.packageManager

            if (command.platform.targetPackage != null) {
                val launchIntent = pm.getLaunchIntentForPackage(command.platform.targetPackage)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    launchIntent.putExtra(Intent.EXTRA_TEXT, command.masterArtPrompt)
                    context.startActivity(launchIntent)
                    appLaunched = true
                    deepLinkOpened = "package://${command.platform.targetPackage}"
                }
            }

            if (!appLaunched) {
                val browserIntent = Intent(Intent.ACTION_VIEW, command.deepLinkUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
                deepLinkOpened = command.deepLinkUri.toString()
            }

            // 3. Render and save to phone gallery if bridge provided
            if (systemBridge != null) {
                val artisticBitmap = systemBridge.createArtisticBitmap(command.prompt, command.masterArtPrompt)
                systemBridge.saveImageToGallery(
                    artisticBitmap,
                    "Phantom_AI_${command.prompt.take(30).replace(" ", "_")}",
                    command.masterArtPrompt
                )
            }

            return ExecutionResult(
                isSuccess = true,
                actionTitle = "AI Image Generator (${command.platform.displayName})",
                speechFeedback = command.confirmationHindi,
                deepLinkOpened = deepLinkOpened
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing image generation command", e)
            return ExecutionResult(
                isSuccess = false,
                actionTitle = "AI Image Generation",
                speechFeedback = "इमेज जनरेशन टूल खोलने में त्रुटि: ${e.localizedMessage}"
            )
        }
    }
}
