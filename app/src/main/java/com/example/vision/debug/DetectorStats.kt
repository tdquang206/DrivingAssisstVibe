package com.example.vision.debug

data class DetectorStats(
    val fps: Float = 0f,
    val latestInferenceTimeMs: Long = 0L,
    val avgInferenceTimeMs: Long = 0L,
    val preprocessTimeMs: Long = 0L,
    val postprocessTimeMs: Long = 0L,
    val totalPipelineTimeMs: Long = 0L,
    val vehicleCount: Int = 0,
    val leadVehicleType: String = "NONE",
    val leadScore: Float = 0f,
    val modelResolution: String = "Unknown",
    val skippedFrames: Int = 0,
    val maxVehicleConf: Float = 0f,
    val rawCandidatesCount: Int = 0,
    val aboveConfCount: Int = 0,
    val validBoxCount: Int = 0,
    val afterNmsCount: Int = 0,
    val minConfidence: Float = 0.40f
)
