package com.lastwave.app.playback.automix.neuralmix

/** Conservative experimental limits; the original planner still validates every cue. */
object NeuralMixTransitionPolicy {
    fun maxBars(score: CompatibilityScore?): Int {
        if (score == null || !score.score.isFinite() || !score.coverage.isFinite()) return 0
        val rhythm = score.components["rhythm"] ?: return 0
        if (!rhythm.isFinite() || score.coverage < 0.5 || score.score < 55 || rhythm < 0.5) return 0
        return if (score.score >= 80) 16 else 4
    }
}
