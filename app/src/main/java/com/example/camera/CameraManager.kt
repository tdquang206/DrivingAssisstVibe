package com.example.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import androidx.camera.core.Preview
import androidx.camera.core.Preview.SurfaceProvider

class CameraManager(private val context: Context) {
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider> = ProcessCameraProvider.getInstance(context)
    private var analysisExecutor: ExecutorService? = null
    
    // Store latest state so we can rebind if surface provider changes
    private var lastLifecycleOwner: LifecycleOwner? = null
    private var lastAnalyzer: ImageAnalysis.Analyzer? = null
    private var lastSurfaceProvider: SurfaceProvider? = null

    fun startCamera(lifecycleOwner: LifecycleOwner, analyzer: ImageAnalysis.Analyzer? = null, surfaceProvider: SurfaceProvider? = null) {
        lastLifecycleOwner = lifecycleOwner
        lastAnalyzer = analyzer
        lastSurfaceProvider = surfaceProvider
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                cameraProvider.unbindAll()

                val useCases = mutableListOf<androidx.camera.core.UseCase>()

                if (surfaceProvider != null) {
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(surfaceProvider)
                    useCases.add(preview)
                }

                if (analyzer != null) {
                    if (analysisExecutor == null || analysisExecutor?.isShutdown == true) {
                        analysisExecutor = Executors.newSingleThreadExecutor()
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(analysisExecutor!!, analyzer)
                    useCases.add(imageAnalysis)
                }

                if (useCases.isNotEmpty()) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        *useCases.toTypedArray()
                    )
                    Log.d("CameraManager", "Camera started with use cases: ${useCases.size}")
                }
            } catch (exc: Exception) {
                Log.e("CameraManager", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // New method to allow attaching a surface dynamically without restarting the whole pipeline if possible,
    // or just re-bind everything
    fun setSurfaceProvider(surfaceProvider: SurfaceProvider?) {
        val owner = lastLifecycleOwner
        if (owner != null) {
            startCamera(owner, lastAnalyzer, surfaceProvider)
        }
    }

    fun stopCamera() {
        try {
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            analysisExecutor?.shutdown()
            analysisExecutor = null
            Log.d("CameraManager", "Camera stopped")
        } catch (exc: Exception) {
            Log.e("CameraManager", "Failed to stop camera", exc)
        }
    }
}
