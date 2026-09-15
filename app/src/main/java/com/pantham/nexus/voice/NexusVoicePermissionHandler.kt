package com.pantham.nexus.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Connect microphone permission check with NexusVoiceController.
 * Returns an onClick lambda ready for UI buttons.
 */
@Composable
fun rememberNexusVoicePermissionHandler(
    voiceController: NexusVoiceController,
    onPermissionDenied: (() -> Unit)? = null
): () -> Unit {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceController.startListening()
        } else {
            Toast.makeText(
                context,
                "Microphone permission चाहिए। कृपया Settings में microphone allow करें।",
                Toast.LENGTH_LONG
            ).show()
            onPermissionDenied?.invoke()
        }
    }

    return remember(context, voiceController) {
        {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                voiceController.startListening()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}
