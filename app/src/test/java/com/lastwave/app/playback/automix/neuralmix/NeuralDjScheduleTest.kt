package com.lastwave.app.playback.automix.neuralmix

import org.junit.Assert.*
import org.junit.Test

class NeuralDjScheduleTest {
    @Test fun midpointAndPreparationDeadline() {
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
    @Test fun keepsQueueOnUnknownOrTiedEvidence() {
        assertNull(NeuralDjSchedule.winner(listOf(null, Double.NaN, 0.0)))
        assertEquals(0, NeuralDjSchedule.winner(listOf(80.0, 80.0)))
    }
}
