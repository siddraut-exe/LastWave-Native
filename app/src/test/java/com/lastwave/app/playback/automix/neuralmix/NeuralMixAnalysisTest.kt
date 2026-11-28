package com.lastwave.app.playback.automix.neuralmix

import com.lastwave.app.playback.automix.analysis.*
import org.junit.Assert.*
import org.junit.Test

class NeuralMixAnalysisTest {
    private fun window(bpm: Float?, confidence: Float = 0.9f, key: String? = "8A", start: Long = 0L) = WindowAnalysis(
        start, start + 40_000, bpm?.let { BeatGrid(it, start.toDouble(), confidence, 0.8f) }, key, 0.9f,
        start, start + 40_000, EndingStyle.NATURAL, null, IntroStyle.BUILD,
    )
    private fun signature(intro: WindowAnalysis? = null, outro: WindowAnalysis? = null) =
        AcousticSignature(key = "test", durationMs = 240_000, intro = intro, outro = outro, vocals = null)

    @Test fun threeFactorsOnlyAndFullScoreWithoutFingerprint() {
        val a = signature(outro = window(120f))
        val b = signature(intro = window(124f))
        val score = requireNotNull(SignatureCompatibility.compare(a, b))
        assertEquals(100.0, score.score, 1e-9)
        assertEquals(1.0, score.coverage, 1e-9)
        assertEquals(setOf("tempo", "harmonic", "phrasing"), score.components.keys)
    }
    @Test fun recommendationAcceptsLowerConfidenceThanBeatLock() {
        val score = requireNotNull(SignatureCompatibility.compare(signature(outro = window(120f, 0.4f)), signature(intro = window(121f, 0.4f))))
        assertEquals(100.0, score.score, 1e-9)
    }
    @Test fun absentRhythmStillHasKeyEvidenceAndUnknownIsNotMismatch() {
        val score = requireNotNull(SignatureCompatibility.compare(signature(outro = window(null)), signature(intro = window(null))))
        assertEquals(40.0, score.score, 1e-9)
        assertEquals(0.4, score.coverage, 1e-9)
        assertFalse(score.components.containsKey("tempo"))
        assertFalse(score.components.containsKey("phrasing"))
        assertNull(SignatureCompatibility.compare(signature(), signature()))
    }
    @Test fun tempoDeductionAndHalfTime() {
        assertEquals(1.0, NeuralMixRanker.tempo(140.0, 70.0)!!, 1e-9)
        assertEquals(0.5, NeuralMixRanker.tempo(120.0, 128.0)!!, 1e-9)
        assertEquals(0.0, NeuralMixRanker.tempo(120.0, 132.0)!!, 1e-9)
        assertNull(NeuralMixRanker.tempo(Double.NaN, 120.0))
    }
    @Test fun camelotWrapAndRelative() {
        assertEquals(0.9, NeuralMixRanker.harmony("12A", "1A")!!, 1e-9)
        assertEquals(0.95, NeuralMixRanker.harmony("8A", "8B")!!, 1e-9)
        assertEquals(0.0, NeuralMixRanker.harmony("8A", "2B")!!, 1e-9)
        assertNull(NeuralMixRanker.harmony("?", "2B"))
    }
    @Test fun shortIntroAndSilence() {
        val a = window(120f)
        val short = window(120f).copy(audibleEndMs = 4_000L)
        assertEquals(0.5, SignatureCompatibility.phraseScore(a, short)!!, 1e-9)
        assertNull(SignatureCompatibility.phraseScore(a, short.copy(audibleStartMs = null)))
    }
    @Test fun cueRhythmPrefersAlignedEnvelopes() {
        val bands = List(40) { (if (it % 5 == 0) 200 else 10) shl 16 }
        val aligned = requireNotNull(CueRhythm.compare(bands, 0, 0.0, bands, 0, 0.0, 1.0, 2000.0))
        val shifted = requireNotNull(CueRhythm.compare(bands, 0, 0.0, bands, 0, 100.0, 1.0, 2000.0))
        assertTrue(aligned > shifted)
        assertNull(CueRhythm.compare(List(40) { 0 }, 0, 0.0, bands, 0, 0.0, 1.0, 2000.0))
    }
}
