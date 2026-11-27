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
    @Test fun silenceIsUnknown() {
        assertNull(BeatSynchronousAnalyzer.analyze(FloatArray(64000),8000,0.0,0.0,120.0))
    }
}
