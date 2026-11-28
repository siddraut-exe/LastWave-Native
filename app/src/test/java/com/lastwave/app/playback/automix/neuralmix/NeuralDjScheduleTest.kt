package com.lastwave.app.playback.automix.neuralmix

import org.junit.Assert.*
import org.junit.Test

class NeuralDjScheduleTest {
    @Test fun seeksUseMediaPositionAndDeadline() {
        assertFalse(NeuralDjSchedule.canPrepare(10_000, 240_000))
        assertTrue(NeuralDjSchedule.canPrepare(84_000, 240_000))
        assertTrue(NeuralDjSchedule.canContinue(84_000, 240_000))
        assertFalse(NeuralDjSchedule.canCommit(119_999, 240_000))
        assertTrue(NeuralDjSchedule.canCommit(120_000, 240_000))
        assertFalse(NeuralDjSchedule.canCommit(225_000, 240_000))
        assertTrue(NeuralDjSchedule.canPrepare(15_000, 60_000))
        assertTrue(NeuralDjSchedule.canCommit(30_000, 60_000))
        assertFalse(NeuralDjSchedule.canPrepare(0, -1))
    }
    @Test fun lowScoreStillWinsAndZeroIsMeasured() {
        assertEquals(1, NeuralDjSchedule.winner(listOf(22.0, 24.0, 22.0)))
        assertEquals(0, NeuralDjSchedule.winner(listOf(0.0, null)))
        assertNull(NeuralDjSchedule.winner(listOf(null, Double.NaN)))
        assertEquals(1, NeuralDjSchedule.winner(listOf(60.0, 63.0)))
    }
    @Test fun scoreHasNoBeatLockBonusOrEvidenceRejection() {
        assertEquals(24.0, NeuralDjSchedule.rank(CompatibilityScore(24.0, 0.4, mapOf("harmonic" to 0.6)))!!, 1e-9)
    }
}
