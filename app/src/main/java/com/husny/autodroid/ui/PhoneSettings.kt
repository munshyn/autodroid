package com.husny.autodroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.husny.autodroid.data.Zone
import com.husny.autodroid.data.ZoneOption
import com.husny.autodroid.settings.ACCENTS
import com.husny.autodroid.settings.ACCENT_NAMES
import com.husny.autodroid.settings.AzanMode
import com.husny.autodroid.settings.ClockStyle
import com.husny.autodroid.settings.Language
import com.husny.autodroid.settings.LyricsMode
import com.husny.autodroid.settings.Settings

/** Everything the phone settings screen shows that isn't in [Settings]. */
data class PhoneState(
    val musicAccess: Boolean = false,
    val locationAllowed: Boolean = false,
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

/** The phone screen: settings from the design canvas, changes apply to the car screen live. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneSettings(settings: Settings, state: PhoneState, actions: PhoneActions) {
    val accent = Color(settings.accent)
    var zonePicker by remember { mutableStateOf(false) }
    val set = actions.update

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AutodroidColors.Background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Autodroid", color = AutodroidColors.Text, fontSize = 30.sp)
            Text("Changes show on the car screen straight away", color = AutodroidColors.TextMuted, fontSize = 14.sp)
        }

        // ------------------------------------------------------------ setup
        Section("SETUP", accent) {
            Item("Music access", if (state.musicAccess) "Allowed" else "Needed to show and control YT Music") {
                if (!state.musicAccess) AccentButton("Allow", accent, actions.allowMusic)
                else StatusText("OK", accent)
            }
            Item("Location", if (state.locationAllowed) "Allowed" else "Needed to pick the prayer zone from GPS", last = true) {
                if (!state.locationAllowed) AccentButton("Allow", accent, actions.allowLocation)
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
                    color = AutodroidColors.TextSoft, fontSize = 15.sp,
                )
                if (state.zoneManual) {
                    OutlinedButton(onClick = { zonePicker = true }) { Text("Change zone") }
                } else if (state.locationAllowed) {
                    OutlinedButton(onClick = actions.detectZone, enabled = !state.detecting) {
                        Text(if (state.detecting) "Finding your zone…" else "Detect zone now")
                    }
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
                    Text(state.azanAudio, color = AutodroidColors.TextSoft, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = actions.chooseAzan) { Text("Choose file") }
                        if (state.azanCustom) OutlinedButton(onClick = actions.builtInAzan) { Text("Use built-in") }
                    }
                }
                Stacked("Subuh azan audio") {
                    Text(state.subuhAudio, color = AutodroidColors.TextSoft, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = actions.chooseSubuh) { Text("Choose file") }
                        if (state.subuhCustom) OutlinedButton(onClick = actions.sameSubuh) { Text("Same as others") }
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
            Item("24-hour clock", "Also switches with the 12h / 24h button in the car") {
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
            Stacked("Lyrics") {
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
                Stacked("Lyrics timing", last = true) {
                    Text(
                        "If lyrics run ahead of or behind the song",
                        color = AutodroidColors.TextMuted, fontSize = 13.sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = {
                            set { it.copy(lyricsOffsetMs = (it.lyricsOffsetMs - 250).coerceAtLeast(-3000)) }
                        }) { Text("Earlier") }
                        Text(
                            formatOffset(settings.lyricsOffsetMs),
                            color = AutodroidColors.Text, fontSize = 16.sp,
                        )
                        OutlinedButton(onClick = {
                            set { it.copy(lyricsOffsetMs = (it.lyricsOffsetMs + 250).coerceAtMost(3000)) }
                        }) { Text("Later") }
                    }
                    if (settings.lyricsOffsetMs != 0) {
                        TextButton(onClick = { set { it.copy(lyricsOffsetMs = 0) } }) { Text("Reset", color = accent) }
                    }
                }
            }
        }

        // ------------------------------------------------------------ appearance
        Section("APPEARANCE", accent) {
            Item("Accent colour") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ACCENTS.forEachIndexed { i, c ->
                        val selected = i == settings.accentIndex
                        Box(
                            Modifier
                                .size(30.dp)
                                .background(Color(c), CircleShape)
                                .border(if (selected) 3.dp else 0.dp, if (selected) AutodroidColors.Text else Color.Transparent, CircleShape)
                                .clickable { set { it.copy(accentIndex = i) } }
                        )
                    }
                }
            }
            Text(
                ACCENT_NAMES.getOrElse(settings.accentIndex) { "" },
                color = AutodroidColors.TextMuted, fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Stacked("Album art shade · ${settings.artShadePercent}%") {
                Text("Lower shows the album art more; higher keeps text easier to read", color = AutodroidColors.TextMuted, fontSize = 13.sp)
                SettingSlider(settings.artShadePercent.toFloat(), 0f..100f, 9, accent) { v ->
                    set { it.copy(artShadePercent = v.toInt()) }
                }
            }
            Stacked("Clock style") {
                val styles = listOf(ClockStyle.LIGHT, ClockStyle.BOLD, ClockStyle.MONO)
                Choices(listOf("Light", "Bold", "Monospace"), styles.indexOf(settings.clockStyle), accent) { i ->
                    set { it.copy(clockStyle = styles[i]) }
                }
            }
            Item("Pure black background", "Deepest black on OLED car screens", last = true) {
                Toggle(settings.amoledBlack, accent) { v -> set { it.copy(amoledBlack = v) } }
            }
        }

        // ------------------------------------------------------------ about
        Section("OFFLINE & SOURCES", accent) {
            Stacked("Prayer times saved on this phone", last = true) {
                Text(state.offlineMonths.ifEmpty { "None yet (saved the first time the car screen opens)" }, color = AutodroidColors.TextSoft, fontSize = 15.sp)
                Text(
                    "Prayer times: JAKIM e-solat (backup: waktusolat.app). Zones: waktusolat.app. " +
                        "Lyrics: Lyrica (open source). Built-in azan: Adzan Mekkah, Abdul Aziz Zahrani.",
                    color = AutodroidColors.TextMuted, fontSize = 13.sp,
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

private fun formatOffset(ms: Int): String = when {
    ms == 0 -> "On time"
    ms < 0 -> "%.2f s earlier".format(-ms / 1000f)
    else -> "%.2f s later".format(ms / 1000f)
}

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
        containerColor = AutodroidColors.MusicPanel,
        title = { Text("Choose prayer zone", color = AutodroidColors.Text) },
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
                    failed -> Text("Couldn't load the zone list. Check your internet and try again.", color = AutodroidColors.TextSoft)
                    list == null -> Text("Loading zones…", color = AutodroidColors.TextSoft)
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
                                    Text(z.districts, color = AutodroidColors.TextSoft, fontSize = 15.sp)
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

@Composable
private fun Section(title: String, accent: Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = accent, fontSize = 12.sp, letterSpacing = 3.sp, modifier = Modifier.padding(start = 4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(AutodroidColors.Panel, RoundedCornerShape(18.dp))
                .border(1.dp, AutodroidColors.PanelBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) { content() }
    }
}

/** A row: label (and optional hint) on the left, a control on the right. */
@Composable
private fun Item(label: String, hint: String? = null, last: Boolean = false, control: @Composable () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = AutodroidColors.Text, fontSize = 16.sp)
                if (hint != null) Text(hint, color = AutodroidColors.TextMuted, fontSize = 13.sp)
            }
            control()
        }
        if (!last) Divider()
    }
}

/** A label with its controls underneath (for choices that need the full width). */
@Composable
private fun Stacked(label: String, last: Boolean = false, content: @Composable () -> Unit) {
    Column {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = AutodroidColors.Text, fontSize = 16.sp)
            content()
        }
        if (!last) Divider()
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 0.dp).heightIn(min = 1.dp, max = 1.dp).background(AutodroidColors.PanelBorder))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(labels: List<String>, selected: Int, accent: Color, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { i, label ->
            FilterChip(
                selected = i == selected,
                onClick = { onSelect(i) },
                label = { Text(label, fontSize = 14.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    labelColor = AutodroidColors.TextSoft,
                    selectedContainerColor = accent,
                    selectedLabelColor = AutodroidColors.Background,
                ),
            )
        }
    }
}

@Composable
private fun Toggle(checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = AutodroidColors.Background),
    )
}

@Composable
private fun SettingSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, accent: Color, onChange: (Float) -> Unit) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
    )
}

@Composable
private fun AccentButton(label: String, accent: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = AutodroidColors.Background),
    ) { Text(label) }
}

@Composable
private fun StatusText(text: String, accent: Color) {
    Text(text, color = accent, fontSize = 15.sp)
}
