package com.lastwave.app.playback.automix.analysis

import com.lastwave.app.playback.automix.neuralmix.RhythmFingerprint
import android.util.Log
import android.util.LruCache
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/** Bump when analysis output changes meaning: older files are then ignored, never misread. */
const val ANALYSIS_VERSION = 4

@Serializable
data class VocalSpan(val startMs: Long, val endMs: Long)

/**
 * Analysis of one exact stream. Only the windows a transition uses are
 * decoded: the first and last [AcousticSignatureExtractor.WINDOW_MS].
 * A null field means "not known", never a guessed default.
 */
@Serializable
data class AcousticSignature(
    val version: Int = ANALYSIS_VERSION,
    val key: String,
    val durationMs: Long,
    val intro: WindowAnalysis?,
    val outro: WindowAnalysis?,
    /** From synced lyrics only; null when the song has none. */
    val vocals: List<VocalSpan>?,
    val introRhythm: RhythmFingerprint? = null,
    val outroRhythm: RhythmFingerprint? = null,
) {
    /** Null when unknown. */
    fun hasVocalsBetween(fromMs: Long, toMs: Long): Boolean? =
        vocals?.any { it.endMs > fromMs && it.startMs < toMs }
}

/** Memory LRU over one small JSON file per stream, so a song is decoded once, not every play. */
class AcousticSignatureStore(private val directory: File) {
    private val memory = LruCache<String, AcousticSignature>(64)
    private val missing = LruCache<String, Boolean>(256)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun get(key: String): AcousticSignature? {
        memory.get(key)?.let { return it }
        if (missing.get(key) == true) return null
        val file = fileFor(key)
        val loaded = if (file.isFile) runCatching {
            val raw = file.readText()
            val obj = json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject
            // Older versions omitted the default version field. Do not decode
            // those using today's default, which silently mislabels old data.
            val version = obj?.get("version") as? kotlinx.serialization.json.JsonPrimitive
            if (version?.content != ANALYSIS_VERSION.toString()) null
            else json.decodeFromString<AcousticSignature>(raw)
        }.getOrNull()?.takeIf { it.version == ANALYSIS_VERSION && it.key == key } else null
        if (loaded == null) missing.put(key, true) else memory.put(key, loaded)
        return loaded
    }

    fun put(signature: AcousticSignature) {
        synchronized(this) {
            memory.put(signature.key, signature)
            missing.remove(signature.key)
        }
        runCatching {
            directory.mkdirs()
            val file = fileFor(signature.key)
            val temp = File(directory, file.name + ".tmp")
            temp.writeText(json.encodeToString(signature))
            check(temp.renameTo(file)) { "Could not replace acoustic cache entry" }
        }.onFailure { Log.w(TAG, "Could not persist analysis for ${signature.key}", it) }
    }

    /** A stable recording ID is only a shortlist hint; never a substitute for an exact rendition key. */
    @Synchronized
    fun recordingHint(identity: String): AcousticSignature? {
        val file = recordingFile(identity)
        val key = runCatching { if (file.isFile && file.length() in 1L..4096L) file.readText() else null }.getOrNull() ?: return null
        return get(key)?.takeIf { it.intro != null }
    }

    @Synchronized
    fun rememberRecording(identity: String, signature: AcousticSignature) {
        if (signature.intro == null || signature.version != ANALYSIS_VERSION || signature.key.length > 4096) return
        runCatching {
            val file = recordingFile(identity)
            if (file.isFile && file.length() <= 4096 && file.readText() == signature.key) return@runCatching
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(signature.key)
            check(temp.renameTo(file))
        }.onFailure { Log.w(TAG, "Could not persist recording hint", it) }
    }

    private fun recordingFile(identity: String): File = File(File(directory, "recordings"), fileFor(identity).name)

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return File(directory, digest.joinToString("") { "%02x".format(it) } + ".json")
    }

    private companion object {
        const val TAG = "AutoMixAnalysis"
    }
}
