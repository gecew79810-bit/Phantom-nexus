package com.example.hardware

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * HardwareController:
 * Dedicated service interacting directly with Android system APIs to manage:
 * - Wi-Fi (status query, toggle / settings intent for modern Android)
 * - Bluetooth (adapter status, enable / disable, settings panel)
 * - Flashlight / Torch (Camera2 API torch mode)
 * - Volume & Audio Stream Levels (Media volume, mute, max, step adjustments, ringer modes)
 *
 * Provides real-time reactive observability via [hardwareState] and rich semantic
 * voice-friendly response messages in Hindi & English for the Max assistant.
 */
class HardwareController(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "HardwareController"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _hardwareState = MutableStateFlow(HardwareState())
    val hardwareState: StateFlow<HardwareState> = _hardwareState.asStateFlow()

    private var activeTorchCameraId: String? = null
    private var isTorchActive = false

    // Torch callback listener
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            if (cameraId == activeTorchCameraId) {
                isTorchActive = enabled
                updateState { copy(isFlashlightOn = enabled) }
            }
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            super.onTorchModeUnavailable(cameraId)
            if (cameraId == activeTorchCameraId) {
                isTorchActive = false
                updateState { copy(isFlashlightOn = false) }
            }
        }
    }

    init {
        initCameraTorchListener()
        registerHardwareReceivers()
        refreshAllHardwareStates()
    }

    private inline fun updateState(crossinline block: HardwareState.() -> HardwareState) {
        _hardwareState.value = _hardwareState.value.block()
    }

    /**
     * Initializes camera flashlight hardware discovery and register callback.
     */
    private fun initCameraTorchListener() {
        try {
            val cm = cameraManager ?: return
            val idList = cm.cameraIdList
            activeTorchCameraId = idList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                flashAvailable && (facing == CameraCharacteristics.LENS_FACING_BACK)
            } ?: idList.firstOrNull()

            val hasFlash = activeTorchCameraId != null
            updateState { copy(hasFlashlight = hasFlash) }

            cm.registerTorchCallback(torchCallback, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing camera torch listener", e)
        }
    }

    /**
     * Registers system broadcast receivers for real-time hardware status changes
     * (e.g., Bluetooth state change, Wi-Fi connectivity, Volume changes).
     */
    private fun registerHardwareReceivers() {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction("android.media.VOLUME_CHANGED_ACTION")
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        BluetoothAdapter.ACTION_STATE_CHANGED -> refreshBluetoothState()
                        WifiManager.WIFI_STATE_CHANGED_ACTION,
                        WifiManager.NETWORK_STATE_CHANGED_ACTION -> refreshWifiState()
                        "android.media.VOLUME_CHANGED_ACTION",
                        AudioManager.RINGER_MODE_CHANGED_ACTION -> refreshVolumeState()
                    }
                }
            }

            context.registerReceiver(receiver, filter)

            // Register NetworkCallback for modern Wi-Fi connectivity monitoring
            connectivityManager?.let { cm ->
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build()
                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        refreshWifiState()
                    }

                    override fun onLost(network: Network) {
                        refreshWifiState()
                    }
                })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error registering hardware receivers", e)
        }
    }

    /**
     * Refreshes the aggregate state of all hardware controllers.
     */
    fun refreshAllHardwareStates() {
        refreshWifiState()
        refreshBluetoothState()
        refreshVolumeState()
    }

    // ==========================================
    // 1. WI-FI CONTROLLER
    // ==========================================

    fun refreshWifiState() {
        try {
            val isEnabled = wifiManager?.isWifiEnabled ?: false
            var isConnected = false
            var ssid = "Disconnected"

            connectivityManager?.let { cm ->
                val activeNetwork = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(activeNetwork)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    isConnected = true
                    val wifiInfo = wifiManager?.connectionInfo
                    val rawSsid = wifiInfo?.ssid?.replace("\"", "") ?: ""
                    ssid = if (rawSsid.isNotBlank() && rawSsid != "<unknown ssid>") rawSsid else "Connected Wi-Fi"
                }
            }

            updateState {
                copy(
                    isWifiEnabled = isEnabled,
                    isWifiConnected = isConnected,
                    wifiSsid = ssid
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing Wi-Fi state", e)
        }
    }

    /**
     * Toggles or sets Wi-Fi state.
     * On Android Q (API 29) and above, Google restricts direct wifiManager.setWifiEnabled()
     * and requires the system Wi-Fi panel intent or settings toggle.
     */
    fun setWifiEnabled(enable: Boolean): HardwareActionResult {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                val success = wifiManager?.setWifiEnabled(enable) ?: false
                refreshWifiState()
                val speech = if (enable) "बॉस, वाई-फाई चालू कर दिया है।" else "बॉस, वाई-फाई बंद कर दिया है।"
                return HardwareActionResult.Success(
                    message = if (enable) "Wi-Fi enabled" else "Wi-Fi disabled",
                    speechFeedback = speech
                )
            } else {
                // Android 10+ Intent-based System Panel
                val intentAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_WIFI
                } else {
                    Settings.ACTION_WIFI_SETTINGS
                }
                val intent = Intent(intentAction).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                val stateText = if (enable) "चालू" else "बंद"
                val speech = "बॉस, वाई-फाई $stateText करने के लिए सिस्टम पैनल खोल दिया है।"
                return HardwareActionResult.DirectIntent(
                    intentAction = intentAction,
                    message = "Opened Wi-Fi system panel",
                    speechFeedback = speech
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle Wi-Fi", e)
            openWifiSettings()
            return HardwareActionResult.DirectIntent(
                intentAction = Settings.ACTION_WIFI_SETTINGS,
                message = "Opened Wi-Fi settings",
                speechFeedback = "बॉस, वाई-फाई सेटिंग्स खोल दी हैं।"
            )
        }
    }

    fun openWifiSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Wi-Fi settings", e)
            false
        }
    }

    // ==========================================
    // 2. BLUETOOTH CONTROLLER
    // ==========================================

    fun refreshBluetoothState() {
        try {
            val adapter = bluetoothAdapter
            val isSupported = adapter != null
            val isEnabled = adapter?.isEnabled == true
            var bondedCount = 0

            if (isEnabled && adapter != null) {
                val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                } else true

                if (hasPerm) {
                    try {
                        bondedCount = adapter.bondedDevices?.size ?: 0
                    } catch (se: SecurityException) {
                        Log.w(TAG, "Missing BLUETOOTH_CONNECT permission to read bonded devices", se)
                    }
                }
            }

            updateState {
                copy(
                    isBluetoothSupported = isSupported,
                    isBluetoothEnabled = isEnabled,
                    bluetoothDeviceCount = bondedCount
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing Bluetooth state", e)
        }
    }

    /**
     * Toggles or sets Bluetooth state.
     * Manages BLUETOOTH_CONNECT runtime permissions on Android 12+ (API 31).
     */
    fun setBluetoothEnabled(enable: Boolean): HardwareActionResult {
        val adapter = bluetoothAdapter
            ?: return HardwareActionResult.Error(
                error = "Bluetooth hardware unavailable",
                speechFeedback = "बॉस, इस डिवाइस में ब्लूटूथ हार्डवेयर उपलब्ध नहीं है।"
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                openBluetoothSettings()
                return HardwareActionResult.PermissionRequired(
                    permission = Manifest.permission.BLUETOOTH_CONNECT,
                    message = "Bluetooth permission required on Android 12+",
                    speechFeedback = "बॉस, ब्लूटूथ नियंत्रित करने के लिए सेटिंग्स खोल दी गई हैं।"
                )
            }
        }

        return try {
            @Suppress("DEPRECATION")
            val success = if (enable) {
                adapter.enable()
            } else {
                adapter.disable()
            }

            if (success) {
                refreshBluetoothState()
                val speech = if (enable) "बॉस, ब्लूटूथ चालू कर दिया है।" else "बॉस, ब्लूटूथ बंद कर दिया है।"
                HardwareActionResult.Success(
                    message = if (enable) "Bluetooth turned ON" else "Bluetooth turned OFF",
                    speechFeedback = speech
                )
            } else {
                openBluetoothSettings()
                HardwareActionResult.DirectIntent(
                    intentAction = Settings.ACTION_BLUETOOTH_SETTINGS,
                    message = "Opened Bluetooth settings",
                    speechFeedback = "बॉस, ब्लूटूथ सेटिंग्स खोल दी हैं।"
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while toggling bluetooth", e)
            openBluetoothSettings()
            HardwareActionResult.DirectIntent(
                intentAction = Settings.ACTION_BLUETOOTH_SETTINGS,
                message = "Opened Bluetooth settings",
                speechFeedback = "बॉस, ब्लूटूथ सेटिंग्स खोल दी हैं।"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling bluetooth", e)
            openBluetoothSettings()
            HardwareActionResult.DirectIntent(
                intentAction = Settings.ACTION_BLUETOOTH_SETTINGS,
                message = "Opened Bluetooth settings",
                speechFeedback = "बॉस, ब्लूटूथ सेटिंग्स खोल दी हैं।"
            )
        }
    }

    fun openBluetoothSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Bluetooth settings", e)
            false
        }
    }

    // ==========================================
    // 3. FLASHLIGHT / TORCH CONTROLLER
    // ==========================================

    /**
     * Toggles flashlight or sets exact state using CameraManager Camera2 API.
     */
    fun setFlashlightEnabled(enable: Boolean): HardwareActionResult {
        val cm = cameraManager
            ?: return HardwareActionResult.Error(
                error = "Camera service not accessible",
                speechFeedback = "कैमरा सर्विस उपलब्ध नहीं है।"
            )

        val cameraId = activeTorchCameraId
            ?: return HardwareActionResult.Error(
                error = "Flashlight hardware not found",
                speechFeedback = "बॉस, टॉर्च हार्डवेयर नहीं मिला।"
            )

        return try {
            cm.setTorchMode(cameraId, enable)
            isTorchActive = enable
            updateState { copy(isFlashlightOn = enable) }
            val speech = if (enable) "टॉर्च चालू कर दी है बॉस।" else "टॉर्च बंद कर दी है।"
            HardwareActionResult.Success(
                message = if (enable) "Torch ON" else "Torch OFF",
                speechFeedback = speech
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting flashlight state: $enable", e)
            HardwareActionResult.Error(
                error = e.localizedMessage ?: "Torch error",
                speechFeedback = "टॉर्च सेट करने में समस्या आई: ${e.message}"
            )
        }
    }

    fun toggleFlashlight(): HardwareActionResult {
        return setFlashlightEnabled(!isTorchActive)
    }

    // ==========================================
    // 4. VOLUME CONTROLLER
    // ==========================================

    fun refreshVolumeState() {
        try {
            val am = audioManager ?: return
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val pct = ((current.toFloat() / max.toFloat()) * 100).toInt()
            val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.isStreamMute(AudioManager.STREAM_MUSIC)
            } else current == 0

            val ringer = when (am.ringerMode) {
                AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
                AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
                else -> RingerMode.NORMAL
            }

            updateState {
                copy(
                    currentVolume = current,
                    maxVolume = max,
                    volumePercent = pct,
                    isMuted = isMuted,
                    ringerMode = ringer
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing volume state", e)
        }
    }

    /**
     * Adjusts music volume step up or step down.
     */
    fun adjustVolume(direction: Int): HardwareActionResult {
        val am = audioManager
            ?: return HardwareActionResult.Error("Audio system unavailable", "ऑडियो सिस्टम उपलब्ध नहीं है।")

        try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            refreshVolumeState()
            val pct = _hardwareState.value.volumePercent
            val dirWord = if (direction == AudioManager.ADJUST_RAISE) "बढ़ाकर" else "घटाकर"
            return HardwareActionResult.Success(
                message = "Volume adjusted to $pct%",
                speechFeedback = "बॉस, आवाज $dirWord $pct% कर दी गई है।"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting volume", e)
            return HardwareActionResult.Error(e.localizedMessage ?: "Volume adjustment error", "वॉल्यूम एडजस्ट नहीं हो सका।")
        }
    }

    /**
     * Sets volume to specific percentage (0 - 100%).
     */
    fun setVolumePercent(percent: Int): HardwareActionResult {
        val am = audioManager
            ?: return HardwareActionResult.Error("Audio system unavailable", "ऑडियो सिस्टम उपलब्ध नहीं है।")

        try {
            val clamped = percent.coerceIn(0, 100)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = ((clamped / 100f) * max).toInt()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            refreshVolumeState()
            val speech = if (clamped == 0) "बॉस, वॉल्यूम म्यूट कर दिया है।" else "बॉस, आवाज $clamped% पर सेट कर दी है।"
            return HardwareActionResult.Success(
                message = "Volume set to $clamped%",
                speechFeedback = speech
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume percent: $percent", e)
            return HardwareActionResult.Error(e.localizedMessage ?: "Set volume error", "वॉल्यूम सेट नहीं हो सका।")
        }
    }

    /**
     * Mutes or unmutes the audio stream.
     */
    fun setMute(mute: Boolean): HardwareActionResult {
        val am = audioManager
            ?: return HardwareActionResult.Error("Audio system unavailable", "ऑडियो सिस्टम उपलब्ध नहीं है।")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                    AudioManager.FLAG_SHOW_UI
                )
            } else {
                @Suppress("DEPRECATION")
                am.setStreamMute(AudioManager.STREAM_MUSIC, mute)
            }
            refreshVolumeState()
            val speech = if (mute) "बॉस, आवाज म्यूट कर दी गई है।" else "बॉस, आवाज अनम्यूट कर दी गई है।"
            return HardwareActionResult.Success(
                message = if (mute) "Audio Muted" else "Audio Unmuted",
                speechFeedback = speech
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error setting mute state: $mute", e)
            return HardwareActionResult.Error(e.localizedMessage ?: "Mute error", "म्यूट स्थिति बदलने में समस्या आई।")
        }
    }

    /**
     * Maximize volume.
     */
    fun setMaxVolume(): HardwareActionResult {
        return setVolumePercent(100)
    }

    // ==========================================
    // 5. NATURAL VOICE COMMAND PROCESSOR
    // ==========================================

    /**
     * Parses and executes natural language voice queries and commands specifically for
     * Wi-Fi, Bluetooth, Flashlight, and Volume in English, Hindi, and Hinglish.
     * Returns true if command matched and was handled.
     */
    fun handleVoiceCommand(rawCommand: String, onResult: (HardwareActionResult) -> Unit): Boolean {
        val lower = rawCommand.lowercase().trim()

        // ------------------ WI-FI COMMANDS ------------------
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("वाईफाई") || lower.contains("वाई-फाई")) {
            val turnOff = lower.contains("off") || lower.contains("band") || lower.contains("बंद") || lower.contains("disable")
            val turnOn = lower.contains("on") || lower.contains("chalu") || lower.contains("चालू") || lower.contains("enable") || lower.contains("start")
            val checkStatus = lower.contains("status") || lower.contains("check") || lower.contains("क्या है") || lower.contains("कनेक्ट") || lower.contains("connected")

            val actionResult = when {
                turnOff -> setWifiEnabled(false)
                turnOn -> setWifiEnabled(true)
                checkStatus -> {
                    refreshWifiState()
                    val s = _hardwareState.value
                    val msg = if (s.isWifiConnected) "Wi-Fi Connected to ${s.wifiSsid}" else if (s.isWifiEnabled) "Wi-Fi is ON (Disconnected)" else "Wi-Fi is OFF"
                    val speech = if (s.isWifiConnected) "बॉस, वाई-फाई ${s.wifiSsid} से कनेक्टेड है।" else if (s.isWifiEnabled) "बॉस, वाई-फाई चालू है लेकिन किसी नेटवर्क से कनेक्टेड नहीं है।" else "बॉस, वाई-फाई अभी बंद है।"
                    HardwareActionResult.Success(msg, speech)
                }
                else -> {
                    // Default to toggle or panel
                    val newState = !_hardwareState.value.isWifiEnabled
                    setWifiEnabled(newState)
                }
            }
            onResult(actionResult)
            return true
        }

        // ------------------ BLUETOOTH COMMANDS ------------------
        if (lower.contains("bluetooth") || lower.contains("ब्लूटूथ") || lower.contains("blue tooth")) {
            val turnOff = lower.contains("off") || lower.contains("band") || lower.contains("बंद") || lower.contains("disable")
            val turnOn = lower.contains("on") || lower.contains("chalu") || lower.contains("चालू") || lower.contains("enable") || lower.contains("start")
            val checkStatus = lower.contains("status") || lower.contains("check") || lower.contains("क्या है") || lower.contains("connected")

            val actionResult = when {
                turnOff -> setBluetoothEnabled(false)
                turnOn -> setBluetoothEnabled(true)
                checkStatus -> {
                    refreshBluetoothState()
                    val s = _hardwareState.value
                    val msg = if (s.isBluetoothEnabled) "Bluetooth is ON (${s.bluetoothDeviceCount} paired devices)" else "Bluetooth is OFF"
                    val speech = if (s.isBluetoothEnabled) "बॉस, ब्लूटूथ चालू है और ${s.bluetoothDeviceCount} पेयर्ड डिवाइसेस हैं।" else "बॉस, ब्लूटूथ अभी बंद है।"
                    HardwareActionResult.Success(msg, speech)
                }
                else -> {
                    val newState = !_hardwareState.value.isBluetoothEnabled
                    setBluetoothEnabled(newState)
                }
            }
            onResult(actionResult)
            return true
        }

        // ------------------ FLASHLIGHT / TORCH COMMANDS ------------------
        if (lower.contains("torch") || lower.contains("flashlight") || lower.contains("टॉर्च") || lower.contains("फ्लैशलाइट") || (lower.contains("light") && !lower.contains("highlight"))) {
            val turnOff = lower.contains("off") || lower.contains("band") || lower.contains("बंद") || lower.contains("bujha") || lower.contains("बुझा")
            val turnOn = lower.contains("on") || lower.contains("chalu") || lower.contains("चालू") || lower.contains("jala") || lower.contains("जला")

            val actionResult = when {
                turnOff -> setFlashlightEnabled(false)
                turnOn -> setFlashlightEnabled(true)
                else -> toggleFlashlight()
            }
            onResult(actionResult)
            return true
        }

        // ------------------ VOLUME & AUDIO LEVEL COMMANDS ------------------
        if (lower.contains("volume") || lower.contains("वॉल्यूम") || lower.contains("आवाज") || lower.contains("sound") || lower.contains("mute") || lower.contains("म्यूट")) {
            val isMute = lower.contains("mute") || lower.contains("म्यूट") || lower.contains("silent") || lower.contains("शांत")
            val isUnmute = lower.contains("unmute") || lower.contains("अनम्यूट")
            val isMax = lower.contains("full") || lower.contains("max") || lower.contains("100") || lower.contains("फुल") || lower.contains("अधिकतम")
            val isUp = lower.contains("up") || lower.contains("increase") || lower.contains("raise") || lower.contains("higher") || lower.contains("boost") || lower.contains("badhao") || lower.contains("बढ़ाओ") || lower.contains("बढ़ाओ") || lower.contains("tez") || lower.contains("तेज़")
            val isDown = lower.contains("down") || lower.contains("decrease") || lower.contains("lower") || lower.contains("reduce") || lower.contains("kam") || lower.contains("कम") || lower.contains("ghatao") || lower.contains("घटाओ")

            // Check if user requested a specific numeric percentage (e.g. "volume 80%", "आवाज 50 प्रतिशत")
            val percentRegex = Regex("""(\d{1,3})\s*(%|percent|प्रतिशत)?""")
            val percentMatch = percentRegex.find(lower)
            val specifiedPercent = percentMatch?.groupValues?.get(1)?.toIntOrNull()

            val actionResult = when {
                isUnmute -> setMute(false)
                isMute -> setMute(true)
                isMax -> setMaxVolume()
                specifiedPercent != null && specifiedPercent in 0..100 -> setVolumePercent(specifiedPercent)
                isUp -> adjustVolume(AudioManager.ADJUST_RAISE)
                isDown -> adjustVolume(AudioManager.ADJUST_LOWER)
                else -> {
                    refreshVolumeState()
                    val s = _hardwareState.value
                    HardwareActionResult.Success(
                        message = "Current volume is ${s.volumePercent}%",
                        speechFeedback = "बॉस, वर्तमान वॉल्यूम ${s.volumePercent}% पर है।"
                    )
                }
            }
            onResult(actionResult)
            return true
        }

        return false
    }
}
