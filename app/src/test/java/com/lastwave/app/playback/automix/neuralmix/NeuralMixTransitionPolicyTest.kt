package com.lastwave.app.playback.automix.neuralmix

import org.junit.Assert.assertEquals
import org.junit.Test

class NeuralMixTransitionPolicyTest {
    private fun score(value: Double, coverage: Double = 0.9, rhythm: Double = 0.8) =
        CompatibilityScore(value, coverage, mapOf("rhythm" to rhythm))

    @Test fun conservativeFallback() {
        assertEquals(0, NeuralMixTransitionPolicy.maxBars(null))
        assertEquals(0, NeuralMixTransitionPolicy.maxBars(score(54.0)))
        assertEquals(0, NeuralMixTransitionPolicy.maxBars(score(90.0, 0.4)))
        assertEquals(0, NeuralMixTransitionPolicy.maxBars(score(90.0, rhythm = 0.4)))
        assertEquals(0, NeuralMixTransitionPolicy.maxBars(score(Double.NaN)))
    }

    @Test fun phraseLimits() {
        assertEquals(4, NeuralMixTransitionPolicy.maxBars(score(55.0)))
        assertEquals(16, NeuralMixTransitionPolicy.maxBars(score(80.0)))
    }
}
