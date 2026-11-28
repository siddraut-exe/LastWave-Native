package com.lastwave.app.playback.automix.neuralmix

/** Work against the media clock, not elapsed listening time. */
object NeuralDjSchedule {
    const val LOCK_REMAINING_MS = 45_000L
    fun lockRemaining(durationMs: Long): Long = (durationMs / 5).coerceIn(8_000L, LOCK_REMAINING_MS)
    private fun prepareAt(durationMs: Long): Long = minOf(durationMs / 4, 20_000L)
    fun canStart(positionMs: Long, durationMs: Long): Boolean = canCommit(positionMs, durationMs)
    fun canPrepare(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= prepareAt(durationMs) && durationMs - positionMs > lockRemaining(durationMs) + 1_000L
    fun canContinue(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs in 0 until durationMs && durationMs - positionMs > lockRemaining(durationMs)
    fun canCommit(positionMs: Long, durationMs: Long): Boolean =
        canContinue(positionMs, durationMs) && positionMs >= minOf(durationMs / 2, durationMs - lockRemaining(durationMs) - 2_000L).coerceAtLeast(0L)

    fun rank(score: CompatibilityScore?): Double? = score?.score?.takeIf { it.isFinite() }
    fun winner(scores: List<Double?>): Int? = NeuralQueueOptimizer.prioritize(scores).firstOrNull()
}
