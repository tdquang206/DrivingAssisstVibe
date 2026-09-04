package com.example.ui.debug

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.DashcamForegroundService
import com.example.vision.VehicleType
import kotlin.math.roundToInt

import androidx.activity.compose.BackHandler

@Composable
fun VehicleDebugScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val detectionState by DashcamForegroundService.detectionState.collectAsStateWithLifecycle()
    val isServiceActive = DashcamForegroundService.activeService != null

    BackHandler {
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            // Unbind surface provider when leaving screen
            DashcamForegroundService.setSurfaceProvider(null)
        }
    }

    if (!isServiceActive) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Service Not Running", color = Color.Red, fontSize = 24.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Please go back and tap 'START DRIVE' first.", color = Color.White)
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onBack) { Text("Back to Home") }
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Camera Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    
                    // Attach to the active service
                    DashcamForegroundService.setSurfaceProvider(this.surfaceProvider)
                }
            }
        )

        // Bounding Boxes Overlay
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val w = maxWidth.value
            val h = maxHeight.value

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                detectionState.vehicles.forEach { vehicle ->
                    val left = vehicle.boundingBox.left * canvasW
                    val top = vehicle.boundingBox.top * canvasH
                    val right = vehicle.boundingBox.right * canvasW
                    val bottom = vehicle.boundingBox.bottom * canvasH

                    val color = when (vehicle.type) {
                        VehicleType.CAR -> Color.Green
                        VehicleType.TRUCK -> Color.Red
                        VehicleType.BUS -> Color.Yellow
                        VehicleType.MOTORCYCLE -> Color.Magenta
                        VehicleType.UNKNOWN -> Color.Gray
                    }

                    drawRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }

            // Overlay Text
            detectionState.vehicles.forEach { vehicle ->
                val topDp = (vehicle.boundingBox.top * h).dp
                val leftDp = (vehicle.boundingBox.left * w).dp
                
                val color = when (vehicle.type) {
                    VehicleType.CAR -> Color.Green
                    VehicleType.TRUCK -> Color.Red
                    VehicleType.BUS -> Color.Yellow
                    VehicleType.MOTORCYCLE -> Color.Magenta
                    VehicleType.UNKNOWN -> Color.Gray
                }
                
                Text(
                    text = "${vehicle.type.name} ${(vehicle.confidence * 100).roundToInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .absoluteOffset(x = leftDp, y = topDp - 20.dp)
                        .background(color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f))) {
                Text("Back")
            }
        }

        // Stats Panel (Bottom)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .navigationBarsPadding()
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            val s = detectionState.stats
            Text("FPS: ${s.fps}", color = Color.White, fontSize = 14.sp)
            Text("Latest inference: ${s.latestInferenceTimeMs} ms", color = Color.White, fontSize = 14.sp)
            Text("Avg inference: ${s.avgInferenceTimeMs} ms", color = Color.White, fontSize = 14.sp)
            Text("Detections: ${s.vehicleCount}", color = Color.White, fontSize = 14.sp)
            Text("Model: ${s.modelResolution}", color = Color.White, fontSize = 14.sp)
            Text("Skipped Frames: ${s.skippedFrames}", color = Color.White, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Max vehicle conf: ${"%.2f".format(s.maxVehicleConf)}", color = Color.Yellow, fontSize = 14.sp)
            Text("Vehicle raw: ${s.rawCandidatesCount}", color = Color.White, fontSize = 14.sp)
            Text("Above ${"%.2f".format(s.minConfidence)}: ${s.aboveConfCount}", color = Color.White, fontSize = 14.sp)
            Text("Valid boxes: ${s.validBoxCount}", color = Color.White, fontSize = 14.sp)
            Text("After NMS: ${s.afterNmsCount}", color = Color.White, fontSize = 14.sp)
        }
    }
}
