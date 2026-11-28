package com.lastwave.app.playback.automix.neuralmix

import kotlin.math.abs

/** Points are earned, not invented: missing features do not inflate a partial score. */
data class CompatibilityScore(val score: Double, val coverage: Double, val components: Map<String, Double>)

object NeuralMixRanker {
    fun tempo(a: Double?, b: Double?): Double? {
        if (a == null || b == null || !a.isFinite() || !b.isFinite() || a <= 0 || b <= 0) return null
        val diff = listOf(0.5, 1.0, 2.0).minOf { abs(a - b * it) }
        return when {
            diff <= 4 -> 1.0
            diff <= 8 -> 1.0 - (diff - 4) / 8
            else -> (0.5 - (diff - 8) / 8).coerceAtLeast(0.0)
        }
    }

    fun harmony(a: String?, b: String?): Double? {
        val pattern = Regex("(1[0-2]|[1-9])([AB])")
        val x = pattern.matchEntire(a ?: return null) ?: return null
        val y = pattern.matchEntire(b ?: return null) ?: return null
        val d = abs(x.groupValues[1].toInt() - y.groupValues[1].toInt()).let { minOf(it, 12 - it) }
        val sameMode = x.groupValues[2] == y.groupValues[2]
        return when {
            d == 0 && sameMode -> 1.0
            d == 0 -> 0.95
            sameMode && d == 1 -> 0.9
            sameMode && d == 2 -> 0.4
            else -> 0.0
        }
    }

    /** No rhythm, genre, energy, texture, preference or bass contribution. */
    fun score(tempo: Double?, harmony: Double?, phrasing: Double?): CompatibilityScore? {
        val parts = linkedMapOf<String, Double>()
        var coverage = 0.0
        var points = 0.0
        fun add(name: String, value: Double?, weight: Double) {
            if (value == null || !value.isFinite()) return
            val v = value.coerceIn(0.0, 1.0)
            parts[name] = v
            coverage += weight
            points += weight * v
        }
        add("tempo", tempo, 40.0)
        add("harmonic", harmony, 40.0)
        add("phrasing", phrasing, 20.0)
        return if (coverage == 0.0) null else CompatibilityScore(points, coverage / 100.0, parts)
    }
}
