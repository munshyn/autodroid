package com.husny.autodroid.data

import android.util.LruCache
import org.json.JSONObject

data class LyricLine(val timeMs: Long, val text: String)

/** [synced] lyrics have a time on every line; plain ones are just text. */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {
    /** Index of the line being sung at [positionMs], or -1 before the first line. */
    fun indexAt(positionMs: Long): Int {
        if (!synced) return -1
        var index = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= positionMs) index = i else break
        }
        return index
    }
}

sealed interface LyricsResult {
    data object Loading : LyricsResult
    data object NotFound : LyricsResult
    data class Found(val lyrics: Lyrics) : LyricsResult
}

/**
 * Lyrics from Lyrica (github.com/Wilooper/Lyrica), an open-source API that searches LRCLIB,
 * Lrcmux, Genius, YouTube Music, NetEase, Megalobiz and more, returning synced lyrics when any
 * source has them.
 */
class LyricsRepository {

    private val cache = LruCache<String, LyricsResult>(50)

    suspend fun find(title: String, artist: String, @Suppress("UNUSED_PARAMETER") durationMs: Long): LyricsResult {
        val key = "$artist|$title"
        cache.get(key)?.let { return it }

        val url = "$LYRICA_BASE_URL/lyrics/?artist=${Http.encode(artist)}" +
            "&song=${Http.encode(cleanTitle(title))}&timestamps=true"
        // Lyrica tries every source before saying "not found" (about 35 s), and the free server
        // can take a while to wake up, so allow a long wait.
        val body = Http.get(url, readTimeoutMs = 60_000)
        val result = body?.let { runCatching { toResult(JSONObject(it)) }.getOrNull() } ?: LyricsResult.NotFound
        // Only remember hits, so a song looked up with no signal is tried again next time.
        if (result is LyricsResult.Found) cache.put(key, result)
        return result
    }

    private fun toResult(response: JSONObject): LyricsResult {
        if (response.optString("status") != "success") return LyricsResult.NotFound
        val data = response.optJSONObject("data") ?: return LyricsResult.NotFound
        if (data.optBoolean("instrumental")) return LyricsResult.NotFound

        val timed = data.optJSONArray("timed_lyrics")
        if (timed != null && timed.length() > 0) {
            val lines = (0 until timed.length()).map { i ->
                val o = timed.getJSONObject(i)
                LyricLine(o.optLong("start_time"), o.optString("text").trim())
            }.sortedBy { it.timeMs }
            return LyricsResult.Found(Lyrics(lines, synced = true))
        }

        val text = data.optString("lyrics").takeIf { !data.isNull("lyrics") && it.isNotBlank() }
            ?: return LyricsResult.NotFound
        val lrc = parseLrc(text)
        if (lrc.isNotEmpty()) return LyricsResult.Found(Lyrics(lrc, synced = true))
        val lines = text.lines().filter { it.isNotBlank() }.map { LyricLine(0, it.trim()) }
        return LyricsResult.Found(Lyrics(lines, synced = false))
    }

    companion object {
        /** Public Lyrica server. If you self-host Lyrica later, change this one line. */
        const val LYRICA_BASE_URL = "https://test-0k.onrender.com"

        private val TAG = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")

        fun parseLrc(text: String): List<LyricLine> = text.lines().flatMap { raw ->
            val tags = TAG.findAll(raw).toList()
            if (tags.isEmpty()) return@flatMap emptyList()
            val words = raw.substring(tags.last().range.last + 1).trim()
            tags.map { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                LyricLine(min * 60_000 + sec * 1_000 + ms, words)
            }
        }.sortedBy { it.timeMs }

        /** Drops YouTube-style extras: "Song (Official Video)" → "Song", "Song [Lyrics]" → "Song". */
        fun cleanTitle(title: String): String =
            title.replace(Regex("""\s*[(\[][^)\]]*(official|video|audio|lyric|visuali[sz]er|mv|hd|4k)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
                .trim()
                .ifEmpty { title }
    }
}
