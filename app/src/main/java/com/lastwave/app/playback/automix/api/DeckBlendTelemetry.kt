package com.lastwave.app.playback.automix.api

import androidx.compose.runtime.Immutable

/** Effective mixing mode, independent of whether an acoustic plan exists yet. */
enum class MixEngine { NONE, AUTOMIX, NEURALMIX }

/** Phase of the dual-deck transition lifecycle. */
enum class BlendPhase {
    IDLE,
    PRE_ANALYZING,
    ARMED,
    BLENDING,
    MIX_COMPLETED,
}

/** How the two songs meet; every value names audio that is really produced. */
enum class TransitionStyle {
    /** Tempo-matched, beat-locked mix with a bass swap. */
    HARMONIC_BEATMIX,
    /** Crossfade with the low end handed over mid-way. */
    SPECTRAL_SWEEP,
    /** Plain crossfade. */
    PERCEPTUAL_FADE,
}

/** How the two decks' levels move across a mix. */
enum class MixShape {
    /** Constant-power crossfade. */
    EQUAL_POWER,
    /**
     * DJ blend around a mid-point bass swap: the incoming deck rises (bass
     * cut) while the outgoing one holds, then the outgoing (bass cut) falls
     * while the incoming holds. The deck that owns the low end stays at full
     * level; dipping it with an equal-power curve sagged kick-led music ~4 dB.
     */
    BASS_SWAP,
    /** The outgoing song keeps its level and ends on its own (cold endings). */
    HOLD_OUTGOING,
    /** Real filter automation on processed decks. */
    FILTER_FADE,
    /** Real bounded PCM delay on the outgoing processed deck. */
    ECHO_OUT,
}

/** One deck as the mix view draws it. */
@Immutable
data class MixDeckView(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val bpm: Float?,
    val camelotKey: String?,
    /** Media time of the first band column; columns are [com.lastwave.app.playback.automix.analysis.BAND_COLUMN_MS] apart. */
    val bandsStartMs: Long,
    val bands: List<Int>,
)

/** Everything needed to draw a planned mix: both waveforms, the cue points, grid and EQ moves. */
@Immutable
data class MixView(
    val outgoing: MixDeckView,
    val incoming: MixDeckView,
    val cueOutMs: Long,
    val cueInMs: Long,
    val overlapMs: Long,
    val tempoRatio: Float,
    val shape: MixShape,
    val bassSwapAtMs: Long?,
    val bassSwapRampMs: Long,
    /** Beat-locked mixes only: an outgoing downbeat and the bar length, for grid lines. */
    val downbeatMs: Double?,
    val barMs: Double?,
    val bars: Int?,
    val style: TransitionStyle,
    val keyPair: String?,
    val neuralMode: Boolean = false,
    val neuralScore: Double? = null,
    val neuralCoverage: Double? = null,
    val neuralComponents: Map<String, Double> = emptyMap(),
    val neuralReason: String? = null,
) {
    val techniqueName: String get() = when (shape) {
        MixShape.BASS_SWAP -> if (style == TransitionStyle.HARMONIC_BEATMIX) "Bass Swap" else "Bass fade"
        MixShape.FILTER_FADE -> "Filter Fade"
        MixShape.ECHO_OUT -> "Echo Out"
        else -> if (style == TransitionStyle.HARMONIC_BEATMIX) "Phrase Fade" else "Smooth fade"
    }
    fun incomingToOutgoing(incomingMs: Double): Double = cueOutMs + (incomingMs - cueInMs) / tempoRatio
}

@Immutable
data class TrackChromeSummary(
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
)

/**
 * Real-time immutable telemetry consumed by the Expanded Player, Canvas Slider,
 * and Collapsed Mini-Player chrome.
 */
@Immutable
data class DeckBlendTelemetry(
    val phase: BlendPhase = BlendPhase.IDLE,
    val blendProgress: Float = 0.0f, // 0.0f..1.0f clamped strictly to Material 3 range
    val activeTransitionStyle: TransitionStyle = TransitionStyle.PERCEPTUAL_FADE,
    val syncedBpm: Float? = null,
    val harmonicKeyPair: String? = null, // e.g. "8A â†’ 9A"
    val spliceStartPositionMs: Long = 0L,
    val spliceDurationMs: Long = 5_000L, // 1,000ms up to 30,000ms
    val incomingTrackPreview: TrackChromeSummary? = null,
    val isVocalProtected: Boolean = true,
    /** Null when either song lacks analysis; the UI then keeps the artwork. */
    val view: MixView? = null,
    val engine: MixEngine = MixEngine.NONE,
    val noMixReason: String? = null,
    /** True after a natural track advance with no overlap; never mark it MIX_COMPLETED. */
    val noMixCompleted: Boolean = false,
) {
    val isNeuralMix: Boolean get() = engine == MixEngine.NEURALMIX || view?.neuralMode == true
    val mixName: String get() = if (isNeuralMix) "NeuralMix" else "AutoMix"

    val mixStatusLabel: String get() = when {
        isNeuralMix && noMixReason != null -> "NeuralMix didn’t mix"
        isNeuralMix && phase == BlendPhase.MIX_COMPLETED && activeTransitionStyle != TransitionStyle.HARMONIC_BEATMIX -> "NeuralMix · Not beat-mixed"
        isNeuralMix && (phase == BlendPhase.ARMED || phase == BlendPhase.BLENDING) && activeTransitionStyle != TransitionStyle.HARMONIC_BEATMIX -> "NeuralMix · Fade"
        else -> mixName
    }

    val isBlendingActive: Boolean
        get() = phase == BlendPhase.BLENDING

    val isArmedOrBlending: Boolean
        get() = phase == BlendPhase.ARMED || isBlendingActive

    val clampedProgress: Float
        get() = blendProgress.coerceIn(0.0f, 1.0f)

    companion object {
        val IDLE = DeckBlendTelemetry(
            phase = BlendPhase.IDLE,
            blendProgress = 0.0f,
            activeTransitionStyle = TransitionStyle.PERCEPTUAL_FADE,
            syncedBpm = null,
            harmonicKeyPair = null,
            spliceStartPositionMs = 0L,
            spliceDurationMs = 5_000L,
            incomingTrackPreview = null,
            isVocalProtected = true,
        )
    }
}
