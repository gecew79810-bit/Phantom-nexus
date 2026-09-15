package com.example.hardware

/**
 * HardwareState:
 * Immutable snapshot of device hardware controllers and current operational status.
 */
data class HardwareState(
    val isWifiEnabled: Boolean = false,
    val isWifiConnected: Boolean = false,
    val wifiSsid: String = "Disconnected",
    val isBluetoothEnabled: Boolean = false,
    val isBluetoothSupported: Boolean = false,
    val bluetoothDeviceCount: Int = 0,
    val isFlashlightOn: Boolean = false,
    val hasFlashlight: Boolean = false,
    val currentVolume: Int = 0,
    val maxVolume: Int = 100,
    val volumePercent: Int = 0,
    val isMuted: Boolean = false,
    val ringerMode: RingerMode = RingerMode.NORMAL,
    val lastActionMessage: String = "Hardware controller standing by"
)

enum class RingerMode {
    NORMAL,
    SILENT,
    VIBRATE
}

sealed class HardwareActionResult {
    data class Success(val message: String, val speechFeedback: String) : HardwareActionResult()
    data class DirectIntent(val intentAction: String, val message: String, val speechFeedback: String) : HardwareActionResult()
    data class PermissionRequired(val permission: String, val message: String, val speechFeedback: String) : HardwareActionResult()
    data class Error(val error: String, val speechFeedback: String) : HardwareActionResult()
}
