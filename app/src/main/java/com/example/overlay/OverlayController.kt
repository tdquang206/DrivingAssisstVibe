package com.example.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class OverlayController(
    private val context: Context,
    private val speedFlow: StateFlow<Float>
) {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var speedTextView: TextView? = null
    private var isShowing = false
    
    private var overlayJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    fun start() {
        if (isShowing) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            android.util.Log.e("OverlayController", "Cannot show overlay: overlay permission not granted")
            return
        }
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            android.util.Log.e("OverlayController", "WindowManager is null")
            return
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        // Creating a simple programmatic view for Phase 0
        speedTextView = TextView(context).apply {
            text = "GAP: --.-s  |  SPD: 0 km/h"
            setTextColor(Color(0xFF40C4FF).toArgb())
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color(0xCC000000).toArgb())
                setStroke(2, Color(0x33FFFFFF).toArgb())
                cornerRadius = 32f
            }
            background = drawable
            setPadding(40, 20, 40, 20)
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
        }
        overlayView = speedTextView

        overlayView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager?.updateViewLayout(overlayView, params)
                    } catch (e: Exception) {
                        android.util.Log.e("OverlayController", "Failed to update overlay view layout", e)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(overlayView, params)
            isShowing = true
        } catch (e: Exception) {
            android.util.Log.e("OverlayController", "Failed to add overlay view", e)
            isShowing = false
            return
        }

        overlayJob = scope.launch {
            speedFlow.collect { speed ->
                speedTextView?.text = String.format(Locale.getDefault(), "GAP: --.-s  |  SPD: %.0f km/h", speed)
            }
        }
    }

    fun stop() {
        if (!isShowing) return
        overlayJob?.cancel()
        overlayJob = null
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            android.util.Log.e("OverlayController", "Failed to remove overlay view", e)
        }
        overlayView = null
        speedTextView = null
        isShowing = false
    }
}
