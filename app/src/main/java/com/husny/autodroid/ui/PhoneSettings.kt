package com.husny.autodroid.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.husny.autodroid.azan.Azan
import com.husny.autodroid.car.PrayerStatus
import com.husny.autodroid.data.LyricLine
import com.husny.autodroid.data.Lyrics
import com.husny.autodroid.data.LyricsResult
import com.husny.autodroid.data.Playback
import com.husny.autodroid.data.Prayer
import com.husny.autodroid.data.PrayerDay
import com.husny.autodroid.data.Track
import com.husny.autodroid.data.Zone
import com.husny.autodroid.data.ZoneOption
import com.husny.autodroid.settings.ACCENTS
import com.husny.autodroid.settings.ACCENT_NAMES
import com.husny.autodroid.settings.ACCENT_ORDER
import com.husny.autodroid.settings.AzanMode
import com.husny.autodroid.settings.Background
import com.husny.autodroid.settings.ClockStyle
import com.husny.autodroid.settings.FontStyle
import com.husny.autodroid.settings.Language
import com.husny.autodroid.settings.LyricsMode
import com.husny.autodroid.settings.Settings
import com.husny.autodroid.settings.TextSize
import java.time.LocalDate
import java.time.LocalTime

/** Everything the phone settings screen shows that isn't in [Settings]. */
data class PhoneState(
    val musicAccess: Boolean = false,
    val locationAllowed: Boolean = false,
    val carSpeedAllowed: Boolean = false,
    val zone: Zone = Zone("", ""),
    val zoneManual: Boolean = false,
    val zoneFromGps: Boolean = false,
    val detecting: Boolean = false,
    val azanAudio: String = "",
    val azanCustom: Boolean = false,
    val subuhAudio: String = "",
    val subuhCustom: Boolean = false,
    val azanRunning: Boolean = false,
    val offlineMonths: String = "",
)

/** Actions the settings screen can ask the activity to do. */
class PhoneActions(
    val update: ((Settings) -> Settings) -> Unit,
    val allowMusic: () -> Unit,
    val allowLocation: () -> Unit,
    val allowCarSpeed: () -> Unit,
    val detectZone: () -> Unit,
    val useGps: () -> Unit,
    val loadZones: (onLoaded: (List<ZoneOption>?) -> Unit) -> Unit,
    val chooseZone: (ZoneOption) -> Unit,
    val chooseAzan: () -> Unit,
    val builtInAzan: () -> Unit,
    val chooseSubuh: () -> Unit,
    val sameSubuh: () -> Unit,
    val testAzan: () -> Unit,
    val stopAzan: () -> Unit,
)

