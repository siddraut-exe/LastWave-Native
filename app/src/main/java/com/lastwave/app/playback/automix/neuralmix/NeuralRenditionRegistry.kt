package com.lastwave.app.playback.automix.neuralmix

/** A loader can resolve a placeholder without changing the player's MediaItem.
 * Never guess its recording from title/artist or use a rendition from another deck.
 */
internal class NeuralRenditionRegistry<T>(private val keyOf: (T) -> String, private val limit: Int = 256) {
    private data class Entry<T>(val key: String, val value: T?)
    private val entries = LinkedHashMap<String, Entry<T>>()
    @Synchronized fun observe(placeholder: String, value: T) {
        val key = keyOf(value)
        val previous = entries[placeholder]
        // Different renditions may still have buffered bytes. Mark ambiguous rather than
        // claiming that the newest resolve is necessarily the currently audible stream.
        entries[placeholder] = Entry(key, if (previous == null || (previous.key == key && previous.value != null)) value else null)
        while (entries.size > limit.coerceAtLeast(1)) entries.remove(entries.keys.first())
    }
    @Synchronized fun get(placeholder: String): T? = entries[placeholder]?.value
    @Synchronized fun clear() = entries.clear()
}
