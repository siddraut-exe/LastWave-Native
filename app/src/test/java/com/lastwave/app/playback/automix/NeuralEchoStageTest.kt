package com.lastwave.app.playback.automix

import com.lastwave.app.playback.automix.engine.NeuralEchoStage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class NeuralEchoStageTest {
    private fun samples(count: Int, impulse: Boolean): ByteBuffer = ByteBuffer.allocate(count * 4).order(ByteOrder.nativeOrder()).apply {
        asFloatBuffer().put(FloatArray(count) { if (impulse && it == 0) 0.8f else 0f })
    }
    @Test fun actualDelayedImpulseAndNoLeakAfterClear() {
        val rate = 8_000
        val echo = NeuralEchoStage()
        echo.schedule(0, 1_000_000, 250_000)
        val output = samples(8_000, true)
        echo.process(output, 1, rate, 0L)
        val floats = output.asFloatBuffer()
        assertEquals(0.8f, floats.get(0), 1e-6f)
        assertTrue(floats.get(2_000) > 0.1f)
        assertTrue(floats.get(4_000) > 0f)
        assertTrue(floats.get(4_000) < floats.get(2_000))
        echo.clear()
        val silent = samples(8_000, false)
        echo.process(silent, 1, rate, 1_000_000L)
        assertTrue((0 until 8_000).all { silent.asFloatBuffer().get(it) == 0f })
    }
}
