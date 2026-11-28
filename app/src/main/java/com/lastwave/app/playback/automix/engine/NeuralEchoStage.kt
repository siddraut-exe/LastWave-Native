package com.lastwave.app.playback.automix.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/** Per-deck, media-time PCM echo. The audible tail lives inside the real overlap. */
class NeuralEchoStage {
    private data class Command(val startUs: Long, val durationUs: Long, val delayUs: Long)
    private val command = AtomicReference<Command?>(null)
    private var seen: Command? = null
    private var ring = FloatArray(0)
    private var cursor = 0
    private var rate = 0
    private var channels = 0

    fun schedule(startUs: Long, durationUs: Long, delayUs: Long) {
        if (durationUs <= 0L) { clear(); return }
        command.set(Command(startUs, durationUs, delayUs.coerceIn(120_000L, 750_000L)))
    }
    fun clear() { command.set(null) }

    fun process(output: ByteBuffer, channelCount: Int, sampleRate: Int, mediaUs: Long) {
        val requested = command.get()
        if (requested == null) {
            if (seen != null) { seen = null; cursor = 0; ring = FloatArray(0) }
            return
        }
        if (channelCount !in 1..2 || sampleRate !in 8_000..384_000 || mediaUs < 0) return
        if (requested !== seen || rate != sampleRate || channels != channelCount) {
            seen = requested; rate = sampleRate; channels = channelCount; cursor = 0
            val frames = (requested.delayUs * sampleRate / 1_000_000.0).roundToInt().coerceAtLeast(1)
            ring = FloatArray(frames * channelCount)
        }
        val samples = output.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
        val base = samples.position()
        val frames = samples.remaining() / channelCount
        for (frame in 0 until frames) {
            val timeUs = mediaUs + frame * 1_000_000L / sampleRate
            val p = (timeUs - requested.startUs).toDouble() / requested.durationUs
            val wet = wetAt(p.toFloat())
            val feed = (1f - p.toFloat().coerceIn(0f, 1f)).coerceIn(0f, 1f)
            for (channel in 0 until channelCount) {
                val i = base + frame * channelCount + channel
                val dry = samples.get(i)
                val delayed = ring[cursor]
                ring[cursor] = (dry * feed + delayed * 0.35f).coerceIn(-1f, 1f)
                if (wet > 0f) samples.put(i, (dry * (1f - wet) + delayed * wet).coerceIn(-1f, 1f))
                cursor++
                if (cursor == ring.size) cursor = 0
            }
        }
    }

    companion object {
        /** Same wet envelope as the Studio graph. */
        fun wetAt(progress: Float): Float = ((progress - 0.08f) / 0.42f).coerceIn(0f, 1f) * 0.8f
    }
}
