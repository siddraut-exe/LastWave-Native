package com.lastwave.app.playback.automix.neuralmix

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class NeuralMixAnalysisTest {
    private fun fingerprint(shift: Int): RhythmFingerprint {
        val rate=8000
        val pcm=FloatArray(rate*8) { i ->
            val slot=(i/1000)%16
            if(slot%4==shift && i%1000<400) (0.6*sin(2*PI*80*i/rate)).toFloat() else 0f
        }
        return requireNotNull(BeatSynchronousAnalyzer.analyze(pcm,rate,0.0,0.0,120.0))
    }
    @Test fun matchingRhythmRanksHigher() {
        val a=fingerprint(0)
        assertTrue(NeuralMixRanker.compare(a,a).score > NeuralMixRanker.compare(a,fingerprint(2)).score)
        assertEquals(100.0,NeuralMixRanker.compare(a,a).score,0.001)
        assertEquals(0.7,NeuralMixRanker.compare(a,a).coverage,0.001)
    }
    @Test fun flatRhythmDoesNotCountAsEvidence() {
        val flat = List(16) { 1.0 }
        val a = RhythmFingerprint(flat, flat, flat, 0.5, 120.0)
        val score = NeuralMixRanker.compare(a, a)
        assertFalse(score.components.containsKey("rhythm"))
        assertEquals(0, NeuralMixTransitionPolicy.maxBars(score))
    }
    @Test fun halfTimeTempoIsCompatible() {
        val a = fingerprint(0)
        val score = NeuralMixRanker.compare(a, a.copy(bpm = a.bpm / 2))
        assertEquals(1.0, requireNotNull(score.components["tempo"]), 0.001)
    }
    @Test fun cueRhythmPrefersAlignedEnvelopes() {
        val bands = List(40) { (if (it % 5 == 0) 200 else 10) shl 16 }
        val aligned = requireNotNull(CueRhythm.compare(bands, 0, 0.0, bands, 0, 0.0, 1.0, 2000.0))
        val shifted = requireNotNull(CueRhythm.compare(bands, 0, 0.0, bands, 0, 100.0, 1.0, 2000.0))
        assertTrue(aligned > shifted)
        assertNull(CueRhythm.compare(List(40) { 0 }, 0, 0.0, bands, 0, 0.0, 1.0, 2000.0))
    }
    @Test fun uncertainBarPhaseAllowsWholeBeatRotationButNotOffbeatShift() {
        val profile = List(16) { if (it == 0) 1.0 else if (it == 8) 0.4 else 0.0 }
        val a = RhythmFingerprint(profile, profile, profile, 0.5, 120.0)
        fun rotated(n: Int) = List(16) { profile[(it + n) % 16] }.let { a.copy(rhythm = it, bass = it, texture = it) }
        val aligned = NeuralMixRanker.compare(a, rotated(4), allowBarRotation = true)
        assertEquals(1.0, aligned.components.getValue("rhythm"), 0.001)
        assertTrue(NeuralMixRanker.compare(a, rotated(1), allowBarRotation = true).score < aligned.score)
        assertTrue(NeuralMixRanker.compare(a, rotated(4)).score < aligned.score)
    }
    @Test fun silenceIsUnknown() {
        assertNull(BeatSynchronousAnalyzer.analyze(FloatArray(64000),8000,0.0,0.0,120.0))
    }
}
