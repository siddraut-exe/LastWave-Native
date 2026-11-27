package com.lastwave.app.playback.automix.neuralmix

/** Immutable snapshot of the real DJ scan, separate from the high-frequency playhead. */
data class NeuralQueueState(
    val currentKey: String? = null,
    val stage: String = "Waiting for playback",
    val examined: Int = 0,
    val total: Int = 0,
    val originalPositions: Map<String, Int> = emptyMap(),
    val priorityKeys: List<String> = emptyList(),
    val committed: Boolean = false,
    val previewDepth: Int = 3,
)

/** First edge uses the live outgoing track. Subsequent edges require pairwise evidence. */
object NeuralQueueOptimizer {
    fun chain(firstScores: List<Double?>, depth: Int = 3, edge: (Int, Int) -> Double?): List<Int> {
        val first = NeuralDjSchedule.winner(firstScores) ?: return emptyList()
        if (depth <= 0) return emptyList()
        val result = mutableListOf(first)
        while (result.size < depth) {
            val previous = result.last()
            val next = firstScores.indices.asSequence().filter { it !in result }
                .mapNotNull { index -> edge(previous, index)?.takeIf { it.isFinite() && it >= 40.0 }?.let { index to it } }
                .maxByOrNull { it.second }?.first ?: break
            result += next
        }
        return result
    }

    /** Move only selected future items, preserving relative order of unselected entries. */
    fun moves(size: Int, chain: List<Int>): List<Pair<Int, Int>> {
        require(size >= 0 && chain.distinct().size == chain.size && chain.all { it in 0 until size })
        val order = (0 until size).toMutableList()
        return buildList {
            chain.forEachIndexed { target, candidate ->
                val from = order.indexOf(candidate)
                if (from != target) {
                    add(from to target)
                    order.add(target, order.removeAt(from))
                }
            }
        }
    }
}