/** The phone screen: changes apply to the car screen live. Styled to match the car screen. */
@Composable
fun PhoneSettings(settings: Settings, state: PhoneState, actions: PhoneActions) {
    val accent = Color(settings.accent)
    var zonePicker by remember { mutableStateOf(false) }
    val set = actions.update

    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = Barlow, color = PhoneColors.Text)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PhoneColors.Background)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Autodroid", fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 32.sp)
                Text("Changes show on the car screen straight away", color = PhoneColors.TextMuted, fontSize = 14.sp)
            }

            // ------------------------------------------------------------ setup
            Section("SETUP", accent) {
                Item("Music access", if (state.musicAccess) "Allowed" else "Needed to show and control YT Music") {
                    if (!state.musicAccess) AccentButton("Allow", accent, actions.allowMusic)
                    else StatusText("OK", accent)
                }
                Item("Location", if (state.locationAllowed) "Allowed" else "Needed to pick the prayer zone from GPS") {
                    if (!state.locationAllowed) AccentButton("Allow", accent, actions.allowLocation)
                    else StatusText("OK", accent)
                }
                Item("Car speed", if (state.carSpeedAllowed) "Allowed" else "Lets video play only when the car is stopped", last = true) {
                    if (!state.carSpeedAllowed) AccentButton("Allow", accent, actions.allowCarSpeed)
                    else StatusText("OK", accent)
                }
            }

            // ------------------------------------------------------------ prayer & azan
            Section("PRAYER TIMES & AZAN", accent) {
                Stacked("Prayer zone") {
                    Choices(
                        listOf("Auto (GPS)", "Choose zone"),
                        if (state.zoneManual) 1 else 0,
                        accent,
                    ) { i -> if (i == 0) actions.useGps() else zonePicker = true }
                    Text(
                        "${state.zone.code} · ${state.zone.name}" + when {
                            state.zoneManual -> " (chosen)"
                            state.zoneFromGps -> " (from GPS)"
                            else -> " (default)"
                        },
                        color = PhoneColors.TextSoft, fontSize = 14.sp,
                    )
                    if (state.zoneManual) {
                        PlainButton("Change zone") { zonePicker = true }
                    } else if (state.locationAllowed) {
                        PlainButton(if (state.detecting) "Finding your zone…" else "Detect zone now", enabled = !state.detecting, onClick = actions.detectZone)
                    }
                }
                if (!state.zoneManual) {
                    Item("Notify when zone changes", "Message on the car screen") {
                        Toggle(settings.notifyZoneChange, accent) { v -> set { it.copy(notifyZoneChange = v) } }
                    }
                }
                Stacked("\"Azan soon\" highlight") {
                    val options = listOf(0, 5, 10, 15)
                    Choices(
                        options.map { if (it == 0) "Off" else "$it min before" },
                        options.indexOf(settings.azanSoonMinutes).coerceAtLeast(0),
                        accent,
                    ) { i -> set { it.copy(azanSoonMinutes = options[i]) } }
                }
                Stacked("At prayer time") {
                    val modes = listOf(AzanMode.FULL, AzanMode.PAUSE_ONLY, AzanMode.OFF)
                    Choices(
                        listOf("Chimes, pause, azan, resume", "Chimes & pause only", "Do nothing"),
                        modes.indexOf(settings.azanMode),
                        accent,
                    ) { i -> set { it.copy(azanMode = modes[i]) } }
                }
                if (settings.azanMode == AzanMode.FULL) {
                    Stacked("Azan audio") {
                        Text(state.azanAudio, color = PhoneColors.TextSoft, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PlainButton("Choose file", onClick = actions.chooseAzan)
                            if (state.azanCustom) PlainButton("Use built-in", onClick = actions.builtInAzan)
                        }
                    }
                    Stacked("Subuh azan audio") {
                        Text(state.subuhAudio, color = PhoneColors.TextSoft, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PlainButton("Choose file", onClick = actions.chooseSubuh)
                            if (state.subuhCustom) PlainButton("Same as others", onClick = actions.sameSubuh)
                        }
                    }
                    Stacked("Azan volume · ${settings.azanVolumePercent}%") {
                        SettingSlider(settings.azanVolumePercent.toFloat(), 10f..100f, 8, accent) { v ->
                            set { it.copy(azanVolumePercent = v.toInt()) }
                        }
                    }
                    Item("Resume music after azan", "1 second after it ends or is stopped") {
                        Toggle(settings.resumeMusic, accent) { v -> set { it.copy(resumeMusic = v) } }
                    }
                }
                if (settings.azanMode != AzanMode.OFF) {
                    Box(Modifier.padding(vertical = 12.dp)) {
                        if (state.azanRunning) AccentButton("Stop", accent, actions.stopAzan)
                        else AccentButton("Test now", accent, actions.testAzan)
                    }
                }
            }

            // ------------------------------------------------------------ clock & date
            Section("CLOCK & DATE", accent) {
                Item("24-hour clock", "Also switches when you tap the clock in the car") {
                    Toggle(settings.use24h, accent) { v -> set { it.copy(use24h = v) } }
                }
                Item("Show Hijri date") {
                    Toggle(settings.showHijri, accent) { v -> set { it.copy(showHijri = v) } }
                }
                Item("Show greeting", "Good morning / Selamat pagi") {
                    Toggle(settings.showGreeting, accent) { v -> set { it.copy(showGreeting = v) } }
                }
                Stacked("Car screen language", last = true) {
                    val langs = listOf(Language.ENGLISH, Language.MALAY)
                    Choices(listOf("English", "Bahasa Melayu"), langs.indexOf(settings.language), accent) { i ->
                        set { it.copy(language = langs[i]) }
                    }
                }
            }

            // ------------------------------------------------------------ lyrics
            Section("LYRICS", accent) {
                Stacked("Lyrics", last = settings.lyricsMode == LyricsMode.OFF) {
                    val modes = listOf(LyricsMode.SYNCED, LyricsMode.CURRENT_ONLY, LyricsMode.OFF)
                    Choices(listOf("Synced lines", "Current line only", "Off"), modes.indexOf(settings.lyricsMode), accent) { i ->
                        set { it.copy(lyricsMode = modes[i]) }
                    }
                }
                if (settings.lyricsMode != LyricsMode.OFF) {
                    Stacked("Lyrics size") {
                        Choices(listOf("Small", "Medium", "Large"), settings.lyricsSize, accent) { i ->
                            set { it.copy(lyricsSize = i) }
                        }
                    }
                    Stacked("Lyrics timing", hint = "If lyrics run ahead of or behind the song", last = true) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PlainButton("Earlier") {
                                set { it.copy(lyricsOffsetMs = (it.lyricsOffsetMs - 250).coerceAtLeast(-3000)) }
                            }
                            Text(formatOffset(settings.lyricsOffsetMs), color = PhoneColors.TextSoft, fontSize = 14.sp)
                            PlainButton("Later") {
                                set { it.copy(lyricsOffsetMs = (it.lyricsOffsetMs + 250).coerceAtMost(3000)) }
                            }
                        }
                        if (settings.lyricsOffsetMs != 0) {
                            TextButton(onClick = { set { it.copy(lyricsOffsetMs = 0) } }) { Text("Reset", color = accent) }
                        }
                    }
                }
            }

            // ------------------------------------------------------------ car screen look
            Section("CAR SCREEN LOOK", accent) {
                Stacked("Preview", hint = "Updates as you change the settings below") {
                    CarPreview(settings, "${state.zone.code} · ${state.zone.name}")
                }
                Stacked("Font style") {
                    val styles = listOf(
                        Triple(FontStyle.CLUSTER, "Cluster", BarlowCondensed to FontWeight.Medium),
                        Triple(FontStyle.ROUNDED, "Rounded", Outfit to FontWeight.Light),
                        Triple(FontStyle.CLASSIC, "Classic", FontFamily.Default to FontWeight.Light),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        styles.forEach { (style, name, font) ->
                            val on = settings.fontStyle == style
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(if (on) 2.dp else 1.dp, if (on) accent else PhoneColors.Outline, RoundedCornerShape(6.dp))
                                    .clickable { set { it.copy(fontStyle = style) } }
                                    .padding(top = 8.dp, bottom = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("18:02", fontFamily = font.first, fontWeight = font.second, fontSize = 24.sp)
                                Text(name, color = PhoneColors.TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Stacked("Text size") {
                    val sizes = TextSize.entries
                    Choices(listOf("Compact", "Standard", "Large"), sizes.indexOf(settings.textSize), accent) { i ->
                        set { it.copy(textSize = sizes[i]) }
                    }
                }
                Stacked("Background") {
                    val names = listOf("Graphite", "Black", "Navy", "Forest")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Background.entries.forEachIndexed { i, bg ->
                            val on = settings.background == bg
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .then(if (on) Modifier.border(2.dp, accent, RoundedCornerShape(6.dp)) else Modifier)
                                    .padding(if (on) 2.dp else 0.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(paletteFor(bg).panel)
                                    .border(1.dp, PhoneColors.Outline, RoundedCornerShape(5.dp))
                                    .clickable { set { it.copy(background = bg) } }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = Alignment.BottomStart,
                            ) { Text(names[i], fontSize = 11.5.sp) }
                        }
                    }
                }
                Stacked("Accent colour", hint = ACCENT_NAMES.getOrElse(settings.accentIndex) { "" }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        ACCENT_ORDER.forEach { i ->
                            val on = i == settings.accentIndex
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .then(if (on) Modifier.border(2.dp, PhoneColors.Text, CircleShape) else Modifier)
                                    .padding(4.dp)
                                    .background(Color(ACCENTS[i]), CircleShape)
                                    .clip(CircleShape)
                                    .clickable { set { it.copy(accentIndex = i) } }
                            )
                        }
                    }
                }
                Stacked("Clock style", last = true) {
                    val styles = listOf(ClockStyle.LIGHT, ClockStyle.BOLD, ClockStyle.MONO)
                    Choices(listOf("Light", "Bold", "Monospace"), styles.indexOf(settings.clockStyle), accent) { i ->
                        set { it.copy(clockStyle = styles[i]) }
                    }
                }
            }

            // ------------------------------------------------------------ video
            Section("VIDEO · PARKED ONLY", accent) {
                Stacked("YouTube on the car screen", last = true) {
                    Text(
                        "Tap the video button on the car screen, or open a video in the YouTube app here and choose " +
                            "Share › Play on car screen. Video only opens while the car is stopped and closes " +
                            "as soon as it moves. Speed comes from the car, or from this phone's GPS if the car " +
                            "doesn't share it.",
                        color = PhoneColors.TextSoft, fontSize = 14.sp, lineHeight = 19.sp,
                    )
                }
            }

            // ------------------------------------------------------------ about
            Section("OFFLINE & SOURCES", accent) {
                Stacked("Prayer times saved on this phone", last = true) {
                    Text(state.offlineMonths.ifEmpty { "None yet (saved the first time the car screen opens)" }, color = PhoneColors.TextSoft, fontSize = 14.sp)
                    Text(
                        "Prayer times: JAKIM e-solat (backup: waktusolat.app). Zones: waktusolat.app. " +
                            "Lyrics: Lyrica (open source). Built-in azan: Adzan Mekkah, Abdul Aziz Zahrani. " +
                            "Fonts: Barlow and Outfit (SIL Open Font License).",
                        color = PhoneColors.TextMuted, fontSize = 12.5.sp, lineHeight = 17.sp,
                    )
                }
            }
        }

        if (zonePicker) {
            ZonePicker(
                accent = accent,
                load = actions.loadZones,
                onPick = { option ->
                    zonePicker = false
                    actions.chooseZone(option)
                },
                onDismiss = { zonePicker = false },
            )
        }
    }
}

