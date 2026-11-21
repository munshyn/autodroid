package com.husny.autodroid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.husny.autodroid.azan.Azan
import com.husny.autodroid.car.DashboardModel
import com.husny.autodroid.settings.ClockStyle
import com.husny.autodroid.settings.Language
import com.husny.autodroid.settings.LyricsMode
import com.husny.autodroid.settings.Settings
import com.husny.autodroid.settings.SettingsStore
import com.husny.autodroid.car.PrayerStatus
import com.husny.autodroid.car.SurfaceInfo
import com.husny.autodroid.data.Lyrics
import com.husny.autodroid.data.LyricsResult
import com.husny.autodroid.data.MALAYSIA
import com.husny.autodroid.data.Playback
import com.husny.autodroid.data.Prayer
import com.husny.autodroid.data.PrayerDay
import com.husny.autodroid.data.Track
import com.husny.autodroid.data.YT_MUSIC_PACKAGE
import com.husny.autodroid.data.formatHijri
import com.husny.autodroid.data.upcomingPrayers
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The layout is designed for an 800×400 screen and scaled to whatever the car reports. */
private const val DESIGN_WIDTH = 800f
private const val DESIGN_HEIGHT = 400f

/** Settings from the phone, available to every part of the car screen. */
private val LocalSettings = staticCompositionLocalOf { Settings() }

/** English or Malay, following the phone setting. */
@Composable
private fun tr(en: String, ms: String): String =
    if (LocalSettings.current.language == Language.MALAY) ms else en

@Composable
private fun accentColor(): Color = Color(LocalSettings.current.accent)

/**
 * The car screen: clock and prayer times on top, music with lyrics across the full width below.
 */
