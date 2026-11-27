package com.lastwave.app.playback.automix.neuralmix

import kotlin.math.sqrt
import kotlin.math.floor

/** Compare actual low-band envelopes at candidate cues, without shifting the beat grid. */
object CueRhythm {
    fun compare(a: List<Int>, aStart: Long, aCue: Double, b: List<Int>, bStart: Long, bCue: Double, ratio: Double, span: Double): Double? {
        if (!aCue.isFinite() || !bCue.isFinite() || !ratio.isFinite() || ratio <= 0 || !span.isFinite() || span < 1000) return null
        val count = (span / 100).toInt().coerceAtMost(80)
        val x = ArrayList<Double>(); val y = ArrayList<Double>()
        for (i in 0 until count) {
            val ai = floor((aCue - aStart + i * 100) / 100).toInt()
            val bi = floor((bCue - bStart + i * 100 * ratio) / 100).toInt()
            if (ai !in a.indices || bi !in b.indices) continue
            x.add(((a[ai] ushr 16) and 255).toDouble())
            y.add(((b[bi] ushr 16) and 255).toDouble())
        }
        if (x.size < 10) return null
        val xm = x.average(); val ym = y.average()
        val xx = x.sumOf { (it-xm)*(it-xm) }; val yy = y.sumOf { (it-ym)*(it-ym) }
        if (xx < 1 || yy < 1) return null
        return (x.indices.sumOf { (x[it]-xm)*(y[it]-ym) } / sqrt(xx*yy)).coerceIn(-1.0, 1.0)
    }
}
