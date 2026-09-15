package com.pantham.nexus.vision.health

enum class VisionHealth {
    HEALTHY,
    CAMERA_PERMISSION_REQUIRED,
    SCREEN_PERMISSION_REQUIRED,
    MODEL_LOADING,
    DEGRADED,
    OFFLINE_AI,
    ERROR
}

data class VisionHealthSnapshot(
    val health: VisionHealth,
    val camera: Boolean,
    val ocr: Boolean,
    val barcode: Boolean,
    val labels: Boolean,
    val objects: Boolean,
    val screenCapture: Boolean,
    val multimodalAI: Boolean,
    val lastError: String? = null
)

class NexusVisionHealthManager {

    fun evaluate(
        camera: Boolean,
        ocr: Boolean,
        barcode: Boolean,
        labels: Boolean,
        objects: Boolean,
        screenCapture: Boolean,
        multimodalAI: Boolean
    ): VisionHealthSnapshot {

        val health =
            when {

                !camera ->
                    VisionHealth.CAMERA_PERMISSION_REQUIRED

                !ocr &&
                    !objects &&
                    !labels ->
                    VisionHealth.DEGRADED

                else ->
                    VisionHealth.HEALTHY
            }

        return VisionHealthSnapshot(
            health = health,
            camera = camera,
            ocr = ocr,
            barcode = barcode,
            labels = labels,
            objects = objects,
            screenCapture = screenCapture,
            multimodalAI = multimodalAI
        )
    }
}