@Composable
fun CarDashboard(
    info: SurfaceInfo,
    model: DashboardModel,
    onToggleClock: () -> Unit,
) {
    val settings by SettingsStore.settings.collectAsState()
    val use24h = settings.use24h
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
    // the cards stretch to use any extra room.
    val scaled = if (info.width > 0 && info.height > 0) {
        Density(minOf(info.width / DESIGN_WIDTH, info.height / DESIGN_HEIGHT), base.fontScale)
    } else {
        base
    }

    CompositionLocalProvider(LocalDensity provides scaled, LocalSettings provides settings) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (settings.amoledBlack) Color.Black else AutodroidColors.Background)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ClockCard(
                        now = now,
                        use24h = use24h,
                        hijri = if (!settings.showHijri) null else (prayer as? PrayerStatus.Ready)?.days
                            ?.firstOrNull { it.date == now.toLocalDate() }?.hijri,
                        onToggle = onToggleClock,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    PrayerCard(
                        status = prayer,
                        azan = azan,
                        onStopAzan = Azan::stop,
                        now = now,
                        use24h = use24h,
                        zoneLabel = "${zone.code} · ${zone.name}",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                MusicCard(
                    hasAccess = hasAccess,
                    track = track,
                    playback = playback,
                    lyrics = lyrics,
                    onPrevious = model.music::previous,
                    onPlayPause = model.music::playPause,
                    onNext = model.music::next,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- clock

@Composable
private fun ClockCard(
    now: LocalDateTime,
    use24h: Boolean,
    hijri: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val malay = settings.language == Language.MALAY
    val locale = if (malay) MALAY_LOCALE else Locale.ENGLISH
    val clockWeight = if (settings.clockStyle == ClockStyle.BOLD) FontWeight.SemiBold else FontWeight.Light
    val clockFamily = if (settings.clockStyle == ClockStyle.MONO) FontFamily.Monospace else FontFamily.Default
    Row(
        modifier = modifier.clickable(onClick = onToggle).padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                text = now.format(if (use24h) TIME_24 else TIME_12),
                style = TextStyle(
                    color = AutodroidColors.Text,
                    fontSize = if (settings.clockStyle == ClockStyle.MONO) 72.sp else 84.sp,
                    fontWeight = clockWeight,
                    fontFamily = clockFamily,
                    letterSpacing = if (settings.clockStyle == ClockStyle.MONO) (-4).sp else (-3).sp,
                    lineHeight = 84.sp,
                ),
                maxLines = 1,
            )
            if (!use24h) {
                BasicText(
                    text = now.format(AM_PM).uppercase(Locale.ENGLISH),
                    modifier = Modifier.padding(start = 4.dp, bottom = 14.dp),
                    style = TextStyle(color = AutodroidColors.Text, fontSize = 20.sp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (settings.showGreeting) {
                BasicText(
                    text = greetingFor(now.hour, malay),
                    style = TextStyle(color = AutodroidColors.TextMuted, fontSize = 13.sp, letterSpacing = 2.sp),
                    maxLines = 1,
                )
            }
            BasicText(
                text = now.format(DAY.withLocale(locale)).uppercase(locale),
                style = TextStyle(color = AutodroidColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
                maxLines = 1,
            )
            BasicText(
                text = now.format(DATE.withLocale(locale)).uppercase(locale),
                style = TextStyle(color = AutodroidColors.TextSoft, fontSize = 18.sp, letterSpacing = 1.sp),
                maxLines = 1,
            )
            if (hijri != null) {
                BasicText(
                    text = formatHijri(hijri),
                    style = TextStyle(color = AutodroidColors.TextMuted, fontSize = 15.sp),
                    maxLines = 2,
                )
            }
        }
    }
}

private fun greetingFor(hour: Int, malay: Boolean): String = if (malay) {
    when (hour) {
        in 5..11 -> "SELAMAT PAGI"
        in 12..13 -> "SELAMAT TENGAH HARI"
        in 14..18 -> "SELAMAT PETANG"
        else -> "SELAMAT MALAM"
    }
} else {
    when (hour) {
        in 5..11 -> "GOOD MORNING"
        in 12..18 -> "GOOD AFTERNOON"
        in 19..23 -> "GOOD EVENING"
        else -> "GOOD NIGHT"
    }
}

private val MALAY_LOCALE: Locale = Locale("ms", "MY")

// ---------------------------------------------------------------- prayer

@Composable
private fun PrayerCard(
    status: PrayerStatus,
    azan: Azan.State,
    onStopAzan: () -> Unit,
    now: LocalDateTime,
    use24h: Boolean,
    zoneLabel: String,
    modifier: Modifier = Modifier,
) {
    val days: List<PrayerDay> = (status as? PrayerStatus.Ready)?.days.orEmpty()
    // Prayer times are Malaysian time; compare against Malaysian "now" too. (`now` ticking each
    // second is what recomposes this card.)
    @Suppress("UNUSED_VARIABLE") val tick = now
    val nowMy = LocalDateTime.now(MALAYSIA)
    val upcoming = upcomingPrayers(days, nowMy)
    val left = upcoming?.let { Duration.between(nowMy, it.next.at) }
    val azanOn = azan.phase != Azan.Phase.IDLE
    val soonMinutes = LocalSettings.current.azanSoonMinutes
    val soon = azanOn || (left != null && soonMinutes > 0 && left <= Duration.ofMinutes(soonMinutes.toLong()))
    val today = days.firstOrNull { it.date == nowMy.toLocalDate() }

    val accent = if (soon) AutodroidColors.Alert else accentColor()
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .then(if (azanOn) Modifier.clickable(onClick = onStopAzan) else Modifier)
            .background(if (soon) AutodroidColors.AlertPanel else AutodroidColors.Panel)
            .border(1.dp, if (soon) AutodroidColors.AlertBorder else AutodroidColors.PanelBorder, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        ) {
            when {
                azanOn -> {
                    CardLabel("AZAN · ${azan.prayer?.label?.uppercase(Locale.ENGLISH).orEmpty()}", accent, AutodroidColors.AlertText)
                    BasicText(
                        text = tr("It's time for", "Masuk waktu") + "\n${azan.prayer?.label.orEmpty()}",
                        style = TextStyle(color = AutodroidColors.Text, fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.Medium),
                    )
                    BasicText(
                        text = when (azan.phase) {
                            Azan.Phase.ALERT -> tr("Azan in a moment…", "Azan sebentar lagi…")
                            Azan.Phase.PAUSED -> tr("Music paused · tap to close", "Muzik dihentikan · tekan untuk tutup")
                            else -> tr("Tap to stop", "Tekan untuk henti")
                        },
                        style = TextStyle(color = AutodroidColors.AlertText, fontSize = 15.sp),
                    )
                }
                upcoming != null && left != null -> {
                    CardLabel(
                        if (soon) tr("AZAN SOON", "AZAN HAMPIR") else tr("NEXT PRAYER", "WAKTU SETERUSNYA"),
                        accent,
                        if (soon) AutodroidColors.AlertText else AutodroidColors.TextLabel,
                    )
                    BasicText(
                        text = upcoming.next.prayer.label + tr(" in", " dalam"),
                        style = TextStyle(color = AutodroidColors.TextSoft, fontSize = 22.sp),
                        maxLines = 1,
                    )
                    BasicText(
                        text = formatLeft(left),
                        style = TextStyle(
                            color = if (soon) AutodroidColors.AlertText else AutodroidColors.Text,
                            fontSize = 38.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                    BasicText(
                        text = zoneLabel,
                        style = TextStyle(color = AutodroidColors.TextMuted, fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                status is PrayerStatus.Loading -> Message(tr("PRAYER TIMES", "WAKTU SOLAT"), tr("Loading…", "Memuatkan…"))
                else -> Message(
                    tr("PRAYER TIMES", "WAKTU SOLAT"),
                    tr("No signal yet.\nWill retry every minute.", "Tiada isyarat.\nCuba lagi setiap minit."),
                )
            }
        }
        if (today != null) {
            TodayTimes(
                day = today,
                highlight = if (azanOn) azan.prayer else upcoming?.next?.takeIf { it.at.toLocalDate() == today.date }?.prayer,
                highlightColor = accent,
                nowMy = nowMy,
                use24h = use24h,
            )
        }
    }
}

@Composable
private fun CardLabel(text: String, bar: Color, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 14.dp).background(bar, RoundedCornerShape(2.dp)))
        BasicText(
            text = text,
            modifier = Modifier.padding(start = 8.dp),
            style = TextStyle(color = color, fontSize = 13.sp, letterSpacing = 2.sp),
            maxLines = 1,
        )
    }
}

/** Today's five prayers: past ones dimmed, the next one highlighted. */
@Composable
private fun TodayTimes(
    day: PrayerDay,
    highlight: Prayer?,
    highlightColor: Color,
    nowMy: LocalDateTime,
    use24h: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Prayer.entries.filter { it.isMain }.forEach { prayer ->
            val time = day.times[prayer] ?: return@forEach
            val isNext = prayer == highlight
            val past = !isNext && LocalDateTime.of(day.date, time).isBefore(nowMy)
            val color = when {
                isNext -> highlightColor
                past -> AutodroidColors.LyricPast
                else -> AutodroidColors.TextSoft
            }
            val weight = if (isNext) FontWeight.SemiBold else FontWeight.Normal
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    prayer.label,
                    modifier = Modifier.width(66.dp),
                    style = TextStyle(color = color, fontSize = 17.sp, fontWeight = weight),
                )
                BasicText(
                    time.format(if (use24h) TIME_24 else TIME_12),
                    style = TextStyle(color = color, fontSize = 17.sp, fontWeight = weight, textAlign = TextAlign.End),
                    modifier = Modifier.width(46.dp),
                )
            }
        }
    }
}

private fun formatLeft(d: Duration): String {
    val totalMin = (d.seconds + 59) / 60
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        else -> "$m min"
    }
}

// ---------------------------------------------------------------- music + lyrics

@Composable
private fun MusicCard(
    hasAccess: Boolean,
    track: Track?,
    playback: Playback?,
    lyrics: LyricsResult?,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(AutodroidColors.MusicPanel)
            .border(1.dp, AutodroidColors.PanelBorder, shape)
    ) {
        when {
            !hasAccess -> Message(
                tr("MUSIC", "MUZIK"),
                tr(
                    "Allow music access: open Autodroid on your phone and tap \"Allow music access\".",
                    "Benarkan akses muzik: buka Autodroid di telefon dan tekan \"Allow music access\".",
                ),
                Modifier.padding(18.dp),
            )
            track == null -> Message(
                tr("MUSIC", "MUZIK"),
                tr("Nothing playing. Start YT Music and it will show here.", "Tiada lagu dimainkan. Mainkan YT Music dan ia akan dipaparkan di sini."),
                Modifier.padding(18.dp),
            )
            else -> Row(Modifier.fillMaxSize()) {
                val showLyrics = LocalSettings.current.lyricsMode != LyricsMode.OFF
                PlayerHalf(
                    track = track,
                    playback = playback,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                if (showLyrics) {
                    LyricsPane(
                        lyrics = lyrics,
                        playback = playback,
                        modifier = Modifier.weight(1.5f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerHalf(
    track: Track,
    playback: Playback?,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = rememberPosition(playback)
    val art: ImageBitmap? = remember(track.art) { track.art?.asImageBitmap() }

    Box(modifier) {
        if (art != null) {
            // Album art fills the background, only lightly softened so it's easy to recognise.
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(3.dp),
            )
        }
        // Darker at the top and bottom where the text and buttons sit, clearer in the middle.
        // How dark follows the "Album art shade" setting.
        val shade = LocalSettings.current.artShadePercent / 100f
        val strong = AutodroidColors.Background.copy(alpha = (0.25f + 0.7f * shade).coerceAtMost(0.95f))
        val light = AutodroidColors.Background.copy(alpha = 0.45f * shade)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to strong, 0.35f to light, 0.6f to light, 1f to strong)
            )
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(
                    text = track.title,
                    style = TextStyle(color = AutodroidColors.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, shadow = TEXT_SHADOW),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText(
                    text = track.artist,
                    style = TextStyle(color = AutodroidColors.OnArt, fontSize = 17.sp, shadow = TEXT_SHADOW),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (track.durationMs > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val fraction = (position.toFloat() / track.durationMs).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(5.dp).background(AutodroidColors.Track, RoundedCornerShape(3.dp))) {
                            Box(
                                Modifier.fillMaxWidth(fraction).height(5.dp)
                                    .background(AutodroidColors.Text, RoundedCornerShape(3.dp))
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            BasicText(formatMs(position), style = TextStyle(color = AutodroidColors.OnArt, fontSize = 14.sp, shadow = TEXT_SHADOW))
                            BasicText(formatMs(track.durationMs), style = TextStyle(color = AutodroidColors.OnArt, fontSize = 14.sp, shadow = TEXT_SHADOW))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundButton(50.dp, AutodroidColors.ButtonOnArt, onPrevious) { SkipIcon(forward = false, color = AutodroidColors.Text) }
                    RoundButton(60.dp, AutodroidColors.Text, onPlayPause) {
                        if (playback?.isPlaying == true) PauseIcon(AutodroidColors.Background) else PlayIcon(AutodroidColors.Background)
                    }
                    RoundButton(50.dp, AutodroidColors.ButtonOnArt, onNext) { SkipIcon(forward = true, color = AutodroidColors.Text) }
                }
            }
        }
    }
}

private val TEXT_SHADOW = Shadow(color = Color(0xCC000000), offset = Offset(0f, 1f), blurRadius = 6f)

@Composable
private fun LyricsPane(lyrics: LyricsResult?, playback: Playback?, modifier: Modifier = Modifier) {
    val position = rememberPosition(playback) - LocalSettings.current.lyricsOffsetMs
    Column(
        modifier = modifier.padding(start = 22.dp, end = 18.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        when (lyrics) {
            null, LyricsResult.Loading -> CenterNote(tr("Finding lyrics…", "Mencari lirik…"))
            LyricsResult.NotFound -> CenterNote(tr("No lyrics found", "Lirik tidak dijumpai"))
            is LyricsResult.Found -> SyncedLines(lyrics.lyrics, position)
        }
    }
}

/** One line before, the current line large, and the next lines, filling the pane. */
@Composable
private fun SyncedLines(lyrics: Lyrics, positionMs: Long) {
    val lines = lyrics.lines
    val settings = LocalSettings.current
    val scale = when (settings.lyricsSize) { 0 -> 0.85f; 2 -> 1.2f; else -> 1f }
    val small = (19 * scale).sp
    val big = (30 * scale).sp
    val bigLine = (34 * scale).sp
    val currentOnly = settings.lyricsMode == LyricsMode.CURRENT_ONLY
    if (!lyrics.synced) {
        lines.take(6).forEach {
            BasicText(
                it.text,
                style = TextStyle(color = AutodroidColors.TextSoft, fontSize = small),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        return
    }
    val index = lyrics.indexAt(positionMs)
    val previous = lines.getOrNull(index - 1)?.text.orEmpty()
    val current = lines.getOrNull(index)?.text?.ifBlank { "♪" } ?: "♪"
    if (currentOnly) {
        BasicText(
            current,
            style = TextStyle(color = AutodroidColors.Text, fontSize = big * 1.15f, fontWeight = FontWeight.SemiBold, lineHeight = bigLine * 1.15f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    BasicText(
        previous,
        style = TextStyle(color = AutodroidColors.LyricPast, fontSize = small),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    BasicText(
        current,
        style = TextStyle(color = AutodroidColors.Text, fontSize = big, fontWeight = FontWeight.SemiBold, lineHeight = bigLine),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    for (offset in 1..3) {
        BasicText(
            lines.getOrNull(index + offset)?.text.orEmpty(),
            style = TextStyle(
                color = if (offset == 1) AutodroidColors.LyricNext else AutodroidColors.LyricPast,
                fontSize = small,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

// ---------------------------------------------------------------- small pieces

@Composable
private fun Message(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(title, style = TextStyle(color = AutodroidColors.TextLabel, fontSize = 13.sp, letterSpacing = 2.sp))
        BasicText(body, style = TextStyle(color = AutodroidColors.TextSoft, fontSize = 18.sp, lineHeight = 23.sp))
    }
}

@Composable
private fun CenterNote(text: String) {
    BasicText(text, style = TextStyle(color = AutodroidColors.TextMuted, fontSize = 20.sp))
}

@Composable
private fun RoundButton(size: Dp, color: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PlayIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val p = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.15f)
            lineTo(size.width * 0.85f, size.height * 0.5f)
            lineTo(size.width * 0.28f, size.height * 0.85f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
private fun PauseIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.5.dp.toPx()
        drawLine(color, Offset(size.width * 0.37f, size.height * 0.2f), Offset(size.width * 0.37f, size.height * 0.8f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.63f, size.height * 0.2f), Offset(size.width * 0.63f, size.height * 0.8f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun SkipIcon(forward: Boolean, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val tip = if (forward) w * 0.65f else w * 0.35f
        val back = if (forward) w * 0.2f else w * 0.8f
        val bar = if (forward) w * 0.8f else w * 0.2f
        val p = Path().apply {
            moveTo(back, h * 0.18f)
            lineTo(tip, h * 0.5f)
            lineTo(back, h * 0.82f)
            close()
        }
        drawPath(p, color)
        drawLine(color, Offset(bar, h * 0.2f), Offset(bar, h * 0.8f), 2.dp.toPx(), StrokeCap.Round)
    }
}

private val TIME_12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH)
private val TIME_12_AMPM: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val TIME_24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val AM_PM: DateTimeFormatter = DateTimeFormatter.ofPattern("a", Locale.ENGLISH)
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)
