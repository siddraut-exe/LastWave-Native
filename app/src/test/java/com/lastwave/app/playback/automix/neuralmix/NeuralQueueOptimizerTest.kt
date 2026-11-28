package com.lastwave.app.playback.automix.neuralmix

import org.junit.Assert.*
import org.junit.Test

class NeuralQueueOptimizerTest {
    @Test fun ranksLowScoresAndRetainsAllItems() {
        val order = NeuralQueueOptimizer.prioritize(listOf(22.0, 22.0, 24.0, 22.0, null, 0.0))
        assertEquals(listOf(2, 0, 1, 3, 5), order)
        val queue = (1..6).toMutableList()
        NeuralQueueOptimizer.moves(queue.size, order).forEach { (from, to) -> queue.add(to, queue.removeAt(from)) }
        assertEquals(listOf(3, 1, 2, 4, 6, 5), queue)
        assertEquals((1..6).toSet(), queue.toSet())
    }
    @Test fun bassCalledOnlyForTies() {
        val calls = mutableListOf<Int>()
        val order = NeuralQueueOptimizer.prioritize(listOf(87.4, 87.4, 72.6, 63.2)) { i -> calls += i; if (i == 1) 0.96 else 0.72 }
        assertEquals(listOf(1, 0, 2, 3), order)
        assertEquals(listOf(0, 1), calls)
    }
    @Test fun unknownBassKeepsStableOrder() {
        assertEquals(listOf(0, 1), NeuralQueueOptimizer.prioritize(listOf(24.0, 24.0)) { if (it == 0) null else 0.9 })
    }
    @Test fun nextCycleCanPromotePreviouslyLowScore() {
        assertEquals(listOf(2, 1, 0), NeuralQueueOptimizer.prioritize(listOf(22.0, 23.0, 24.0)))
        assertEquals(listOf(0, 1), NeuralQueueOptimizer.prioritize(listOf(91.0, 65.0)))
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
