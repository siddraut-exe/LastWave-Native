package com.lastwave.app.playback.automix.neuralmix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeuralQueueOptimizerTest {
    @Test fun plansPairwiseChainAndPreservesRemainingOrder() {
        val chain = NeuralQueueOptimizer.chain(listOf(50.0, 30.0, 95.0, 70.0, null, null)) { a, b ->
            when (a to b) { 2 to 3 -> 90.0; 3 to 0 -> 85.0; else -> null }
        }
        assertEquals(listOf(2, 3, 0), chain)
        val order = (2..7).toMutableList()
        NeuralQueueOptimizer.moves(order.size, chain).forEach { (from, to) -> order.add(to, order.removeAt(from)) }
        assertEquals(listOf(1, 4, 5, 2, 3, 6, 7), listOf(1) + order)
    }
    @Test fun missingPairwiseEvidenceStopsChain() {
        assertEquals(listOf(1), NeuralQueueOptimizer.chain(listOf(45.0, 85.0, 60.0)) { _, _ -> null })
    }
    @Test fun noCredibleMatchDoesNotChangeQueue() {
        assertEquals(emptyList<Int>(), NeuralQueueOptimizer.chain(listOf(null, 20.0)) { _, _ -> 90.0 })
    }
    @Test fun conflictingRenditionIsNotReused() {
        val registry = NeuralRenditionRegistry<String>({ it })
        registry.observe("placeholder", "A")
        assertEquals("A", registry.get("placeholder"))
        registry.observe("placeholder", "B")
        assertNull(registry.get("placeholder"))
        registry.observe("placeholder", "B")
        assertNull(registry.get("placeholder"))
        registry.clear()
        registry.observe("placeholder", "B")
        assertEquals("B", registry.get("placeholder"))
    }
}
