package com.husny.autodroid.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.husny.autodroid.R
import com.husny.autodroid.settings.Background
import com.husny.autodroid.settings.ClockStyle
import com.husny.autodroid.settings.FontStyle
import com.husny.autodroid.settings.Settings

/** The colours of one car screen background. */
data class CarPalette(
    /** Screen background. */
    val bg: Color,
    /** Music panel. */
    val panel: Color,
    /** Lines and borders. */
    val line: Color,
    val text: Color,
    val muted: Color,
    /** The lyric line just sung. */
    val dim: Color,
    /** Upcoming lyric lines. */
    val sub: Color,
    /** The lyric line being sung. */
    val highlight: Color,
)

fun paletteFor(background: Background): CarPalette = when (background) {
    Background.GRAPHITE -> CarPalette(
        Color(0xFF121417), Color(0xFF1A1D21), Color(0xFF2A2D32), Color(0xFFECE6DA),
        Color(0xFF8E8B84), Color(0xFF6E6C67), Color(0xFF9A968E), Color(0xFFFFF6E6),
    )
    Background.BLACK -> CarPalette(
        Color(0xFF000000), Color(0xFF0B0C0D), Color(0xFF1E2023), Color(0xFFECE6DA),
        Color(0xFF85827C), Color(0xFF5E5C58), Color(0xFF96928A), Color(0xFFFFF6E6),
    )
    Background.NAVY -> CarPalette(
        Color(0xFF0D1520), Color(0xFF132031), Color(0xFF22324A), Color(0xFFE4EAF1),
        Color(0xFF8393A8), Color(0xFF5E6E84), Color(0xFF9AAABE), Color(0xFFF4F8FC),
    )
    Background.FOREST -> CarPalette(
        Color(0xFF10150F), Color(0xFF171F15), Color(0xFF283322), Color(0xFFE8EADC),
        Color(0xFF8B927C), Color(0xFF67705A), Color(0xFFA1A893), Color(0xFFF7F8EE),
    )
}

/** Text drawn on top of the accent colour (play button, azan box). */
val OnAccent = Color(0xFF121417)

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_light, FontWeight.Light),
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
)

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

/** Outfit is one variable font file; each weight picks its point on the weight axis. */
@OptIn(ExperimentalTextApi::class)
val Outfit = FontFamily(
    listOf(200, 300, 400, 500, 600).map {
        Font(R.font.outfit, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it)))
    }
)

/** Everything the car screen needs to know about the look settings, worked out once. */
class CarLook(settings: Settings) {
    val palette = paletteFor(settings.background)
    val accent = Color(settings.accent)

    /** Numbers, labels and the clock. */
    val display: FontFamily
    /** Song title, lyrics and messages. */
    val body: FontFamily
    /** Narrows the clock so wider fonts fit the left column. */
    private val clockWidth: Float
    /** Narrows the countdown and prayer times for wider fonts. */
    val numberWidth: Float
    /** Prayer names in the times strip are capitals only in the Cluster font. */
    val upperTimes: Boolean
    private val light: FontWeight
    private val bold: FontWeight

    init {
        when (settings.fontStyle) {
            FontStyle.CLUSTER -> {
                display = BarlowCondensed; body = Barlow
                clockWidth = 1f; numberWidth = 1f; upperTimes = true
                light = FontWeight.Light; bold = FontWeight.SemiBold
            }
            FontStyle.ROUNDED -> {
                display = Outfit; body = Outfit
                clockWidth = 0.8f; numberWidth = 0.88f; upperTimes = false
                light = FontWeight.ExtraLight; bold = FontWeight.SemiBold
            }
            FontStyle.CLASSIC -> {
                display = FontFamily.Default; body = FontFamily.Default
                clockWidth = 0.82f; numberWidth = 0.9f; upperTimes = false
                light = FontWeight.Light; bold = FontWeight.Bold
            }
        }
    }

    /** Text size setting: 0.9, 1 or 1.1. */
    val z = settings.textSize.scale

    /** Lyrics size setting on top of the text size. */
    val lyricsScale = z * when (settings.lyricsSize) { 0 -> 0.85f; 2 -> 1.15f; else -> 1f }

    /** Large text or large lyrics leaves room for 2 upcoming lines instead of 3. */
    val upcomingLines = if (lyricsScale > 1.05f) 2 else 3

    val clockFamily: FontFamily = if (settings.clockStyle == ClockStyle.MONO) FontFamily.Monospace else display
    val clockWeight: FontWeight = when (settings.clockStyle) {
        ClockStyle.BOLD -> bold
        ClockStyle.MONO -> FontWeight.Normal
        ClockStyle.LIGHT -> light
    }
    val clockScale: Float = z * if (settings.clockStyle == ClockStyle.MONO) 0.72f else clockWidth
}
