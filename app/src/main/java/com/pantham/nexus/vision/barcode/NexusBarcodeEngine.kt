package com.pantham.nexus.vision.barcode

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.pantham.nexus.vision.model.VisionBarcode
import com.pantham.nexus.vision.model.VisionBoundingBox
import kotlinx.coroutines.tasks.await

class NexusBarcodeEngine {

    private val scanner =
        BarcodeScanning.getClient()

    suspend fun scan(
        bitmap: Bitmap,
        rotationDegrees: Int = 0
    ): List<VisionBarcode> {

        val image =
            InputImage.fromBitmap(
                bitmap,
                rotationDegrees
            )

        return scanner
            .process(image)
            .await()
            .map { barcode ->

                VisionBarcode(
                    rawValue = barcode.rawValue,
                    displayValue = barcode.displayValue,
                    format = barcode.format,
                    boundingBox =
                        barcode.boundingBox?.let {
                            VisionBoundingBox(
                                left = it.left,
                                top = it.top,
                                right = it.right,
                                bottom = it.bottom
                            )
                        }
                )
            }
    }

    fun close() {
        scanner.close()
    }
}
