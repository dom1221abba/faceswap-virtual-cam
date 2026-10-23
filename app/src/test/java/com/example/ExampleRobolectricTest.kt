package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.processor.VirtualFrameProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("FaceSwap Virtual Cam", appName)
    }

    @Test
    fun `virtual frame processor encodes NV21 buffer correctly`() {
        val processor = VirtualFrameProcessor()
        val width = 64
        val height = 48
        val expectedSize = width * height * 3 / 2
        val nv21 = ByteArray(expectedSize)

        // Create a solid red bitmap
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, Color.RED)
            }
        }

        val latencyMs = processor.processFrame(bmp, nv21, width, height)
        assertTrue(latencyMs >= 0)

        // For pure red (R=255, G=0, B=0) in ITU-R BT.601:
        // Y = 0.299 * 255 = ~76 (or (77 * 255 + 128) >> 8 = 76)
        val yVal = nv21[0].toInt() and 0xFF
        assertTrue("Y value should be around 76 for pure red, got $yVal", yVal in 70..85)

        // V (Cr) = 0.5 * 255 + 128 = ~255 (or (128 * 255 + 128) >> 8 + 128 = ~255)
        // In NV21, Chroma starts at width * height, V is at offset 0
        val vVal = nv21[width * height].toInt() and 0xFF
        assertTrue("V value should be high (~240-255) for pure red, got $vVal", vVal in 230..255)
    }
}
