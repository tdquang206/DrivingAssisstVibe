package com.example.vision

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.DataType
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.Locale

class YoloVehicleDetector(
    context: Context,
    modelPath: String = "yolov8n.tflite",
    var minConfidence: Float = 0.40f,
    var minNormalizedArea: Float = 0.0005f,
    var nmsIoUThreshold: Float = 0.45f,
    numThreads: Int = 4
) : VehicleDetector {

    private val preprocessor = VehicleDetectionPreprocessor(inputWidth = 640, inputHeight = 640)
    private var interpreter: Interpreter? = null
    private var lastBoxDiagnosticTimeMs = -2000L
    var initializationError: String? = null
        private set
    var modelIdentity: String = "Not loaded"
        private set

    // Output shape for YOLOv8n: [1, 84, 8400]
    private val outputBuffer = Array(1) { Array(84) { FloatArray(8400) } }

    var latestPreprocessTimeMs: Long = 0L
        private set
    var latestInferenceTimeMs: Long = 0L
        private set
    var latestPostprocessTimeMs: Long = 0L
        private set

    init {
        try {
            val modelBuffer = loadModelFile(context, modelPath)
            val digest = MessageDigest.getInstance("SHA-256").apply { update(modelBuffer.duplicate()) }
            modelIdentity = digest.digest().joinToString("") { "%02x".format(it) }.take(12)
            val options = Interpreter.Options().apply {
                setNumThreads(numThreads)
                useXNNPACK = true
            }
            interpreter = Interpreter(modelBuffer, options)

            val inputTensor = interpreter!!.getInputTensor(0)
            val outputTensor = interpreter!!.getOutputTensor(0)

            Log.d(TAG, "YOLO input shape=${inputTensor.shape().contentToString()}")
            Log.d(TAG, "YOLO input type=${inputTensor.dataType()}")

            Log.d(TAG, "YOLO output shape=${outputTensor.shape().contentToString()}")
            Log.d(TAG, "YOLO output type=${outputTensor.dataType()}")

            require(inputTensor.shape().contentEquals(intArrayOf(1, 640, 640, 3)) &&
                inputTensor.dataType() == DataType.FLOAT32) {
                "Unsupported input: ${inputTensor.shape().contentToString()} ${inputTensor.dataType()}"
            }
            require(outputTensor.shape().contentEquals(intArrayOf(1, 84, 8400)) &&
                outputTensor.dataType() == DataType.FLOAT32) {
                "Unsupported output: ${outputTensor.shape().contentToString()} ${outputTensor.dataType()}"
            }

            Log.d(TAG, "YOLO detector $DIAGNOSTIC_REVISION initialized with $modelPath sha256=$modelIdentity (threads=$numThreads)")
        } catch (e: Exception) {
            interpreter?.close()
            interpreter = null
            initializationError = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "Failed to load YOLO model: $modelPath", e)
        }
    }

    private fun loadModelFile(context: Context, modelFileName: String): MappedByteBuffer {
        return context.assets.openFd(modelFileName).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
            }
        }
    }

    @Synchronized
    override fun detect(bitmap: Bitmap, rotationDegrees: Int): DetectionFrame {
        val interp = checkNotNull(interpreter) { initializationError ?: "Detector is closed" }

        // 1. Preprocessing
        val t0 = SystemClock.elapsedRealtime()
        val preprocessed = preprocessor.preprocess(bitmap, rotationDegrees)
        val t1 = SystemClock.elapsedRealtime()
        latestPreprocessTimeMs = t1 - t0

        // 2. Inference
        interp.run(preprocessed.inputBuffer, outputBuffer)
        val t2 = SystemClock.elapsedRealtime()
        latestInferenceTimeMs = t2 - t1

        // 3. Postprocessing
        val candidateList = mutableListOf<DetectedVehicle>()
        val predictions = outputBuffer[0] // 84 rows x 8400 columns
        var highestScoreInFrame = 0f
        var rawCandidateCount = 0
        var aboveThresholdCount = 0
        var validBoxCount = 0
        var bestCandidateIndex = -1
        var bestVehicleType = VehicleType.UNKNOWN

        for (i in 0 until 8400) {
            // Vehicle class scores in COCO (rows 4+cls)
            val carScore = predictions[6][i]
            val motoScore = predictions[7][i]
            val busScore = predictions[9][i]
            val truckScore = predictions[11][i]

            var maxScore = carScore
            var vehicleType = VehicleType.CAR

            if (motoScore > maxScore) {
                maxScore = motoScore
                vehicleType = VehicleType.MOTORCYCLE
            }
            if (busScore > maxScore) {
                maxScore = busScore
                vehicleType = VehicleType.BUS
            }
            if (truckScore > maxScore) {
                maxScore = truckScore
                vehicleType = VehicleType.TRUCK
            }

            if (maxScore > highestScoreInFrame) {
                highestScoreInFrame = maxScore
                bestCandidateIndex = i
                bestVehicleType = vehicleType
            }

            if (maxScore >= 0.05f) {
                rawCandidateCount++
            }

            if (maxScore >= minConfidence) {
                aboveThresholdCount++
                val cx = predictions[0][i]
                val cy = predictions[1][i]
                val w = predictions[2][i]
                val h = predictions[3][i]

                val rect = preprocessor.mapToNormalizedRect(cx, cy, w, h, preprocessed)
                if (rect.area >= minNormalizedArea && rect.width > 0.01f && rect.height > 0.01f) {
                    validBoxCount++
                    candidateList.add(
                        DetectedVehicle(
                            trackingId = null,
                            type = vehicleType,
                            confidence = maxScore,
                            boundingBox = rect
                        )
                    )
                }
            }
        }

        // Apply Non-Maximum Suppression (NMS)
        val nmsVehicles = applyNms(candidateList, nmsIoUThreshold)
        // Diagnose the strongest vehicle score, including frames where none passes confidence.
        val boxSummary = if (bestCandidateIndex >= 0) {
            val i = bestCandidateIndex
            val cx = predictions[0][i]
            val cy = predictions[1][i]
            val w = predictions[2][i]
            val h = predictions[3][i]
            val rect = preprocessor.mapToNormalizedRect(cx, cy, w, h, preprocessed)
            val result = when {
                highestScoreInFrame < minConfidence -> "below confidence threshold"
                !rect.area.isFinite() -> "non-finite coordinates"
                rect.width <= 0f || rect.height <= 0f -> "empty after clipping"
                rect.width <= 0.01f || rect.height <= 0.01f -> "width/height too small"
                rect.area < minNormalizedArea -> "area too small"
                else -> "passed size filter"
            }
            val summary = String.format(Locale.US,
                "%s raw xywh: %.4f, %.4f, %.4f, %.4f\nMapped w/h/area: %.4f / %.4f / %.6f\nBox check: %s",
                bestVehicleType, cx, cy, w, h, rect.width, rect.height, rect.area, result)
            if (t2 - lastBoxDiagnosticTimeMs >= 2000L) {
                Log.d(TAG, "YOLO box diagnostic: revision=$DIAGNOSTIC_REVISION class=$bestVehicleType " +
                    "confidence=$highestScoreInFrame rawCxCyWh=[$cx, $cy, $w, $h] " +
                    "rotation=$rotationDegrees oriented=${preprocessed.originalWidth}x${preprocessed.originalHeight} " +
                    "scale=${preprocessed.scale} padX=${preprocessed.padX} padY=${preprocessed.padY} " +
                    "mappedLTRB=[${rect.left}, ${rect.top}, ${rect.right}, ${rect.bottom}] " +
                    "mappedWidth=${rect.width} mappedHeight=${rect.height} area=${rect.area} result=$result")
                lastBoxDiagnosticTimeMs = t2
            }
            summary
        } else {
            "No positive finite vehicle scores"
        }
        val t3 = SystemClock.elapsedRealtime()
        latestPostprocessTimeMs = t3 - t2
        
        Log.d(
            TAG,
            "Max vehicle conf: ${"%.2f".format(highestScoreInFrame)}, Vehicle raw: $rawCandidateCount, Above ${"%.2f".format(minConfidence)}: $aboveThresholdCount, Valid boxes: $validBoxCount, After NMS: ${nmsVehicles.size}"
        )

        val diagnostics = DiagnosticMetrics(
            maxVehicleConf = highestScoreInFrame,
            rawCount = rawCandidateCount,
            aboveConfCount = aboveThresholdCount,
            validBoxCount = validBoxCount,
            afterNmsCount = nmsVehicles.size,
            boxSummary = boxSummary
        )

        return DetectionFrame(
            timestampMs = System.currentTimeMillis(),
            vehicles = nmsVehicles,
            inferenceTimeMs = latestInferenceTimeMs,
            diagnostics = diagnostics
        )
    }

    private fun applyNms(
        candidates: List<DetectedVehicle>,
        iouThreshold: Float
    ): List<DetectedVehicle> {
        if (candidates.isEmpty()) return emptyList()

        val sorted = candidates.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<DetectedVehicle>()

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            selected.add(best)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                if (calculateIoU(best.boundingBox, other.boundingBox) > iouThreshold) {
                    iterator.remove()
                }
            }
        }

        return selected
    }

    private fun calculateIoU(a: NormalizedRect, b: NormalizedRect): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        val interWidth = maxOf(0f, interRight - interLeft)
        val interHeight = maxOf(0f, interBottom - interTop)
        val interArea = interWidth * interHeight

        val unionArea = a.area + b.area - interArea
        return if (unionArea <= 0f) 0f else interArea / unionArea
    }

    @Synchronized
    override fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        const val DIAGNOSTIC_REVISION = "normalized-boxes-v2"
        private const val TAG = "YoloVehicleDetector"
    }
}
