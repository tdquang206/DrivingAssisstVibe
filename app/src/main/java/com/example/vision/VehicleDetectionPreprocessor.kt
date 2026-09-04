package com.example.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

class VehicleDetectionPreprocessor(
    val inputWidth: Int = 640,
    val inputHeight: Int = 640
) {
    data class PreprocessedFrame(
        val inputBuffer: ByteBuffer,
        val originalWidth: Int,
        val originalHeight: Int,
        val scale: Float,
        val padX: Float,
        val padY: Float
    )

    private val bufferSize = 1 * inputWidth * inputHeight * 3 * 4
    private val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(bufferSize).apply {
        order(ByteOrder.nativeOrder())
    }

    private val letterboxBitmap: Bitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
    private val letterboxCanvas = Canvas(letterboxBitmap)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val intValues = IntArray(inputWidth * inputHeight)

    @Synchronized
    fun preprocess(sourceBitmap: Bitmap, rotationDegrees: Int): PreprocessedFrame {
        val orientedBitmap: Bitmap
        val needRecycle: Boolean
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            orientedBitmap = Bitmap.createBitmap(
                sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, true
            )
            needRecycle = true
        } else {
            orientedBitmap = sourceBitmap
            needRecycle = false
        }

        val origW = orientedBitmap.width
        val origH = orientedBitmap.height

        val scale = min(inputWidth.toFloat() / origW, inputHeight.toFloat() / origH)
        val scaledW = origW * scale
        val scaledH = origH * scale
        val padX = (inputWidth - scaledW) / 2f
        val padY = (inputHeight - scaledH) / 2f

        letterboxCanvas.drawColor(Color.rgb(114, 114, 114))

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(padX, padY)
        }
        letterboxCanvas.drawBitmap(orientedBitmap, matrix, paint)

        if (needRecycle) {
            orientedBitmap.recycle()
        }

        byteBuffer.rewind()
        letterboxBitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        var pixelIdx = 0
        for (i in 0 until inputWidth * inputHeight) {
            val pixel = intValues[pixelIdx++]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }

        byteBuffer.rewind()

        return PreprocessedFrame(
            inputBuffer = byteBuffer,
            originalWidth = origW,
            originalHeight = origH,
            scale = scale,
            padX = padX,
            padY = padY
        )
    }

    fun mapToNormalizedRect(
        cx: Float,
        cy: Float,
        w: Float,
        h: Float,
        frame: PreprocessedFrame
    ): NormalizedRect {
        val boxLeft = cx - w / 2f
        val boxTop = cy - h / 2f
        val boxRight = cx + w / 2f
        val boxBottom = cy + h / 2f

        val unpaddedLeft = (boxLeft - frame.padX) / frame.scale
        val unpaddedTop = (boxTop - frame.padY) / frame.scale
        val unpaddedRight = (boxRight - frame.padX) / frame.scale
        val unpaddedBottom = (boxBottom - frame.padY) / frame.scale

        val normLeft = (unpaddedLeft / frame.originalWidth).coerceIn(0f, 1f)
        val normTop = (unpaddedTop / frame.originalHeight).coerceIn(0f, 1f)
        val normRight = (unpaddedRight / frame.originalWidth).coerceIn(0f, 1f)
        val normBottom = (unpaddedBottom / frame.originalHeight).coerceIn(0f, 1f)

        return NormalizedRect(
            left = normLeft,
            top = normTop,
            right = normRight,
            bottom = normBottom
        )
    }
}
