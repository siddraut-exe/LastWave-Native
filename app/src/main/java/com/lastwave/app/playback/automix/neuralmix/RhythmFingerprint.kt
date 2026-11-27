package com.lastwave.app.playback.automix.neuralmix

import kotlin.math.*
import kotlinx.serialization.Serializable

/** Sixteenth-note energy and transient profiles, averaged over complete four-beat bars. */
@Serializable
data class RhythmFingerprint(val rhythm: List<Double>, val bass: List<Double>, val texture: List<Double>, val rms: Double, val bpm: Double)

object BeatSynchronousAnalyzer {
    fun analyze(pcm: FloatArray, rate: Int, startMs: Double, downbeatMs: Double, bpm: Double): RhythmFingerprint? {
        if (rate < 1000 || bpm !in 40.0..240.0 || !startMs.isFinite() || !downbeatMs.isFinite() || pcm.isEmpty()) return null
        val step = rate * 60.0 / bpm / 4
        val bar = step * 16
        val offset = (downbeatMs - startMs) * rate / 1000
        val first = offset + ceil(-offset / bar) * bar
        val count = floor((pcm.size - first) / bar).toInt().coerceAtMost(64)
        if (count < 2) return null
        val rhythm = DoubleArray(16); val bass = DoubleArray(16); val texture = DoubleArray(16)
        val alpha = 1 - exp(-2 * PI * 150 / rate)
        var low = 0.0; var prev = 0.0; var power = 0.0; var samples = 0
        for (b in 0 until count) {
            for (s in 0 until 16) {
                val from = (first + (b * 16 + s) * step).roundToInt().coerceIn(0, pcm.size)
                val to = (first + (b * 16 + s + 1) * step).roundToInt().coerceIn(from, pcm.size)
                var e = 0.0; var l = 0.0; var h = 0.0
                for (i in from until to) {
                    val x = pcm[i].toDouble(); if (!x.isFinite()) return null
                    low += alpha * (x - low)
                    e += x*x; l += low*low; h += (x-prev)*(x-prev); prev = x
                }
                val n = (to-from).coerceAtLeast(1)
                rhythm[s] += sqrt(e/n); bass[s] += sqrt(l/n); texture[s] += sqrt(h/n)
                power += e; samples += to-from
            }
        }
        val rms = sqrt(power / samples.coerceAtLeast(1))
        if (rms < 0.0001) return null
        fun normalized(a: DoubleArray): List<Double> { val max = a.maxOrNull() ?: 0.0; return a.map { if (max > 1e-9) it/max else 0.0 } }
        return RhythmFingerprint(normalized(rhythm), normalized(bass), normalized(texture), rms, bpm)
    }
}
