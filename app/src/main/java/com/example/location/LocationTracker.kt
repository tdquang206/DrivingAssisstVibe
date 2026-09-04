package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface LocationTracker {
    val currentSpeedKmh: StateFlow<Float>
    fun startTracking()
    fun stopTracking()
}

class PlayServicesLocationTracker(private val context: Context) : LocationTracker {
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    
    private val _currentSpeedKmh = MutableStateFlow(0f)
    override val currentSpeedKmh: StateFlow<Float> = _currentSpeedKmh.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                if (location.hasSpeed()) {
                    // Convert m/s to km/h
                    val speedKmh = location.speed * 3.6f
                    _currentSpeedKmh.value = speedKmh
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startTracking() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    override fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
