package com.pantham.nexus.vision.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

data class VisionPermissionState(
    val cameraGranted: Boolean,
    val screenCaptureActive: Boolean
)

class NexusVisionPermissionChecker(
    private val context: Context
) {

    fun cameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) ==
            PackageManager.PERMISSION_GRANTED

    fun state(
        screenCaptureActive: Boolean
    ): VisionPermissionState {

        return VisionPermissionState(
            cameraGranted =
                cameraGranted(),
            screenCaptureActive =
                screenCaptureActive
        )
    }
}
