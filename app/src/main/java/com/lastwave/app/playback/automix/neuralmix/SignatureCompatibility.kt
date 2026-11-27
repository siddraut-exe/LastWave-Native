package com.lastwave.app.playback.automix.neuralmix

import com.lastwave.app.playback.automix.analysis.AcousticSignature

/** Compare transition windows; unknown or unreliable rhythm yields no score. */
object SignatureCompatibility {
    fun compare(outgoing: AcousticSignature, incoming: AcousticSignature): CompatibilityScore? {
        val a = outgoing.outroRhythm ?: return null
        val b = incoming.introRhythm ?: return null
        val aKey = outgoing.outro?.takeIf { it.keyConfidence >= 0.5f }?.camelotKey
        val bKey = incoming.intro?.takeIf { it.keyConfidence >= 0.5f }?.camelotKey
        // Vocal overlap depends on the actual cue points, not the whole windows.
        return NeuralMixRanker.compare(a, b, aKey, bKey)
    }
}