private fun formatOffset(ms: Int): String = when {
    ms == 0 -> "On time"
    ms < 0 -> "%.2f s earlier".format(-ms / 1000f)
    else -> "%.2f s later".format(ms / 1000f)
}

// ---------------------------------------------------------------- live preview

/** A small copy of the car screen with example content, drawn with the current settings. */
@Composable
private fun CarPreview(settings: Settings, zoneLabel: String) {
    val data = remember(zoneLabel) { previewData(zoneLabel) }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(DESIGN_WIDTH / DESIGN_HEIGHT)
            .clip(RoundedCornerShape(4.dp))
    ) {
        val scale = constraints.maxWidth / DESIGN_WIDTH
        CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
            ClusterScreen(settings, data, Modifier.requiredSize(DESIGN_WIDTH.dp, DESIGN_HEIGHT.dp))
        }
    }
}

private fun previewData(zoneLabel: String): ScreenData {
    val today = LocalDate.now()
    val now = today.atTime(18, 2)
    val day = PrayerDay(
        date = today,
        hijri = "1448-06-11",
        times = mapOf(
            Prayer.FAJR to LocalTime.of(5, 38),
            Prayer.DHUHR to LocalTime.of(12, 53),
            Prayer.ASR to LocalTime.of(16, 16),
            Prayer.MAGHRIB to LocalTime.of(18, 53),
            Prayer.ISHA to LocalTime.of(20, 6),
        ),
    )
    val lines = listOf(
        "Kubiarkan malam membawa resah", "Dalam sujud aku temui tenang", "Doa yang kusimpan sejak lama",
        "Perlahan menjadi cahaya", "Menuntun aku pulang",
    ).mapIndexed { i, text -> LyricLine(if (i == 0) 0L else 50_000L + (i - 1) * 10_000L, text) }
    return ScreenData(
        now = now,
        nowMy = now,
        prayer = PrayerStatus.Ready(listOf(day)),
        azan = Azan.State(),
        zoneLabel = zoneLabel,
        hasAccess = true,
        track = Track("Song title", "Artist", 268_000L, null, ""),
        // Speed 0 keeps the example song standing still at 0:56 while showing "playing".
        playback = Playback(isPlaying = true, positionMs = 56_000L, measuredAt = SystemClock.elapsedRealtime(), speed = 0f),
        lyrics = LyricsResult.Found(Lyrics(lines, synced = true)),
    )
}

