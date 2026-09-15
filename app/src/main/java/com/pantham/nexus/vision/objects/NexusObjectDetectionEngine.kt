package com.pantham.nexus.vision.objects

import android.graphics.Bitmap
import com.pantham.nexus.vision.model.VisionObject

interface NexusObjectDetector {

    suspend fun detect(
        bitmap: Bitmap,
        rotationDegrees: Int
    ): List<VisionObject>
}

class EmptyObjectDetector :
    NexusObjectDetector {

    override suspend fun detect(
        bitmap: Bitmap,
        rotationDegrees: Int
    ): List<VisionObject> {
        return emptyList()
    }
}
