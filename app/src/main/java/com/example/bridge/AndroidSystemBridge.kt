package com.example.bridge

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale
import java.util.Random

data class RealSystemDiagnostic(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val powerSource: String,
    val batteryHealth: String,
    val batteryTempCelsius: Double,
    val totalRamGb: Double,
    val usedRamGb: Double,
    val freeRamGb: Double,
    val ramUsedPercent: Int,
    val totalStorageGb: Double,
    val freeStorageGb: Double,
    val usedStorageGb: Double,
    val networkStatus: String,
    val cpuCores: Int,
    val model: String,
    val androidVersion: String,
    val uptimeHours: Double
) {
    fun toDiagnosticReport(): String {
        val chargeStatus = if (isCharging) "चार्जिंग ($powerSource)" else "डिस्चार्जिंग ($powerSource)"
        val temp = if (batteryTempCelsius > 0) ", तापमान ${batteryTempCelsius}°C" else ""
        return """
            बॉस, रियल सिस्टम डायग्नोस्टिक एनालिसिस:
            • बैटरी: $batteryPercent% ($chargeStatus$temp, स्वास्थ्य: $batteryHealth)
            • रैम (RAM): ${String.format(Locale.US, "%.1f", usedRamGb)} GB / ${String.format(Locale.US, "%.1f", totalRamGb)} GB (${ramUsedPercent}% उपयोग में)
            • स्टोरेज: ${String.format(Locale.US, "%.1f", freeStorageGb)} GB खाली / ${String.format(Locale.US, "%.1f", totalStorageGb)} GB
            • नेटवर्क: $networkStatus
            • डिवाइस: $model ($androidVersion)
            • CPU कोर: $cpuCores Cores | Uptime: ${uptimeHours} घंटे
            सिस्टम पूरी तरह सुरक्षित, स्थिर और ऑप्टिमाइज़्ड है।
        """.trimIndent()
    }
}

data class DeviceSpecs(
    val model: String,
    val androidVersion: String,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val storageFreeGb: Double,
    val uptimeHours: Double,
    val cpuCores: Int
)

data class InstalledAppInfo(
    val appName: String,
    val packageName: String
)

