package com.pantham.nexus.vision.model

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

object InputImageConverter {

    fun imageProxyToBitmap(
        image: ImageProxy
    ): Bitmap? {

        if (
            image.format !=
            ImageFormat.YUV_420_888
        ) {
            return null
        }

        val y =
            image.planes[0]
                .buffer

        val u =
            image.planes[1]
                .buffer

        val v =
            image.planes[2]
                .buffer

        val yBytes =
            ByteArray(y.remaining()).also {
                y.get(it)
            }

        val uBytes =
            ByteArray(u.remaining()).also {
                u.get(it)
            }

        val vBytes =
            ByteArray(v.remaining()).also {
                v.get(it)
            }

        return Yuv420Converter.convert(
            yBytes = yBytes,
            uBytes = uBytes,
            vBytes = vBytes,
            width = image.width,
            height = image.height,
            yRowStride =
                image.planes[0].rowStride,
            uRowStride =
                image.planes[1].rowStride,
            vRowStride =
                image.planes[2].rowStride,
            uPixelStride =
                image.planes[1].pixelStride,
            vPixelStride =
                image.planes[2].pixelStride
        )
    }
}

object Yuv420Converter {

    fun convert(
        yBytes: ByteArray,
        uBytes: ByteArray,
        vBytes: ByteArray,
        width: Int,
        height: Int,
        yRowStride: Int,
        uRowStride: Int,
        vRowStride: Int,
        uPixelStride: Int,
        vPixelStride: Int
    ): Bitmap {

        val argb =
            IntArray(
                width * height
            )

        for (row in 0 until height) {

            for (col in 0 until width) {

                val yIndex =
                    row * yRowStride + col

                val uvRow =
                    row / 2

                val uvCol =
                    col / 2

                val uIndex =
                    uvRow * uRowStride +
                        uvCol * uPixelStride

                val vIndex =
                    uvRow * vRowStride +
                        uvCol * vPixelStride

                val y =
                    (yBytes[yIndex].toInt() and 0xFF)

                val u =
                    (uBytes[uIndex].toInt() and 0xFF)

                val v =
                    (vBytes[vIndex].toInt() and 0xFF)

                val yValue =
                    (y - 16)
                        .coerceAtLeast(0)

                val r =
                    (1.164f * yValue +
                        1.596f * (v - 128))
                        .toInt()
                        .coerceIn(0, 255)

                val g =
                    (1.164f * yValue -
                        0.813f * (v - 128) -
                        0.391f * (u - 128))
                        .toInt()
                        .coerceIn(0, 255)

                val b =
                    (1.164f * yValue +
                        2.018f * (u - 128))
                        .toInt()
                        .coerceIn(0, 255)

                argb[
                    row * width + col
                ] =
                    (0xFF shl 24) or
                    (r shl 16) or
                    (g shl 8) or
                    b
            }
        }

        return Bitmap.createBitmap(
            argb,
            width,
            height,
            Bitmap.Config.ARGB_8888
        )
    }
}
