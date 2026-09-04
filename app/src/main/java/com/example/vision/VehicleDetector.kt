package com.example.vision

import android.graphics.Bitmap

interface VehicleDetector {
    fun detect(bitmap: Bitmap, rotationDegrees: Int = 0): DetectionFrame
    fun close()
}
