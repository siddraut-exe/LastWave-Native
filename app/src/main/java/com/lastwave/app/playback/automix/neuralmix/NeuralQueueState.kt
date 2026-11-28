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
    val scores: Map<String, CompatibilityScore> = emptyMap(),
    val bassTies: Map<String, Double> = emptyMap(),
)

/** Rank all measured candidates. Unknowns stay in their original relative order. */
object NeuralQueueOptimizer {
    private const val TIE_EPSILON = 0.000001

    fun prioritize(scores: List<Double?>, bass: (Int) -> Double? = { null }): List<Int> {
        val sorted = scores.indices.filter { scores[it]?.isFinite() == true }
            .sortedWith(compareByDescending<Int> { scores[it]!! }.thenBy { it })
        if (sorted.isEmpty()) return emptyList()
        val result = mutableListOf<Int>()
        var i = 0
        while (i < sorted.size) {
            val first = scores[sorted[i]]!!
            var end = i + 1
            while (end < sorted.size && first - scores[sorted[end]]!! <= TIE_EPSILON) end++
            val group = sorted.subList(i, end)
            // Only a real tie calls the bass callback. If any member lacks
            // evidence, retain stable order instead of penalizing the unknown.
            val ties = if (group.size > 1) group.associateWith(bass) else emptyMap()
            if (ties.isNotEmpty() && ties.values.all { it?.isFinite() == true }) {
                result.addAll(group.sortedWith(compareByDescending<Int> { ties[it]!! }.thenBy { it }))
            } else result.addAll(group.sorted())
            i = end
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
