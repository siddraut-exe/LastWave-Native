package com.lastwave.app.playback.automix.neuralmix

/** All times are media milliseconds. No queue mutation inside the transition preparation window. */
object NeuralDjSchedule {
    const val LOCK_REMAINING_MS = 45_000L
    fun canStart(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= durationMs / 2 && durationMs - positionMs > LOCK_REMAINING_MS + 5_000L

    // Resolve and analyse ahead of the midpoint; never mutate the queue before it.
    fun canPrepare(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= durationMs / 4 && durationMs - positionMs > LOCK_REMAINING_MS + 5_000L

    fun canContinue(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs in 0 until durationMs && durationMs - positionMs > LOCK_REMAINING_MS

    fun canCommit(positionMs: Long, durationMs: Long): Boolean =
        canContinue(positionMs, durationMs) && positionMs >= durationMs / 2

    /** Beat-lock is preferred, but a well-evidenced smoother fade can also win. */
    fun rank(score: CompatibilityScore?, beatLocked: Boolean): Double? {
        if (score == null || !score.score.isFinite() || !score.coverage.isFinite() || score.coverage < 0.5) return null
        val rhythm = score.components["rhythm"] ?: return null
        if (!rhythm.isFinite()) return null
        val weighted = score.score.coerceIn(0.0, 100.0) * score.coverage.coerceIn(0.0, 1.0)
        return when {
            beatLocked -> 100.0 + weighted
            rhythm < 0.35 -> minOf(weighted, 39.0) // weak rhythm must not win a promotion
            else -> weighted
        }
    }

    /** Avoid pointless queue churn: only promote a credible, materially better alternative. */
    fun winner(scores: List<Double?>): Int? {
        val best = scores.withIndex().filter { it.value?.isFinite() == true && it.value!! >= 40.0 }
            .maxByOrNull { it.value!! } ?: return null
        val first = scores.firstOrNull()?.takeIf { it.isFinite() }
        if (best.index != 0 && first != null && best.value!! < first + 5.0) return 0
        return best.index
    }
}
