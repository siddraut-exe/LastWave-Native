package com.lastwave.app.playback.automix.neuralmix

import com.lastwave.app.playback.automix.analysis.AcousticSignature
import com.lastwave.app.playback.automix.analysis.WindowAnalysis
import kotlin.math.floor

/** Recommendation confidence is deliberately lower than live beat-lock confidence. */
object SignatureCompatibility {
    private const val RECOMMEND_BEAT = 0.35f
    private const val RECOMMEND_KEY = 0.35f

    fun compare(outgoing: AcousticSignature, incoming: AcousticSignature): CompatibilityScore? {
        val a = outgoing.outro
        val b = incoming.intro
        // A track-wide estimate may help discovery when an edge is quiet. It
        // cannot authorize beat locking at that edge.
        fun bpm(primary: WindowAnalysis?, fallback: WindowAnalysis?): Double? =
            sequenceOf(primary, fallback).mapNotNull { it?.grid }
                .firstOrNull { it.confidence >= RECOMMEND_BEAT && it.bpm.isFinite() && it.bpm > 0 }?.bpm?.toDouble()
        fun key(primary: WindowAnalysis?, fallback: WindowAnalysis?): String? =
            sequenceOf(primary, fallback).firstOrNull { it?.camelotKey != null && it.keyConfidence >= RECOMMEND_KEY }?.camelotKey
        return NeuralMixRanker.score(
            NeuralMixRanker.tempo(bpm(a, outgoing.intro), bpm(b, incoming.outro)),
            NeuralMixRanker.harmony(key(a, outgoing.intro), key(b, incoming.outro)),
            phraseScore(a, b),
        )
    }

    /** Number of available aligned beats, not a claim of detected instrumental stems.
     * Intro section changes and outro boundaries constrain the estimate. */
    fun phraseScore(a: WindowAnalysis?, b: WindowAnalysis?): Double? {
        if (a == null || b == null) return null
        if (a.audibleEndMs == null || b.audibleStartMs == null || b.audibleEndMs == null) return null
        val ga = a.grid?.takeIf { it.confidence >= RECOMMEND_BEAT && it.bpm.isFinite() && it.bpm > 0 && it.downbeatMs.isFinite() } ?: return null
        val gb = b.grid?.takeIf { it.confidence >= RECOMMEND_BEAT && it.bpm.isFinite() && it.bpm > 0 && it.downbeatMs.isFinite() } ?: return null
        val aEnd = minOf(a.endMs, a.audibleEndMs).toDouble()
        val aStart = maxOf(a.startMs, a.audibleStartMs ?: a.startMs).toDouble()
        val aCue = ga.downbeatAtOrAfter(aStart)
        val bCue = gb.downbeatAtOrAfter(maxOf(b.startMs, b.audibleStartMs).toDouble())
        val bEnd = minOf(b.endMs, b.audibleEndMs).toDouble()
        val firstSection = b.phrases.firstOrNull { it.ms > bCue && it.shiftDb > 0 }?.ms?.toDouble() ?: bEnd
        val inBeats = floor((minOf(bEnd, firstSection) - bCue) / gb.beatMs)
        val outBeats = floor((aEnd - aCue) / ga.beatMs)
        val beats = minOf(inBeats, outBeats)
        val phrase = when {
            beats >= 32 -> 1.0
            beats >= 16 -> 0.9
            beats >= 8 -> 0.5
            beats >= 4 -> 0.25
            else -> 0.0
        }
        // Available bars are a proxy for structure, not proof of a drum-only intro.
        return if (ga.barConfidence >= 0.5f && gb.barConfidence >= 0.5f) phrase else minOf(phrase, 0.5)
    }

    /** Only called for tied measured scores; reuse cached low-band columns. */
    fun bassTie(outgoing: AcousticSignature, incoming: AcousticSignature): Double? {
        val a = outgoing.outro ?: return null
        val b = incoming.intro ?: return null
        val ga = a.grid?.takeIf { it.confidence >= RECOMMEND_BEAT && it.bpm.isFinite() && it.bpm > 0 } ?: return null
        val gb = b.grid?.takeIf { it.confidence >= RECOMMEND_BEAT && it.bpm.isFinite() && it.bpm > 0 } ?: return null
        val aEnd = a.audibleEndMs ?: return null
        val bStart = b.audibleStartMs ?: return null
        val span = 4 * ga.beatMs
        val aCue = ga.downbeatAtOrBefore(aEnd - span)
        val bCue = gb.downbeatAtOrAfter(bStart.toDouble())
        return CueRhythm.compare(a.bands, a.startMs, aCue, b.bands, b.startMs, bCue,
            gb.beatMs / ga.beatMs, span)?.let { (it + 1.0) / 2.0 }
    }
}
