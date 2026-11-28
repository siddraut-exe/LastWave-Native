package com.lastwave.app.playback.automix.neuralmix

/** These are execution limits only. Queue ranking never consults them. */
object NeuralMixTransitionPolicy {
    fun maxBars(score: CompatibilityScore?): Int {
        val tempo = score?.components?.get("tempo") ?: return 0
        val phrase = score.components["phrasing"] ?: return 0
        if (!tempo.isFinite() || !phrase.isFinite() || tempo < 0.5 || phrase < 0.25) return 0
        val harmony = score.components["harmonic"]
        // Long blends need positive evidence of harmony; don't equate unknown with safe.
        if (harmony != null && harmony >= 0.9 && phrase >= 0.9) return 8
        return if (phrase >= 0.5) 2 else 1
    }
}
