package com.example.vision

import com.example.vision.debug.DetectorStats

data class VehicleDetectionState(
    val vehicles: List<DetectedVehicle> = emptyList(),
    val leadVehicle: DetectedVehicle? = null,
    val stats: DetectorStats = DetectorStats()
)
