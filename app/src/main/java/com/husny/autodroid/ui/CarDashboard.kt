package com.husny.autodroid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.husny.autodroid.azan.Azan
import com.husny.autodroid.car.DashboardModel
import com.husny.autodroid.car.PrayerStatus
import com.husny.autodroid.car.SurfaceInfo
import com.husny.autodroid.data.Lyrics
import com.husny.autodroid.data.LyricsResult
import com.husny.autodroid.data.MALAYSIA
import com.husny.autodroid.data.Playback
import com.husny.autodroid.data.Prayer
import com.husny.autodroid.data.PrayerDay
import com.husny.autodroid.data.Track
import com.husny.autodroid.data.formatHijri
import com.husny.autodroid.data.upcomingPrayers
import com.husny.autodroid.settings.Language
import com.husny.autodroid.settings.LyricsMode
import com.husny.autodroid.settings.Settings
import com.husny.autodroid.settings.SettingsStore
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The layout is designed for an 800×400 screen and scaled to whatever the car reports. */
const val DESIGN_WIDTH = 800f
const val DESIGN_HEIGHT = 400f

/** Settings from the phone, available to every part of the car screen. */
private val LocalSettings = staticCompositionLocalOf { Settings() }
private val LocalLook = staticCompositionLocalOf { CarLook(Settings()) }

/** English or Malay, following the phone setting. */
@Composable
private fun tr(en: String, ms: String): String =
    if (LocalSettings.current.language == Language.MALAY) ms else en

/** Everything the car screen shows, as plain values (so the phone can draw a preview too). */
data class ScreenData(
    val now: LocalDateTime,
    /** "Now" in Malaysian time, which prayer times are in. */
    val nowMy: LocalDateTime,
    val prayer: PrayerStatus,
    val azan: Azan.State,
    val zoneLabel: String,
    val hasAccess: Boolean,
    val track: Track?,
    val playback: Playback?,
    val lyrics: LyricsResult?,
)

