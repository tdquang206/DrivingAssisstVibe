package com.example.vision.lead

import com.example.vision.DetectedVehicle

interface LeadVehicleSelector {
    fun selectLeadVehicle(vehicles: List<DetectedVehicle>): DetectedVehicle?
}
