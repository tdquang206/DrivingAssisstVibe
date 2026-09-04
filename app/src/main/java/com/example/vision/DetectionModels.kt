package com.example.vision

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    val bottomY get() = bottom
    val width get() = right - left
    val height get() = bottom - top
    val area get() = width * height
}

enum class VehicleType {
    CAR,
    TRUCK,
    BUS,
    MOTORCYCLE,
    UNKNOWN
}

data class DetectedVehicle(
    val trackingId: Long? = null,
    val type: VehicleType,
    val confidence: Float,
    val boundingBox: NormalizedRect,
    val leadScore: Float? = null,
    val isLead: Boolean = false
)

data class DetectionFrame(
    val timestampMs: Long,
    val vehicles: List<DetectedVehicle>,
    val inferenceTimeMs: Long
)
