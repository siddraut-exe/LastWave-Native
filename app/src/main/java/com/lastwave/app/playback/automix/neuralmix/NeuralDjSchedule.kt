package com.lastwave.app.playback.automix.neuralmix

/** All times are media milliseconds. No queue mutation inside the transition preparation window. */
object NeuralDjSchedule {
    const val LOCK_REMAINING_MS = 45_000L
    fun canStart(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= durationMs / 2 && durationMs - positionMs > LOCK_REMAINING_MS + 5_000L

    fun canCommit(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs in 0 until durationMs && durationMs - positionMs > LOCK_REMAINING_MS

    /** Only promote an actual feasible candidate; ties keep the original queue order. */
    fun winner(scores: List<Double?>): Int? = scores.withIndex()
        .filter { it.value?.isFinite() == true && it.value!! > 0.0 }
        .maxByOrNull { it.value!! }?.index
}
