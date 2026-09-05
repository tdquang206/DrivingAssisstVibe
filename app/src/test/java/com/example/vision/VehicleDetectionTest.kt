package com.example.vision

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VehicleDetectionTest {

    @Test
    fun testNormalizedRectProperties() {
        val rect = NormalizedRect(
            left = 0.2f,
            top = 0.3f,
            right = 0.6f,
            bottom = 0.8f
        )

        assertEquals(0.4f, rect.centerX, 0.001f)
        assertEquals(0.55f, rect.centerY, 0.001f)
        assertEquals(0.8f, rect.bottomY, 0.001f)
        assertEquals(0.4f, rect.width, 0.001f)
        assertEquals(0.5f, rect.height, 0.001f)
        assertEquals(0.2f, rect.area, 0.001f)
    }

    @Test
    fun testPreprocessorCoordinateMapping() {
        val preprocessor = VehicleDetectionPreprocessor(inputWidth = 640, inputHeight = 640)

        // Simulate a 16:9 input frame (1920 x 1080)
        // Scale = 640 / 1920 = 0.333333f
        // Scaled height = 1080 * (640/1920) = 360px
        // padX = 0, padY = (640 - 360) / 2 = 140px
        val dummyBuffer = java.nio.ByteBuffer.allocateDirect(10)
        val frame = VehicleDetectionPreprocessor.PreprocessedFrame(
            inputBuffer = dummyBuffer,
            originalWidth = 1920,
            originalHeight = 1080,
            scale = 640f / 1920f,
            padX = 0f,
            padY = 140f
        )

        // Model coordinates are normalized to the 640x640 letterboxed input.
        // In pixels: cx = 320, cy = 320, w = 160, h = 90.
        val normRect = preprocessor.mapToNormalizedRect(
            cx = 0.5f,
            cy = 0.5f,
            w = 0.25f,
            h = 90f / 640f,
            frame = frame
        )

        // In original 1920x1080 image, this should be exactly at center:
        // cx_orig = 320 / (640/1920) = 960 -> 960 / 1920 = 0.5f
        // cy_orig = (320 - 140) / (640/1920) = 180 / (1/3) = 540 -> 540 / 1080 = 0.5f
        assertEquals(0.5f, normRect.centerX, 0.005f)
        assertEquals(0.5f, normRect.centerY, 0.005f)
        assertEquals(0.25f, normRect.width, 0.005f)
        assertEquals(0.25f, normRect.height, 0.005f)
    }

    @Test
    fun testS24NormalizedBoxSurvivesSizeFilter() {
        val preprocessor = VehicleDetectionPreprocessor()
        // Actual S24 diagnostic: 90-degree rotation already applied by preprocessing.
        val frame = VehicleDetectionPreprocessor.PreprocessedFrame(
            inputBuffer = java.nio.ByteBuffer.allocateDirect(0),
            originalWidth = 480,
            originalHeight = 640,
            scale = 1f,
            padX = 80f,
            padY = 0f
        )
        val rect = preprocessor.mapToNormalizedRect(
            cx = 0.4763185f,
            cy = 0.682813f,
            w = 0.09989901f,
            h = 0.07047339f,
            frame = frame
        )

        assertEquals(0.4018253f, rect.left, 0.00001f)
        assertEquals(0.6475763f, rect.top, 0.00001f)
        assertEquals(0.5350240f, rect.right, 0.00001f)
        assertEquals(0.7180497f, rect.bottom, 0.00001f)
        assertTrue(rect.width > 0.01f)
        assertTrue(rect.height > 0.01f)
        assertTrue(rect.area >= 0.0005f)
    }

    @Test
    fun testLatestS24LogUsesInputPixelsBeforeRemovingPadding() {
        val preprocessor = VehicleDetectionPreprocessor()
        val frame = VehicleDetectionPreprocessor.PreprocessedFrame(
            inputBuffer = java.nio.ByteBuffer.allocateDirect(0),
            originalWidth = 480, originalHeight = 640,
            scale = 1f, padX = 80f, padY = 0f
        )
        val rect = preprocessor.mapToNormalizedRect(
            cx = 0.5016252f, cy = 0.36920905f,
            w = 0.09093833f, h = 0.05980053f, frame = frame
        )
        assertEquals(0.44154138f, rect.left, 0.00001f)
        assertEquals(0.33930879f, rect.top, 0.00001f)
        assertEquals(0.56279249f, rect.right, 0.00001f)
        assertEquals(0.39910931f, rect.bottom, 0.00001f)
        assertTrue(rect.width > 0.01f && rect.height > 0.01f && rect.area >= 0.0005f)
    }

    @Test
    fun testVehicleTypeEnumClasses() {
        // Ensure all required vehicle types for Phase 1 are present
        val types = VehicleType.values()
        assertTrue(types.contains(VehicleType.CAR))
        assertTrue(types.contains(VehicleType.TRUCK))
        assertTrue(types.contains(VehicleType.BUS))
        assertTrue(types.contains(VehicleType.MOTORCYCLE))
    }
}
