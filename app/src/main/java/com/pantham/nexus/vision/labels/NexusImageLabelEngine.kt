package com.pantham.nexus.vision.labels

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.pantham.nexus.vision.model.VisionLabel
import kotlinx.coroutines.tasks.await

class NexusImageLabelEngine {

    private val labeler =
        ImageLabeling.getClient(
            ImageLabelerOptions.DEFAULT_OPTIONS
        )

    suspend fun detect(
        bitmap: Bitmap,
        rotationDegrees: Int = 0
    ): List<VisionLabel> {

        val image =
            InputImage.fromBitmap(
                bitmap,
                rotationDegrees
            )

        return labeler
            .process(image)
            .await()
            .map {
                VisionLabel(
                    label = it.text,
                    confidence = it.confidence
                )
            }
            .sortedByDescending {
                it.confidence
            }
    }

    fun close() {
        labeler.close()
    }
}
