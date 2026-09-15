package com.pantham.nexus.vision.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pantham.nexus.vision.model.VisionTextBlock
import com.pantham.nexus.vision.model.VisionBoundingBox
import kotlinx.coroutines.tasks.await

class NexusTextRecognitionEngine {

    private val recognizer =
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

    suspend fun recognize(
        bitmap: Bitmap,
        rotationDegrees: Int = 0
    ): List<VisionTextBlock> {

        val image =
            InputImage.fromBitmap(
                bitmap,
                rotationDegrees
            )

        val result =
            recognizer.process(
                image
            ).await()

        return result.textBlocks.flatMap { block ->

            block.lines.map { line ->

                VisionTextBlock(
                    text = line.text,
                    boundingBox =
                        line.boundingBox?.let {
                            VisionBoundingBox(
                                left = it.left,
                                top = it.top,
                                right = it.right,
                                bottom = it.bottom
                            )
                        },
                    confidence = null
                )
            }
        }
    }

    suspend fun recognizeFullText(
        bitmap: Bitmap,
        rotationDegrees: Int = 0
    ): String {

        val image =
            InputImage.fromBitmap(
                bitmap,
                rotationDegrees
            )

        return recognizer
            .process(image)
            .await()
            .text
    }

    fun close() {
        recognizer.close()
    }
}
