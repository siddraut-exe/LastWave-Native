package com.lastwave.app.playback.automix.neuralmix

import org.junit.Assert.*
import org.junit.Test

class NeuralDjScheduleTest {
    @Test fun midpointAndPreparationDeadline() {
        assertFalse(NeuralDjSchedule.canPrepare(59_999, 240_000))
        assertTrue(NeuralDjSchedule.canPrepare(60_000, 240_000))
        assertTrue(NeuralDjSchedule.canContinue(60_000, 240_000))
        assertFalse(NeuralDjSchedule.canCommit(119_999, 240_000))
        assertFalse(NeuralDjSchedule.canStart(119_999, 240_000))
        assertTrue(NeuralDjSchedule.canStart(120_000, 240_000))
        assertFalse(NeuralDjSchedule.canStart(195_000, 240_000))
        assertTrue(NeuralDjSchedule.canCommit(194_999, 240_000))
        assertFalse(NeuralDjSchedule.canCommit(195_000, 240_000))
        assertFalse(NeuralDjSchedule.canStart(0, -1))
    }
    @Test fun promotesThirdTrackIfItIsTheOnlyFeasibleCandidate() {
        assertEquals(1, NeuralDjSchedule.winner(listOf(null, 84.0, null)))
    }
    @Test fun betterFadeCanWinWithoutForcingBeatLock() {
        val weak = CompatibilityScore(24.0, 0.9, mapOf("rhythm" to 0.21))
        val better = CompatibilityScore(72.0, 0.9, mapOf("rhythm" to 0.8))
        val scores = listOf(NeuralDjSchedule.rank(weak, false), NeuralDjSchedule.rank(better, false))
        assertEquals(1, NeuralDjSchedule.winner(scores))
        assertEquals(0, NeuralDjSchedule.winner(listOf(NeuralDjSchedule.rank(better, true), scores[1])))
        assertNull(NeuralDjSchedule.rank(CompatibilityScore(99.0, 0.2, mapOf("rhythm" to 1.0)), false))
        assertEquals(0, NeuralDjSchedule.winner(listOf(60.0, 63.0)))
        assertNull(NeuralDjSchedule.winner(listOf(21.6, 30.0)))
        assertNull(NeuralDjSchedule.winner(listOf(null, NeuralDjSchedule.rank(CompatibilityScore(90.0, 0.9, mapOf("rhythm" to 0.1)), false))))
    }
    @Test fun keepsQueueOnUnknownOrTiedEvidence() {
        assertNull(NeuralDjSchedule.winner(listOf(null, Double.NaN, 0.0)))
        assertEquals(0, NeuralDjSchedule.winner(listOf(80.0, 80.0)))
    }
}