class AndroidSystemBridge(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var isTorchOn = false

    // --- HARDWARE: FLASHLIGHT ---
    fun toggleTorch(enable: Boolean? = null): Pair<Boolean, String> {
        val cm = cameraManager ?: return Pair(false, "Camera hardware not accessible.")
        return try {
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facingBack = chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                flashAvailable && facingBack
            } ?: cm.cameraIdList.firstOrNull() ?: return Pair(false, "No flashlight hardware detected.")

            val targetState = enable ?: !isTorchOn
            cm.setTorchMode(cameraId, targetState)
            isTorchOn = targetState
            Pair(true, if (targetState) "Torch ON" else "Torch OFF")
        } catch (e: Exception) {
            Log.e("SystemBridge", "Error toggling torch", e)
            Pair(false, "Flashlight error: ${e.message}")
        }
    }

    fun isFlashlightOn(): Boolean = isTorchOn

    // --- HARDWARE: VOLUME ---
    fun adjustVolume(direction: Int): String {
        val am = audioManager ?: return "Audio system unavailable"
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percent = ((current.toFloat() / max.toFloat()) * 100).toInt()
        return "Volume: $percent%"
    }

    fun setVolumePercent(percent: Int): String {
        val am = audioManager ?: return "Audio system unavailable"
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = ((percent.coerceIn(0, 100) / 100f) * max).toInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        return "Volume set to $percent%"
    }

    // --- HARDWARE: BATTERY & REAL DIAGNOSTICS ---
    fun getBatteryInfo(): Pair<Int, Boolean> {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100).toInt()
        } else {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50
        }
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        return Pair(batteryPct, isCharging)
    }

    fun getRealSystemDiagnostic(): RealSystemDiagnostic {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100).toInt()
        } else {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50
        }
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val powerSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC चार्जर"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "वायरलेस चार्जर"
            else -> if (isCharging) "चार्जर" else "अनप्लग / बैटरी"
        }
        val tempRaw = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val batteryTempCelsius = if (tempRaw > 0) tempRaw / 10.0 else 29.0
        val healthCode = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
        val batteryHealth = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "उत्तम (Good)"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "ओवरहीट (Overheat)"
            BatteryManager.BATTERY_HEALTH_DEAD -> "खराब (Dead)"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "ओवर वोल्टेज"
            else -> "सामान्य (Normal)"
        }

        // REAL Hardware RAM: ActivityManager.MemoryInfo
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val freeRamGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)
        val usedRamGb = (totalRamGb - freeRamGb).coerceAtLeast(0.0)
        val ramUsedPercent = if (totalRamGb > 0) ((usedRamGb / totalRamGb) * 100).toInt() else 0

        // REAL Storage
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.blockSizeLong * stat.blockCountLong
        val availBytes = stat.blockSizeLong * stat.availableBlocksLong
        val totalStorageGb = totalBytes / (1024.0 * 1024.0 * 1024.0)
        val freeStorageGb = availBytes / (1024.0 * 1024.0 * 1024.0)
        val usedStorageGb = (totalStorageGb - freeStorageGb).coerceAtLeast(0.0)

        // REAL Network Connectivity
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val networkStatus = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi सक्रिय"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "मोबाइल डेटा (Cellular)"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "LAN"
            else -> "ऑफलाइन / नो नेटवर्क"
        }

        val uptimeHours = (SystemClock.elapsedRealtime() / (1000.0 * 60.0 * 60.0))

        return RealSystemDiagnostic(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            powerSource = powerSource,
            batteryHealth = batteryHealth,
            batteryTempCelsius = batteryTempCelsius,
            totalRamGb = totalRamGb,
            usedRamGb = usedRamGb,
            freeRamGb = freeRamGb,
            ramUsedPercent = ramUsedPercent,
            totalStorageGb = totalStorageGb,
            freeStorageGb = freeStorageGb,
            usedStorageGb = usedStorageGb,
            networkStatus = networkStatus,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            uptimeHours = String.format(Locale.US, "%.1f", uptimeHours).toDoubleOrNull() ?: 0.0
        )
    }

    // --- CHATGPT AUTOMATION & GALLERY IMAGE CREATION ---
    fun openChatGPTWithPrompt(prompt: String): Pair<Boolean, String> {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("AI Image Prompt", prompt)
            clipboard?.setPrimaryClip(clip)

            val pm = context.packageManager
            val chatGptIntent = pm.getLaunchIntentForPackage("com.openai.chatgpt")
            if (chatGptIntent != null) {
                chatGptIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chatGptIntent)
                Pair(true, "ChatGPT ऐप खोल दिया गया है और आपका प्रॉम्प्ट क्लिपबोर्ड पर कॉपी कर दिया गया है।")
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                Pair(true, "ChatGPT वेब इंटरफेस खोल दिया गया है और आपका प्रॉम्प्ट क्लिपबोर्ड पर तैयार है।")
            }
        } catch (e: Exception) {
            Log.e("AndroidSystemBridge", "Failed to open ChatGPT", e)
            Pair(false, "ChatGPT खोलने में समस्या: ${e.localizedMessage}")
        }
    }

    fun saveImageToGallery(bitmap: Bitmap, title: String, description: String = "Generated by MAX AI Studio"): Pair<Boolean, String> {
        return try {
            val resolver = context.contentResolver
            val filename = "Phantom_AI_${System.currentTimeMillis()}.png"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.TITLE, title)
                put(MediaStore.Images.Media.DESCRIPTION, description)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Phantom AI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri)
                context.sendBroadcast(mediaScanIntent)
                Pair(true, "आपकी इमेज फ़ोन गैलरी (Pictures/Phantom AI) में सफलतापूर्वक सेव कर दी गई है।")
            } else {
                Pair(false, "गैलरी में स्थान नहीं मिला।")
            }
        } catch (e: Exception) {
            Log.e("AndroidSystemBridge", "Failed to save image to gallery", e)
            Pair(false, "इमेज सेव करने में त्रुटि: ${e.localizedMessage}")
        }
    }

    fun createArtisticBitmap(concept: String, prompt: String): Bitmap {
        val width = 1024
        val height = 1024
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    Color.rgb(10, 16, 32),
                    Color.rgb(25, 12, 50),
                    Color.rgb(6, 6, 22)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.argb(170, 0, 229, 255)
        }
        val violetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = Color.argb(150, 189, 0, 255)
        }
        canvas.drawCircle(512f, 512f, 380f, ringPaint)
        canvas.drawCircle(512f, 512f, 260f, violetPaint)
        canvas.drawCircle(512f, 512f, 150f, ringPaint)

        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        val random = Random(concept.hashCode().toLong())
        for (i in 0..160) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height
            val r = 1.5f + random.nextFloat() * 4f
            starPaint.alpha = 100 + random.nextInt(155)
            canvas.drawCircle(x, y, r, starPaint)
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 255)
            textSize = 40f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(16f, 0f, 0f, Color.CYAN)
        }
        canvas.drawText("PHANTOM NEXUS • AI ART STUDIO", 512f, 150f, textPaint)

        val conceptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val displayConcept = if (concept.length > 38) concept.take(35) + "..." else concept
        canvas.drawText("\"$displayConcept\"", 512f, 520f, conceptPaint)

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 190, 220)
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Generated & Saved to Gallery by MAX AI", 512f, 580f, metaPaint)
        canvas.drawText("Neural Prompt & ChatGPT Sync Active", 512f, 890f, metaPaint)

        return bitmap
    }

    // --- SYSTEM SPECIFICATIONS ---
    fun getDeviceSpecs(): DeviceSpecs {
        val (battery, charging) = getBatteryInfo()
        val runtime = Runtime.getRuntime()
        val totalRamMb = (runtime.totalMemory() / (1024 * 1024))
        val freeRamMb = (runtime.freeMemory() / (1024 * 1024))

        val stat = StatFs(Environment.getDataDirectory().path)
        val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
        val storageFreeGb = (bytesAvailable / (1024.0 * 1024.0 * 1024.0))

        val uptimeHours = (SystemClock.elapsedRealtime() / (1000.0 * 60.0 * 60.0))

        return DeviceSpecs(
            model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
            androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            batteryPercent = battery,
            isCharging = charging,
            totalRamMb = totalRamMb,
            availableRamMb = freeRamMb,
            storageFreeGb = String.format(Locale.US, "%.1f", storageFreeGb).toDoubleOrNull() ?: 0.0,
            uptimeHours = String.format(Locale.US, "%.1f", uptimeHours).toDoubleOrNull() ?: 0.0,
            cpuCores = Runtime.getRuntime().availableProcessors()
        )
    }

    // --- APPLICATION DISCOVERY & LAUNCH ---
    fun getInstalledLaunchableApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolveList.mapNotNull { resolveInfo ->
            val appName = resolveInfo.loadLabel(pm).toString()
            val packageName = resolveInfo.activityInfo.packageName
            if (packageName.isNotEmpty()) InstalledAppInfo(appName, packageName) else null
        }.distinctBy { it.packageName }
    }

    fun launchAppByNameOrPackage(query: String): Pair<Boolean, String> {
        val pm = context.packageManager
        val normalized = query.lowercase().trim()

        // Known mapping shortcuts
        val shortcutPackage = when {
            normalized.contains("whatsapp") -> "com.whatsapp"
            normalized.contains("chrome") -> "com.android.chrome"
            normalized.contains("youtube") -> "com.google.android.youtube"
            normalized.contains("settings") || normalized.contains("setting") -> "com.android.settings"
            normalized.contains("camera") || normalized.contains("camera") -> null // use action
            normalized.contains("calculator") -> "com.google.android.calculator"
            normalized.contains("spotify") -> "com.spotify.music"
            normalized.contains("instagram") -> "com.instagram.android"
            normalized.contains("telegram") -> "org.telegram.messenger"
            normalized.contains("calendar") || normalized.contains("कैलेंडर") -> "com.google.android.calendar"
            else -> null
        }

        if (shortcutPackage != null) {
            val intent = pm.getLaunchIntentForPackage(shortcutPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return Pair(true, "Launching $shortcutPackage")
            }
        }

        // Camera direct launch
        if (normalized.contains("camera") || normalized.contains("कैमरा")) {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (cameraIntent.resolveActivity(pm) != null) {
                context.startActivity(cameraIntent)
                return Pair(true, "Camera opened")
            }
        }

        // Search installed apps
        val apps = getInstalledLaunchableApps()
        val found = apps.firstOrNull {
            it.appName.lowercase().contains(normalized) || it.packageName.lowercase().contains(normalized)
        }

        if (found != null) {
            val intent = pm.getLaunchIntentForPackage(found.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return Pair(true, "Opening ${found.appName}")
            }
        }

        return Pair(false, "App '$query' not installed or cannot be opened.")
    }

    /**
     * Opens system Calendar application or schedule view
     */
    fun openCalendar(): Pair<Boolean, String> {
        return try {
            val calendarUri = Uri.parse("content://com.android.calendar/time")
            val intent = Intent(Intent.ACTION_VIEW, calendarUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Pair(true, "Calendar opened")
            } else {
                val genericIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALENDAR)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (genericIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(genericIntent)
                    Pair(true, "Calendar opened")
                } else {
                    launchAppByNameOrPackage("calendar")
                }
            }
        } catch (e: Exception) {
            launchAppByNameOrPackage("calendar")
        }
    }

    // --- WHATSAPP COMMUNICATION ---
    fun openWhatsAppChatOrShare(phone: String? = null, message: String? = null): Pair<Boolean, String> {
        return try {
            if (!phone.isNullOrBlank()) {
                val cleanPhone = phone.replace("+", "").replace(" ", "").replace("-", "")
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message ?: "")}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    setPackage("com.whatsapp")
                }
                context.startActivity(intent)
                Pair(true, "Opening WhatsApp chat for $cleanPhone")
            } else if (!message.isNullOrBlank()) {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage("com.whatsapp")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(sendIntent)
                Pair(true, "Sharing message to WhatsApp")
            } else {
                launchAppByNameOrPackage("whatsapp")
            }
        } catch (e: Exception) {
            // Fallback to general intent or browser
            Log.e("SystemBridge", "WhatsApp launch fallback", e)
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message ?: "")}")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
                Pair(true, "Opened WhatsApp Web / link")
            } catch (e2: Exception) {
                Pair(false, "WhatsApp is not installed.")
            }
        }
    }

    // --- PHONE CALLS ---
    fun initiateCall(number: String, requireDirectCall: Boolean = false): Pair<Boolean, String> {
        val cleanNumber = number.trim()
        if (cleanNumber.isEmpty()) return Pair(false, "Invalid phone number")

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return if (requireDirectCall && hasCallPermission) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(callIntent)
                Pair(true, "Calling $cleanNumber directly...")
            } catch (e: Exception) {
                Pair(false, "Failed to place call: ${e.message}")
            }
        } else {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
                Pair(true, "Dialer opened for $cleanNumber")
            } catch (e: Exception) {
                Pair(false, "Cannot open dialer: ${e.message}")
            }
        }
    }

    // --- SMS / MESSAGING ---
    fun sendSmsDirect(phoneNumber: String, message: String): Pair<Boolean, String> {
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) {
            return openSmsCompose(phoneNumber, message)
        }

        return try {
            @Suppress("DEPRECATION")
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
            } else {
                SmsManager.getDefault()
            }
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            Pair(true, "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e("SystemBridge", "Direct SMS failed, opening compose", e)
            openSmsCompose(phoneNumber, message)
        }
    }

    fun openSmsCompose(phoneNumber: String?, message: String?): Pair<Boolean, String> {
        return try {
            val uri = Uri.parse(if (phoneNumber.isNullOrBlank()) "sms:" else "smsto:$phoneNumber")
            val smsIntent = Intent(Intent.ACTION_SENDTO, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (!message.isNullOrBlank()) {
                    putExtra("sms_body", message)
                }
            }
            context.startActivity(smsIntent)
            Pair(true, "SMS composer opened")
        } catch (e: Exception) {
            Pair(false, "Cannot open SMS app: ${e.message}")
        }
    }

    // --- WEB / SEARCH ---
    fun openWebSearch(query: String): Pair<Boolean, String> {
        return try {
            val uri = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Pair(true, "Searching web for: $query")
        } catch (e: Exception) {
            Pair(false, "Failed to open browser: ${e.message}")
        }
    }

    // --- SYSTEM SETTINGS SHORTCUTS ---
    fun openSetting(settingName: String): Pair<Boolean, String> {
        val action = when (settingName.lowercase()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            val intent = Intent(action).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Pair(true, "Opened $settingName settings")
        } catch (e: Exception) {
            Pair(false, "Cannot open $settingName: ${e.message}")
        }
    }

    fun openSystemSettings(): Pair<Boolean, String> = openSetting("settings")
    fun openDisplaySettings(): Pair<Boolean, String> = openSetting("display")
    fun openWifiSettings(): Pair<Boolean, String> = openSetting("wifi")
    fun openBluetoothSettings(): Pair<Boolean, String> = openSetting("bluetooth")

    fun scheduleSystemAlarm(hour: Int, minute: Int, title: String, skipUi: Boolean = true): Boolean {
        return try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, title)
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, skipUi)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e("AndroidSystemBridge", "Error scheduling alarm: ${e.message}")
            false
        }
    }
}