// ---------------------------------------------------------------- zone picker

@Composable
private fun ZonePicker(
    accent: Color,
    load: ((List<ZoneOption>?) -> Unit) -> Unit,
    onPick: (ZoneOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var zones by remember { mutableStateOf<List<ZoneOption>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { load { list -> if (list == null) failed = true else zones = list } }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PhoneColors.Card,
        shape = RoundedCornerShape(8.dp),
        title = { Text("Choose prayer zone", fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, color = PhoneColors.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search state or district") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val list = zones
                when {
                    failed -> Text("Couldn't load the zone list. Check your internet and try again.", color = PhoneColors.TextSoft)
                    list == null -> Text("Loading zones…", color = PhoneColors.TextSoft)
                    else -> {
                        val q = query.trim().lowercase()
                        val shown = if (q.isEmpty()) list else list.filter {
                            it.code.lowercase().contains(q) || it.state.lowercase().contains(q) || it.districts.lowercase().contains(q)
                        }
                        LazyColumn(Modifier.heightIn(max = 380.dp)) {
                            items(shown, key = { it.code }) { z ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(z) }
                                        .padding(vertical = 10.dp)
                                ) {
                                    Text("${z.code} · ${z.state}", color = accent, fontSize = 14.sp)
                                    Text(z.districts, color = PhoneColors.TextSoft, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = accent) } },
    )
}

// ---------------------------------------------------------------- building blocks

/** Section label over a tick line, then a card holding the rows. */
@Composable
private fun Section(title: String, accent: Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = accent,
            fontFamily = BarlowCondensed,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.14.em,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val step = 10.dp.toPx()
                    var x = 0f
                    while (x < size.width) {
                        drawRect(PhoneColors.Ticks, Offset(x, 0f), Size(1.dp.toPx(), 5.dp.toPx()))
                        x += step
                    }
                }
                .padding(top = 9.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(PhoneColors.Card)
                .border(1.dp, PhoneColors.CardBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 14.dp)
        ) { content() }
    }
}

/** A row: label (and optional hint) on the left, a control on the right. */
@Composable
private fun Item(label: String, hint: String? = null, last: Boolean = false, control: @Composable () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (hint != null) Text(hint, color = PhoneColors.TextMuted, fontSize = 13.sp, lineHeight = 17.sp)
            }
            control()
        }
        if (!last) Divider()
    }
}

