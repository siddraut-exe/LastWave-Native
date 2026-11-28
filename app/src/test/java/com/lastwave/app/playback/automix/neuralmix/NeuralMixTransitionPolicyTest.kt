package com.lastwave.app.playback.automix.neuralmix

import org.junit.Assert.*
import org.junit.Test

class NeuralMixTransitionPolicyTest {
    @Test fun independentExecutionLimits() {
        assertEquals(0, NeuralMixTransitionPolicy.maxBars(null))
        assertEquals(0, NeuralMixTransitionPolicy.maxBars(NeuralMixRanker.score(0.0, 1.0, 1.0)))
        assertEquals(0, NeuralMixTransitionPolicy.maxBars(NeuralMixRanker.score(1.0, 1.0, null)))
        assertEquals(8, NeuralMixTransitionPolicy.maxBars(NeuralMixRanker.score(1.0, 1.0, 1.0)))
        assertEquals(2, NeuralMixTransitionPolicy.maxBars(NeuralMixRanker.score(1.0, 0.0, 1.0)))
        assertEquals(1, NeuralMixTransitionPolicy.maxBars(NeuralMixRanker.score(1.0, 1.0, 0.25)))
    }
}
