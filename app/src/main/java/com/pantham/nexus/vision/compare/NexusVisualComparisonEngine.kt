package com.pantham.nexus.vision.compare

import android.graphics.Bitmap
import android.graphics.Rect
import com.pantham.nexus.vision.model.VisualComparisonResult
import com.pantham.nexus.vision.model.VisionBoundingBox
import kotlin.math.abs

class NexusVisualComparisonEngine {

    fun compare(
        first: Bitmap,
        second: Bitmap,
        threshold: Int = 24,
        blockSize: Int = 32
    ): VisualComparisonResult {

        val width =
            minOf(
                first.width,
                second.width
            )

        val height =
            minOf(
                first.height,
                second.height
            )

        var changedPixels = 0
        var totalPixels = 0

        val regions =
            mutableListOf<VisionBoundingBox>()

        var y = 0

        while (y < height) {

            var x = 0

            while (x < width) {

                val right =
                    minOf(
                        x + blockSize,
                        width
                    )

                val bottom =
                    minOf(
                        y + blockSize,
                        height
                    )

                var localChanged =
                    0

                var localTotal =
                    0

                for (py in y until bottom) {

                    for (px in x until right) {

                        val c1 =
                            first.getPixel(px, py)

                        val c2 =
                            second.getPixel(px, py)

                        val diff =
                            abs(
                                ((c1 shr 16) and 0xFF) -
                                ((c2 shr 16) and 0xFF)
                            ) +
                            abs(
                                ((c1 shr 8) and 0xFF) -
                                ((c2 shr 8) and 0xFF)
                            ) +
                            abs(
                                (c1 and 0xFF) -
                                (c2 and 0xFF)
                            )

                        if (diff > threshold) {
                            localChanged++
                            changedPixels++
                        }

                        localTotal++
                        totalPixels++
                    }
                }

                if (
                    localTotal > 0 &&
                    localChanged.toFloat() /
                    localTotal > 0.12f
                ) {

                    regions +=
                        VisionBoundingBox(
                            left = x,
                            top = y,
                            right = right,
                            bottom = bottom
                        )
                }

                x += blockSize
            }

            y += blockSize
        }

        val similarity =
            1f -
                (
                    changedPixels
                        .toFloat() /
                    totalPixels
                )

        return VisualComparisonResult(
            similarity =
                similarity.coerceIn(
                    0f,
                    1f
                ),
            changed =
                similarity < 0.995f,
            changedRegions =
                regions,
            summary =
                if (similarity < 0.995f) {
                    "Visual changes detected in ${regions.size} regions."
                } else {
                    "No significant visual change detected."
                }
        )
    }
}