/** A label with its controls underneath (for choices that need the full width). */
@Composable
private fun Stacked(label: String, hint: String? = null, last: Boolean = false, content: @Composable () -> Unit) {
    Column {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (hint != null) Text(hint, color = PhoneColors.TextMuted, fontSize = 13.sp, lineHeight = 17.sp)
            }
            content()
        }
        if (!last) Divider()
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(PhoneColors.CardBorder))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(labels: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                color = if (on) OnAccent else PhoneColors.Text,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (on) accent else Color.Transparent)
                    .border(1.dp, if (on) accent else PhoneColors.Outline, RoundedCornerShape(5.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun Toggle(checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(if (checked) accent else PhoneColors.ToggleOff)
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = if (checked) 21.dp else 3.dp)
                .size(20.dp)
                .background(if (checked) OnAccent else PhoneColors.TextMuted, CircleShape)
        )
    }
}

@Composable
private fun SettingSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, accent: Color, onChange: (Float) -> Unit) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = PhoneColors.ToggleOff,
            activeTickColor = OnAccent.copy(alpha = 0.4f),
            inactiveTickColor = PhoneColors.TextMuted,
        ),
    )
}

@Composable
private fun PlainButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        color = if (enabled) PhoneColors.Text else PhoneColors.TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .border(1.dp, PhoneColors.Outline, RoundedCornerShape(5.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun AccentButton(label: String, accent: Color, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = OnAccent,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun StatusText(text: String, accent: Color) {
    Text(text, color = accent, fontFamily = BarlowCondensed, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.1.em)
}
