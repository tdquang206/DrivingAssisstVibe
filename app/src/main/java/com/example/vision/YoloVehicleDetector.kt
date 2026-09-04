package com.example.vision

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

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
            val options = Interpreter.Options().apply {
                setNumThreads(numThreads)
                useXNNPACK = true
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "YOLO detector initialized successfully with $modelPath (threads=$numThreads)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load YOLO model: $modelPath", e)
        }
    }

    private fun loadModelFile(context: Context, modelFileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    @Synchronized
    override fun detect(bitmap: Bitmap, rotationDegrees: Int): DetectionFrame {
        val interp = interpreter ?: return DetectionFrame(System.currentTimeMillis(), emptyList(), 0L)

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

            if (maxScore >= minConfidence) {
                val cx = predictions[0][i]
                val cy = predictions[1][i]
                val w = predictions[2][i]
                val h = predictions[3][i]

                val rect = preprocessor.mapToNormalizedRect(cx, cy, w, h, preprocessed)
                if (rect.area >= minNormalizedArea && rect.width > 0.01f && rect.height > 0.01f) {
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
        val t3 = SystemClock.elapsedRealtime()
        latestPostprocessTimeMs = t3 - t2

        return DetectionFrame(
            timestampMs = System.currentTimeMillis(),
            vehicles = nmsVehicles,
            inferenceTimeMs = latestInferenceTimeMs
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

    override fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        private const val TAG = "YoloVehicleDetector"
    }
}
