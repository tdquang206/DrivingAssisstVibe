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

        // Center of the active image area: cx = 320, cy = 140 + 180 = 320, w = 160, h = 90
        val normRect = preprocessor.mapToNormalizedRect(
            cx = 320f,
            cy = 320f,
            w = 160f,
            h = 90f,
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
    fun testVehicleTypeEnumClasses() {
        // Ensure all required vehicle types for Phase 1 are present
        val types = VehicleType.values()
        assertTrue(types.contains(VehicleType.CAR))
        assertTrue(types.contains(VehicleType.TRUCK))
        assertTrue(types.contains(VehicleType.BUS))
        assertTrue(types.contains(VehicleType.MOTORCYCLE))
    }
}