/** The car screen ("Cluster" design): clock and prayer on the left, music and lyrics on the right. */
@Composable
fun CarDashboard(
    info: SurfaceInfo,
    model: DashboardModel,
    onToggleClock: () -> Unit,
) {
    val settings by SettingsStore.settings.collectAsState()
    val now by produceState(initialValue = LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1_000L - (System.currentTimeMillis() % 1_000L))
        }
    }
    val prayer by model.prayer.collectAsState()
    val hasAccess by model.music.hasAccess.collectAsState()
    val track by model.music.track.collectAsState()
    val playback by model.music.playback.collectAsState()
    val lyrics by model.lyrics.collectAsState()
    val azan by Azan.state.collectAsState()
    val zone by model.zone.collectAsState()

    val base = LocalDensity.current
    // Fit the tighter side, so a wide-and-short or a tall screen never squashes the layout;
    // the panels stretch to use any extra room.
    val scaled = if (info.width > 0 && info.height > 0) {
        Density(minOf(info.width / DESIGN_WIDTH, info.height / DESIGN_HEIGHT), base.fontScale)
    } else {
        base
    }

    CompositionLocalProvider(LocalDensity provides scaled) {
        ClusterScreen(
            settings = settings,
            data = ScreenData(
                now = now,
                nowMy = LocalDateTime.now(MALAYSIA),
                prayer = prayer,
                azan = azan,
                zoneLabel = "${zone.code} · ${zone.name}",
                hasAccess = hasAccess,
                track = track,
                playback = playback,
                lyrics = lyrics,
            ),
            onToggleClock = onToggleClock,
            onStopAzan = Azan::stop,
            onPrevious = model.music::previous,
            onPlayPause = model.music::playPause,
            onNext = model.music::next,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The screen itself, drawn from plain values. Used by the car and by the phone's preview. */
@Composable
fun ClusterScreen(
    settings: Settings,
    data: ScreenData,
    modifier: Modifier = Modifier,
    onToggleClock: () -> Unit = {},
    onStopAzan: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
) {
    val look = remember(settings) { CarLook(settings) }
    CompositionLocalProvider(LocalSettings provides settings, LocalLook provides look) {
        Row(
            modifier = modifier
                .background(look.palette.bg)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LeftColumn(data, onToggleClock, onStopAzan, Modifier.width(262.dp).fillMaxHeight())
            MusicPanel(
                data = data,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

// ---------------------------------------------------------------- text

private val NO_PADDING = PlatformTextStyle(includeFontPadding = false)
private val TRIM = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

private fun style(
    size: TextUnit,
    color: Color,
    family: FontFamily,
    weight: FontWeight = FontWeight.Normal,
    spacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    numbers: Boolean = false,
) = TextStyle(
    color = color,
    fontSize = size,
    fontWeight = weight,
    fontFamily = family,
    letterSpacing = spacing,
    lineHeight = lineHeight,
    fontFeatureSettings = if (numbers) "tnum" else null,
    platformStyle = NO_PADDING,
    lineHeightStyle = TRIM,
)

@Composable
private fun Line(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 1) {
    BasicText(text, modifier = modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Small capitals label in the accent colour ("NEXT · MAGHRIB IN"). */
@Composable
private fun Label(text: String, color: Color, dot: Boolean = false) {
    val look = LocalLook.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dot) {
            Box(
                Modifier
                    .padding(end = 11.dp)
                    .size(8.dp)
                    .drawBehind {
                        drawCircle(color.copy(alpha = 0.25f), radius = size.minDimension / 2 + 4.dp.toPx())
                        drawCircle(color)
                    }
            )
        }
        Line(text, style(13.sp * look.z, color, look.display, FontWeight.SemiBold, spacing = 0.13.em))
    }
}

/** The thin tick line above the next-prayer block (a 1-dp tick every 12 dp). */
private fun Modifier.ticks(color: Color) = drawBehind {
    val step = 12.dp.toPx()
    val w = 1.dp.toPx()
    val h = 6.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawRect(color, Offset(x, 0f), Size(w, h))
        x += step
    }
}

// ---------------------------------------------------------------- left: clock + prayer

@Composable
private fun LeftColumn(
    data: ScreenData,
    onToggleClock: () -> Unit,
    onStopAzan: () -> Unit,
    modifier: Modifier,
) {
    val settings = LocalSettings.current
    val look = LocalLook.current
    val p = look.palette
    val z = look.z
    val malay = settings.language == Language.MALAY
    val locale = if (malay) MALAY_LOCALE else Locale.ENGLISH
    val now = data.now
    val days: List<PrayerDay> = (data.prayer as? PrayerStatus.Ready)?.days.orEmpty()
    val today = days.firstOrNull { it.date == data.nowMy.toLocalDate() }
    val hijri = if (settings.showHijri) days.firstOrNull { it.date == now.toLocalDate() }?.hijri else null

    Column(modifier) {
        Column(Modifier.clickable(onClick = onToggleClock)) {
            if (settings.showGreeting) {
                Line(
                    greetingFor(now.hour, malay),
                    style(15.sp * z, p.muted, look.body, FontWeight.Medium),
                    Modifier.padding(bottom = 4.dp),
                )
            }
            val clockSize = 98.sp * look.clockScale
            BasicText(
                text = buildAnnotatedString {
                    append(now.format(if (settings.use24h) TIME_24 else TIME_12))
                    if (!settings.use24h) {
                        withStyle(
                            SpanStyle(
                                fontSize = clockSize * 0.26f,
                                fontFamily = look.display,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.06.em,
                            )
                        ) { append(" " + now.format(AM_PM).uppercase(Locale.ENGLISH)) }
                    }
                },
                style = style(clockSize, p.text, look.clockFamily, look.clockWeight, spacing = (-0.01).em, lineHeight = clockSize * 0.88f, numbers = true),
                maxLines = 1,
                softWrap = false,
            )
            Line(
                (now.format(DAY.withLocale(locale)) + " · " + now.format(DATE.withLocale(locale))).uppercase(locale),
                style(15.sp * z, p.text, look.display, FontWeight.SemiBold, spacing = 0.12.em),
                Modifier.padding(top = 10.dp),
            )
            if (hijri != null) {
                Line(formatHijri(hijri), style(15.sp * z, p.muted, look.body), Modifier.padding(top = 3.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        PrayerBlock(data, days, onStopAzan)

        if (today != null) {
            TimesStrip(data, today, days)
        }
    }
}

@Composable
private fun PrayerBlock(data: ScreenData, days: List<PrayerDay>, onStopAzan: () -> Unit) {
    val look = LocalLook.current
    val p = look.palette
    val z = look.z
    val nw = look.numberWidth
    val upcoming = upcomingPrayers(days, data.nowMy)
    val left = upcoming?.let { Duration.between(data.nowMy, it.next.at) }
    val azan = data.azan
    val azanOn = azan.phase != Azan.Phase.IDLE
    val soonMinutes = LocalSettings.current.azanSoonMinutes
    val soon = !azanOn && left != null && soonMinutes > 0 && left <= Duration.ofMinutes(soonMinutes.toLong())
    val shape = RoundedCornerShape(5.dp)

    when {
        azanOn -> Column(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(look.accent)
                .clickable(onClick = onStopAzan)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            val name = azan.prayer?.label.orEmpty()
            Label("AZAN · " + name.uppercase(Locale.ENGLISH), OnAccent)
            Line(
                tr("It's time for $name", "Masuk waktu $name"),
                style(26.sp * z * nw, OnAccent, look.body, FontWeight.SemiBold, lineHeight = 26.sp * z * nw * 1.1f),
                Modifier.padding(top = 4.dp),
                maxLines = 2,
            )
            Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (azan.phase == Azan.Phase.PLAYING) {
                    Box(Modifier.size(11.dp).background(OnAccent, RoundedCornerShape(2.dp)))
                }
                Line(
                    when (azan.phase) {
                        Azan.Phase.ALERT -> tr("Azan in a moment…", "Azan sebentar lagi…")
                        Azan.Phase.PAUSED -> tr("Music paused · tap to close", "Muzik dihentikan · tekan untuk tutup")
                        else -> tr("Tap to stop", "Tekan untuk henti")
                    },
                    style(14.sp * z, OnAccent, look.body, FontWeight.Medium),
                )
            }
        }

        upcoming != null && left != null -> {
            val name = upcoming.next.prayer.label.uppercase(Locale.ENGLISH)
            val box = if (soon) {
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .border(2.dp, look.accent, shape)
                    .padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 8.dp)
            } else {
                Modifier.padding(top = 14.dp).fillMaxWidth().ticks(p.line).padding(top = 14.dp)
            }
            Column(box) {
                Label(
                    if (soon) tr("AZAN SOON · $name IN", "AZAN HAMPIR · $name DALAM")
                    else tr("NEXT · $name IN", "SETERUSNYA · $name DALAM"),
                    look.accent,
                    dot = soon,
                )
                val big = 56.sp * z * nw
                BasicText(
                    text = countdown(left, big * 0.4f, p.text),
                    style = style(big, look.accent, look.display, FontWeight.SemiBold, lineHeight = big, numbers = true),
                    maxLines = 1,
                    softWrap = false,
                )
                Line(data.zoneLabel, style(13.sp * z, p.muted, look.body))
            }
        }

        else -> Column(Modifier.padding(top = 14.dp).fillMaxWidth().ticks(p.line).padding(top = 14.dp)) {
            Label(tr("PRAYER TIMES", "WAKTU SOLAT"), look.accent)
            BasicText(
                if (data.prayer is PrayerStatus.Loading) tr("Loading…", "Memuatkan…")
                else tr("No signal yet. Will retry every minute.", "Tiada isyarat. Cuba lagi setiap minit."),
                modifier = Modifier.padding(top = 6.dp),
                style = style(18.sp * z, p.text, look.body, lineHeight = 18.sp * z * 1.3f),
            )
        }
    }
}

/** "51 min", or "1 h 05 m" over an hour; the numbers big, the units small. */
private fun countdown(left: Duration, unitSize: TextUnit, unitColor: Color): AnnotatedString {
    val totalMin = (left.seconds + 59) / 60
    val h = totalMin / 60
    val m = totalMin % 60
    val unit = SpanStyle(fontSize = unitSize, color = unitColor, fontWeight = FontWeight.Medium)
    return buildAnnotatedString {
        if (h > 0) {
            append("$h")
            withStyle(unit) { append(" h  ") }
            append("%02d".format(m))
            withStyle(unit) { append(" m") }
        } else {
            append("$m")
            withStyle(unit) { append("  min") }
        }
    }
}

/** Today's five prayers in a row, the next one in the accent colour. */
@Composable
private fun TimesStrip(data: ScreenData, today: PrayerDay, days: List<PrayerDay>) {
    val look = LocalLook.current
    val p = look.palette
    val z = look.z
    val nw = look.numberWidth
    val use24h = LocalSettings.current.use24h
    val azan = data.azan
    val highlight = if (azan.phase != Azan.Phase.IDLE) azan.prayer
    else upcomingPrayers(days, data.nowMy)?.next?.takeIf { it.at.toLocalDate() == today.date }?.prayer

    Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Prayer.entries.filter { it.isMain }.forEach { prayer ->
            val time = today.times[prayer]
            val on = prayer == highlight
            val bar = if (on) look.accent else p.line
            Column(
                Modifier
                    .weight(1f)
                    .drawBehind { drawRect(bar, size = Size(size.width, 2.dp.toPx())) }
                    .padding(top = 9.dp)
            ) {
                Line(
                    if (look.upperTimes) prayer.label.uppercase(Locale.ENGLISH) else prayer.label,
                    style(12.sp * z * nw, if (on) look.accent else p.muted, look.display, FontWeight.Medium, spacing = 0.04.em),
                )
                Line(
                    time?.format(if (use24h) TIME_24 else TIME_12).orEmpty(),
                    style(19.sp * z * nw, if (on) look.accent else p.text, look.display, FontWeight.SemiBold, numbers = true),
                    Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun greetingFor(hour: Int, malay: Boolean): String = if (malay) {
    when (hour) {
        in 5..11 -> "Selamat pagi"
        in 12..13 -> "Selamat tengah hari"
        in 14..18 -> "Selamat petang"
        else -> "Selamat malam"
    }
} else {
    when (hour) {
        in 5..11 -> "Good morning"
        in 12..18 -> "Good afternoon"
        in 19..23 -> "Good evening"
        else -> "Good night"
    }
}

private val MALAY_LOCALE: Locale = Locale("ms", "MY")

// ---------------------------------------------------------------- right: music + lyrics

@Composable
private fun MusicPanel(
    data: ScreenData,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier,
) {
    val look = LocalLook.current
    val p = look.palette
    val shape = RoundedCornerShape(4.dp)
    val track = data.track
    Column(
        modifier
            .clip(shape)
            .background(p.panel)
            .border(1.dp, p.line, shape)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        when {
            !data.hasAccess -> PanelMessage(
                tr(
                    "Allow music access: open Autodroid on your phone and tap \"Allow music access\".",
                    "Benarkan akses muzik: buka Autodroid di telefon dan tekan \"Allow music access\".",
                )
            )
            track == null -> PanelMessage(
                tr(
                    "Nothing playing. Start YT Music and it will show here.",
                    "Tiada lagu dimainkan. Mainkan YT Music dan ia akan dipaparkan di sini.",
                )
            )
            LocalSettings.current.lyricsMode == LyricsMode.OFF ->
                BigPlayer(track, data.playback, onPrevious, onPlayPause, onNext)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AlbumArt(track, 66.dp, 3.dp)
                    Column(Modifier.weight(1f)) { TitleAndArtist(track, 21f, 15f) }
                    Controls(data.playback, 52.dp, 8.dp, onPrevious, onPlayPause, onNext)
                }
                Progress(track, data.playback, Modifier.padding(top = 12.dp))
                LyricsArea(data.lyrics, data.playback)
            }
        }
    }
}

@Composable
private fun ColumnScope.PanelMessage(text: String) {
    val look = LocalLook.current
    Column(
        Modifier.weight(1f).widthIn(max = 380.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Label(tr("MUSIC", "MUZIK"), look.accent)
        BasicText(text, style = style(19.sp * look.z, look.palette.text, look.body, lineHeight = 19.sp * look.z * 1.35f))
    }
}

@Composable
private fun TitleAndArtist(track: Track, titleSize: Float, artistSize: Float) {
    val look = LocalLook.current
    val z = look.z
    Line(
        track.title,
        style(titleSize.sp * z, look.palette.text, look.body, FontWeight.SemiBold, lineHeight = titleSize.sp * z * 1.15f),
        maxLines = 2,
    )
    Line(track.artist, style(artistSize.sp * z, look.palette.muted, look.body), Modifier.padding(top = 3.dp))
}

/** Lyrics turned off: big album art and bigger buttons fill the panel. */
@Composable
private fun ColumnScope.BigPlayer(
    track: Track,
    playback: Playback?,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        Modifier.weight(1f).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        AlbumArt(track, 190.dp, 4.dp)
        Column(Modifier.weight(1f)) {
            TitleAndArtist(track, 30f, 18f)
            Progress(track, playback, Modifier.padding(top = 14.dp))
            Box(Modifier.padding(top = 16.dp)) {
                Controls(playback, 60.dp, 12.dp, onPrevious, onPlayPause, onNext)
            }
        }
    }
}

@Composable
private fun AlbumArt(track: Track, size: Dp, corner: Dp) {
    val art: ImageBitmap? = remember(track.art) { track.art?.asImageBitmap() }
    val shape = RoundedCornerShape(corner)
    if (art != null) {
        Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(Modifier.size(size).background(LocalLook.current.palette.line, shape))
    }
}

@Composable
private fun Controls(
    playback: Playback?,
    size: Dp,
    gap: Dp,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val look = LocalLook.current
    val text = look.palette.text
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        RoundButton(size, null, text.copy(alpha = 0.22f), onPrevious) { Icon(PREV, text) }
        RoundButton(size, look.accent, null, onPlayPause) {
            Icon(if (playback?.isPlaying == true) PAUSE else PLAY, OnAccent)
        }
        RoundButton(size, null, text.copy(alpha = 0.22f), onNext) { Icon(NEXT, text) }
    }
}

@Composable
private fun Progress(track: Track, playback: Playback?, modifier: Modifier = Modifier) {
    if (track.durationMs <= 0) return
    val look = LocalLook.current
    val position = rememberPosition(playback)
    val fraction = (position.toFloat() / track.durationMs).coerceIn(0f, 1f)
    val numbers = style(13.sp * look.z, look.palette.muted, look.display, FontWeight.Medium, numbers = true)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Line(formatMs(position), numbers)
        Box(Modifier.weight(1f).height(3.dp).background(look.palette.line)) {
            Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(look.accent))
        }
        Line(formatMs(track.durationMs), numbers)
    }
}

@Composable
private fun ColumnScope.LyricsArea(lyrics: LyricsResult?, playback: Playback?) {
    val look = LocalLook.current
    val area = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(top = 12.dp)
        .drawBehind { drawRect(look.palette.line, size = Size(size.width, 1.dp.toPx())) }
        .padding(top = 12.dp)
    when (lyrics) {
        null, LyricsResult.Loading -> Note(tr("Finding lyrics…", "Mencari lirik…"), area)
        LyricsResult.NotFound -> Note(tr("No lyrics found", "Lirik tidak dijumpai"), area)
        is LyricsResult.Found -> Column(area, verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically)) {
            val position = rememberPosition(playback) - LocalSettings.current.lyricsOffsetMs
            SyncedLines(lyrics.lyrics, position)
        }
    }
}

@Composable
private fun Note(text: String, modifier: Modifier) {
    val look = LocalLook.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Line(text, style(17.sp * look.z, look.palette.muted, look.body))
    }
}

/** The line just sung, the current line large, and the next lines. */
@Composable
private fun SyncedLines(lyrics: Lyrics, positionMs: Long) {
    val look = LocalLook.current
    val p = look.palette
    val ly = look.lyricsScale
    val lines = lyrics.lines
    val upStyle = style(20.sp * ly, p.sub, look.body)
    if (!lyrics.synced) {
        lines.take(look.upcomingLines + 2).forEach { Line(it.text, upStyle) }
        return
    }
    val index = lyrics.indexAt(positionMs)
    val current = lines.getOrNull(index)?.text?.ifBlank { "♪" } ?: "♪"
    if (LocalSettings.current.lyricsMode == LyricsMode.CURRENT_ONLY) {
        val size = 40.sp * ly
        Line(current, style(size, p.highlight, look.body, FontWeight.SemiBold, lineHeight = size * 1.12f), maxLines = 3)
        return
    }
    Line(lines.getOrNull(index - 1)?.text.orEmpty(), style(18.sp * ly, p.dim, look.body))
    val size = 32.sp * ly
    Line(current, style(size, p.highlight, look.body, FontWeight.SemiBold, lineHeight = size * 1.12f), maxLines = 2)
    for (offset in 1..look.upcomingLines) {
        Line(lines.getOrNull(index + offset)?.text.orEmpty(), upStyle)
    }
}

/** Song position that keeps moving while music plays (updated a few times a second). */
@Composable
private fun rememberPosition(playback: Playback?): Long {
    val position by produceState(initialValue = playback?.positionNow() ?: 0L, playback) {
        while (true) {
            value = playback?.positionNow() ?: 0L
            if (playback?.isPlaying != true) break
            delay(250L)
        }
    }
    return position
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

// ---------------------------------------------------------------- buttons and icons

@Composable
private fun RoundButton(size: Dp, fill: Color?, outline: Color?, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .then(if (outline != null) Modifier.border(1.dp, outline, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Icon shapes on a 24×24 grid: a list of polygons. */
private typealias IconShape = List<List<Offset>>

private fun rect(l: Float, t: Float, r: Float, b: Float) = listOf(Offset(l, t), Offset(r, t), Offset(r, b), Offset(l, b))

private val PREV: IconShape = listOf(rect(6f, 5f, 8.2f, 19f), listOf(Offset(19.5f, 5f), Offset(19.5f, 19f), Offset(9.2f, 12f)))
private val NEXT: IconShape = listOf(rect(15.8f, 5f, 18f, 19f), listOf(Offset(4.5f, 5f), Offset(4.5f, 19f), Offset(14.8f, 12f)))
private val PAUSE: IconShape = listOf(rect(7f, 5f, 10.6f, 19f), rect(13.4f, 5f, 17f, 19f))
private val PLAY: IconShape = listOf(listOf(Offset(7f, 4.5f), Offset(7f, 19.5f), Offset(19.5f, 12f)))

@Composable
private fun Icon(shape: IconShape, color: Color) {
    Canvas(Modifier.size(22.dp)) { drawIcon(shape, color) }
}

private fun DrawScope.drawIcon(shape: IconShape, color: Color) {
    val k = size.width / 24f
    shape.forEach { points ->
        val path = Path().apply {
            points.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x * k, pt.y * k) else lineTo(pt.x * k, pt.y * k) }
            close()
        }
        drawPath(path, color)
    }
}

private val TIME_12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH)
private val TIME_24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val AM_PM: DateTimeFormatter = DateTimeFormatter.ofPattern("a", Locale.ENGLISH)
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
