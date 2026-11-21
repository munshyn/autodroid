package com.husny.autodroid.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AzanMode { FULL, PAUSE_ONLY, OFF }
enum class Language { ENGLISH, MALAY }
enum class LyricsMode { SYNCED, CURRENT_ONLY, OFF }
enum class ClockStyle { LIGHT, BOLD, MONO }

/** Accent colours offered in Appearance (ARGB). */
val ACCENTS: List<Long> = listOf(0xFF3DD68C, 0xFF5B8CFF, 0xFFF5B544, 0xFFF27FB2)
val ACCENT_NAMES: List<String> = listOf("Green", "Blue", "Amber", "Pink")

/** Everything the user can change on the phone's settings screen. */
data class Settings(
    // Prayer & azan
    val azanMode: AzanMode = AzanMode.FULL,
    /** Minutes before a prayer that the card turns amber; 0 = off. */
    val azanSoonMinutes: Int = 10,
    val azanVolumePercent: Int = 70,
    val resumeMusic: Boolean = true,
    val notifyZoneChange: Boolean = true,
    // Clock & date
    val use24h: Boolean = false,
    val showHijri: Boolean = true,
    val showGreeting: Boolean = true,
    val language: Language = Language.ENGLISH,
    // Lyrics
    val lyricsMode: LyricsMode = LyricsMode.SYNCED,
    /** 0 small, 1 medium, 2 large. */
    val lyricsSize: Int = 1,
    /** Shifts lyrics earlier (negative) or later (positive), in milliseconds. */
    val lyricsOffsetMs: Int = 0,
    // Appearance
    val accentIndex: Int = 0,
    /** How much the album art is darkened behind the song title and buttons, 0–100. */
    val artShadePercent: Int = 60,
    val clockStyle: ClockStyle = ClockStyle.LIGHT,
    val amoledBlack: Boolean = false,
) {
    val accent: Long get() = ACCENTS.getOrElse(accentIndex) { ACCENTS[0] }
}

/**
 * Settings saved on the phone and shared live: a change on the phone shows on the car screen
 * straight away. One per app process.
 */
object SettingsStore {

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs = p
        _settings.value = read(p, context)
    }

    val current: Settings get() = _settings.value

    fun update(change: (Settings) -> Settings) {
        val next = change(_settings.value)
        _settings.value = next
        prefs?.let { write(it, next) }
    }

    private fun read(p: SharedPreferences, context: Context): Settings {
        val d = Settings()
        // Build 0.5–0.9 kept an on/off switch in the "azan" preferences.
        val legacyAzanOff = !context.applicationContext
            .getSharedPreferences("azan", Context.MODE_PRIVATE).getBoolean("enabled", true)
        return Settings(
            azanMode = enumOr(p.getString("azan_mode", null), if (legacyAzanOff) AzanMode.OFF else d.azanMode),
            azanSoonMinutes = p.getInt("azan_soon", d.azanSoonMinutes),
            azanVolumePercent = p.getInt("azan_volume", d.azanVolumePercent),
            resumeMusic = p.getBoolean("resume_music", d.resumeMusic),
            notifyZoneChange = p.getBoolean("notify_zone", d.notifyZoneChange),
            use24h = p.getBoolean("use_24h", d.use24h),
            showHijri = p.getBoolean("show_hijri", d.showHijri),
            showGreeting = p.getBoolean("show_greeting", d.showGreeting),
            language = enumOr(p.getString("language", null), d.language),
            lyricsMode = enumOr(p.getString("lyrics_mode", null), d.lyricsMode),
            lyricsSize = p.getInt("lyrics_size", d.lyricsSize),
            lyricsOffsetMs = p.getInt("lyrics_offset", d.lyricsOffsetMs),
            accentIndex = p.getInt("accent", d.accentIndex),
            artShadePercent = p.getInt("art_shade", d.artShadePercent),
            clockStyle = enumOr(p.getString("clock_style", null), d.clockStyle),
            amoledBlack = p.getBoolean("amoled", d.amoledBlack),
        )
    }

    private fun write(p: SharedPreferences, s: Settings) {
        p.edit()
            .putString("azan_mode", s.azanMode.name)
            .putInt("azan_soon", s.azanSoonMinutes)
            .putInt("azan_volume", s.azanVolumePercent)
            .putBoolean("resume_music", s.resumeMusic)
            .putBoolean("notify_zone", s.notifyZoneChange)
            .putBoolean("use_24h", s.use24h)
            .putBoolean("show_hijri", s.showHijri)
            .putBoolean("show_greeting", s.showGreeting)
            .putString("language", s.language.name)
            .putString("lyrics_mode", s.lyricsMode.name)
            .putInt("lyrics_size", s.lyricsSize)
            .putInt("lyrics_offset", s.lyricsOffsetMs)
            .putInt("accent", s.accentIndex)
            .putInt("art_shade", s.artShadePercent)
            .putString("clock_style", s.clockStyle.name)
            .putBoolean("amoled", s.amoledBlack)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: fallback
}
