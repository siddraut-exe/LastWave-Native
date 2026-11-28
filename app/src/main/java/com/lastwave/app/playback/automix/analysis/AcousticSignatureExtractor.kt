package com.lastwave.app.playback.automix.analysis

import com.lastwave.app.playback.automix.neuralmix.RhythmFingerprint
import android.content.Context
import android.os.Process
import android.util.Log
import com.lastwave.app.data.lyrics.LyricsRepository
import com.lastwave.app.data.lyrics.LyricsResult
import com.lastwave.app.playback.PlayableTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.os.SystemClock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decodes and analyses the intro and outro of a stream once, off the main
 * thread, one stream at a time. Callers poll [signature] / [isSettled]: a
 * stream that fails or times out settles with no signature, and the planner
 * then picks a plain fade instead of inventing a tempo or key.
 */
@Singleton
class AcousticSignatureExtractor @Inject constructor(
    @ApplicationContext context: Context,
    private val lyricsRepository: dagger.Lazy<LyricsRepository>,
) {
    private val store = AcousticSignatureStore(File(context.filesDir, "automix"))
    private val scope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor { runnable ->
            Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                runnable.run()
            }, "AutoMixAnalysis")
        }.asCoroutineDispatcher(),
    )
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap<String, Long>()
    private val decodeLock = Mutex()

    private fun recentlyFailed(key: String): Boolean {
        val time = failed[key] ?: return false
        if (SystemClock.elapsedRealtime() - time < FAILURE_RETRY_MS) return true
        failed.remove(key, time)
        return false
    }

    private fun introKey(key: String): String = "$key|neuralmix-intro"

    fun signature(key: String): AcousticSignature? = store.get(key)

    private fun recordingIdentity(track: PlayableTrack): String? =
        track.videoId?.takeIf { it.isNotBlank() }?.let { "video:$it" }
            ?: track.internalTrackId?.takeIf { it.isNotBlank() }?.let { "library:$it" }

    fun recordingHint(track: PlayableTrack): AcousticSignature? = recordingIdentity(track)?.let(store::recordingHint)
    fun rememberRecording(track: PlayableTrack, signature: AcousticSignature) {
        recordingIdentity(track)?.let { store.rememberRecording(it, signature) }
    }


    /** A candidate needs only its intro; keep that cache separate from a full signature. */
    fun incomingSignature(key: String): AcousticSignature? =
        signature(key)?.takeIf { it.intro != null } ?: store.get(introKey(key))

    fun status(key: String, introOnly: Boolean = false): String {
        val storageKey = if (introOnly) introKey(key) else key
        val available = if (introOnly) incomingSignature(key) else signature(key)
        return when {
            available != null -> "available"
            storageKey in inFlight || (introOnly && key in inFlight) -> "pending"
            recentlyFailed(storageKey) || (introOnly && recentlyFailed(key)) -> "decode failed / retry cooldown"
            else -> "not requested or unsupported input"
        }
    }

    /** True once [key] is analysed or has failed; false while pending or never requested. */
    fun isSettled(key: String): Boolean = recentlyFailed(key) || signature(key) != null
    fun isIntroSettled(key: String): Boolean = incomingSignature(key) != null || recentlyFailed(introKey(key)) ||
        (key !in inFlight && recentlyFailed(key))

    fun request(key: String, track: PlayableTrack, input: AnalysisInput) =
        requestInternal(key, track, input, introOnly = false)

    fun requestIntro(key: String, track: PlayableTrack, input: AnalysisInput) =
        requestInternal(key, track, input, introOnly = true)

    private fun requestInternal(key: String, track: PlayableTrack, input: AnalysisInput, introOnly: Boolean) {
        val storageKey = if (introOnly) introKey(key) else key
        if (introOnly && (incomingSignature(key) != null || key in inFlight)) return
        if (recentlyFailed(storageKey) || storageKey in inFlight || store.get(storageKey) != null) return
        if (!inFlight.add(storageKey)) return
        scope.launch {
            try {
                val signature = decodeLock.withLock {
                    // Full analysis can reuse the intro once an in-flight candidate finishes.
                    val cached = incomingSignature(key)
                    if (introOnly && cached != null) cached.copy(key = storageKey, outro = null, outroRhythm = null)
                    else withTimeoutOrNull(ANALYSIS_TIMEOUT_MS) { analyze(storageKey, input, track, introOnly, cached) }
                }
                if (signature != null) {
                    val vocals = signature.vocals ?: withTimeoutOrNull(3_000L) { vocalSpans(track, signature.durationMs) }
                    val complete = signature.copy(vocals = vocals)
                    if (!introOnly && complete.outro == null) {
                        // Preserve a useful intro, but allow a later outro retry.
                        if (complete.intro != null) {
                            val partial = complete.copy(key = introKey(key))
                            store.put(partial)
                            rememberRecording(track, partial)
                        }
                        failed[storageKey] = SystemClock.elapsedRealtime()
                    } else if (introOnly && complete.intro == null) {
                        failed[storageKey] = SystemClock.elapsedRealtime()
                    } else {
                        store.put(complete)
                        rememberRecording(track, complete)
                        failed.remove(storageKey)
                    }
                } else {
                    failed[storageKey] = SystemClock.elapsedRealtime()
                    Log.w(TAG, "No acoustic signature for '${track.title}' (timeout or empty PCM, introOnly=$introOnly)")
                }
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                Log.w(TAG, "Analysis failed for '${track.title}'", error)
                failed[storageKey] = SystemClock.elapsedRealtime()
            } finally {
                inFlight -= storageKey
            }
        }
    }

    private suspend fun analyze(key: String, input: AnalysisInput, track: PlayableTrack, introOnly: Boolean, cachedIntro: AcousticSignature?): AcousticSignature? {
        val started = System.currentTimeMillis()
        val signature = PcmWindowDecoder(input).use { decoder ->
            val durationMs = decoder.durationMs.takeIf { it > 0L } ?: track.durationMs ?: return null
            if (durationMs <= 0L) return null
            suspend fun window(startMs: Long): Pair<WindowAnalysis, RhythmFingerprint?>? = try {
                withTimeoutOrNull(WINDOW_TIMEOUT_MS) { decoder.decode(startMs, WINDOW_MS)?.let { analyzeWindow(it) } }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "PCM window at ${startMs}ms failed for '${track.title}'", error)
                null
            }
            // Exact stream identity is already checked by the cache key. A changed
            // decoded duration indicates a different edit; don't reuse its intro.
            val cached = cachedIntro?.takeIf { kotlin.math.abs(it.durationMs - durationMs) < 500L }
            val intro = cached?.intro?.let { it to cached.introRhythm } ?: window(0L)
            val outroStart = (durationMs - WINDOW_MS).coerceAtLeast(0L)
            val outro = if (introOnly) null else if (outroStart == 0L) intro else window(outroStart)
            if (intro == null && outro == null) return null
            AcousticSignature(
                key = key,
                durationMs = durationMs,
                intro = intro?.first,
                outro = outro?.first,
                introRhythm = intro?.second,
                outroRhythm = outro?.second,
                vocals = cached?.vocals,
            )
        }
        Log.i(TAG, "'${track.title}' in ${System.currentTimeMillis() - started}ms: introOnly=$introOnly " +
            "intro=${signature.intro?.describe()} outro=${signature.outro?.describe()} " +
            "introRhythm=${signature.introRhythm != null} outroRhythm=${signature.outroRhythm != null}")
        return signature
    }

    private fun analyzeWindow(window: PcmWindowDecoder.Window): Pair<WindowAnalysis, RhythmFingerprint?> =
        TrackAnalyzer.analyze(window.pcm, window.sampleRate, window.startMs) to null

    /** Synced lyric lines are the only vocal evidence trusted; audio-only guesses were noise. */
    private suspend fun vocalSpans(track: PlayableTrack, durationMs: Long): List<VocalSpan>? {
        val lyrics = runCatching {
            lyricsRepository.get().getLyrics(
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationSeconds = (durationMs / 1000).toInt(),
                videoId = track.videoId,
            )
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.getOrNull() as? LyricsResult.Success ?: return null
        if (!lyrics.isSynced) return null
        val lines = lyrics.lines.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }
        return lines.mapIndexed { index, line ->
            val nextStart = lines.getOrNull(index + 1)?.timeMs
            val length = line.durationMs.takeIf { it > 0L }
                ?: nextStart?.let { (it - line.timeMs).coerceIn(500L, MAX_LINE_MS) }
                ?: DEFAULT_LINE_MS
            VocalSpan(line.timeMs, line.timeMs + length)
        }
    }

    private fun WindowAnalysis.describe(): String =
        "[${grid?.let { "%.1fbpm c=%.2f bar=%.2f".format(it.bpm, it.confidence, it.barConfidence) } ?: "no-grid"} " +
            "key=${camelotKey ?: "?"}(${"%.2f".format(keyConfidence)}) " +
            "audible=${audibleStartMs}..${audibleEndMs} $ending${fadeStartMs?.let { "@$it" } ?: ""} $intro]"

    companion object {
        const val WINDOW_MS = 40_000L
        private const val ANALYSIS_TIMEOUT_MS = 30_000L
        private const val WINDOW_TIMEOUT_MS = 14_000L
        private const val FAILURE_RETRY_MS = 60_000L
        private const val MAX_LINE_MS = 6_000L
        private const val DEFAULT_LINE_MS = 3_500L
        private const val TAG = "AutoMixAnalysis"
    }
}
