package com.husny.autodroid

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.husny.autodroid.azan.Azan
import com.husny.autodroid.azan.AzanSettings
import com.husny.autodroid.azan.AzanSlot
import com.husny.autodroid.data.Prayer
import com.husny.autodroid.data.Zone
import com.husny.autodroid.data.ZoneTracker
import com.husny.autodroid.data.hasMusicAccess
import com.husny.autodroid.settings.AzanMode
import com.husny.autodroid.settings.SettingsStore
import com.husny.autodroid.ui.PhoneActions
import com.husny.autodroid.ui.PhoneSettings
import com.husny.autodroid.ui.PhoneState
import kotlinx.coroutines.launch
import java.io.File

/** Phone screen: Autodroid's settings. Changes apply to the car screen live. */
class MainActivity : ComponentActivity() {

    private val phoneState = mutableStateOf(PhoneState())
    private lateinit var azanSettings: AzanSettings
    private var pickingSlot = AzanSlot.MAIN

    private val pickAzanAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val ok = azanSettings.importAudio(pickingSlot, uri)
        Toast.makeText(this, if (ok) "Azan audio saved" else "Couldn't read that file", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        if (ZoneTracker.hasPermission(this) && !ZoneTracker.isManual(this)) detectZone()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        ZoneTracker.init(this)
        azanSettings = AzanSettings(this)

        val actions = PhoneActions(
            update = { change -> SettingsStore.update(change) },
            allowMusic = { startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            allowLocation = {
                askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            detectZone = ::detectZone,
            useGps = {
                ZoneTracker.setAuto(this)
                refresh()
                if (ZoneTracker.hasPermission(this)) detectZone()
            },
            loadZones = { onLoaded -> lifecycleScope.launch { onLoaded(ZoneTracker.allZones(this@MainActivity)) } },
            chooseZone = { option ->
                ZoneTracker.setManual(this, Zone(option.code, option.districts.substringBefore(",").trim()))
                refresh()
            },
            chooseAzan = {
                pickingSlot = AzanSlot.MAIN
                pickAzanAudio.launch(arrayOf("audio/*"))
            },
            builtInAzan = {
                azanSettings.useDefault(AzanSlot.MAIN)
                refresh()
            },
            chooseSubuh = {
                pickingSlot = AzanSlot.SUBUH
                pickAzanAudio.launch(arrayOf("audio/*"))
            },
            sameSubuh = {
                azanSettings.useDefault(AzanSlot.SUBUH)
                refresh()
            },
            testAzan = {
                Azan.start(this, Prayer.MAGHRIB, pauseOnly = SettingsStore.current.azanMode == AzanMode.PAUSE_ONLY)
            },
            stopAzan = Azan::stop,
        )

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val settings by SettingsStore.settings.collectAsState()
                val zone by ZoneTracker.current.collectAsState()
                val azan by Azan.state.collectAsState()
                PhoneSettings(
                    settings = settings,
                    state = phoneState.value.copy(zone = zone, azanRunning = azan.phase != Azan.Phase.IDLE),
                    actions = actions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun detectZone() {
        phoneState.value = phoneState.value.copy(detecting = true)
        lifecycleScope.launch {
            val zone = ZoneTracker.detectNow(this@MainActivity)
            refresh()
            Toast.makeText(
                this@MainActivity,
                zone?.let { "Prayer zone: ${it.code} · ${it.name}" } ?: "Couldn't find your zone (no location or signal)",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun refresh() {
        val months = File(filesDir, "prayer").listFiles().orEmpty()
            .map { it.nameWithoutExtension }
            .sorted()
            .joinToString("\n") { name ->
                val parts = name.split("_", limit = 2)
                "${parts[0]} · ${parts.getOrElse(1) { "" }}"
            }
        phoneState.value = phoneState.value.copy(
            musicAccess = hasMusicAccess(this),
            locationAllowed = ZoneTracker.hasPermission(this),
            zoneManual = ZoneTracker.isManual(this),
            zoneFromGps = ZoneTracker.detected(this),
            detecting = false,
            azanAudio = azanSettings.audioName(AzanSlot.MAIN),
            azanCustom = azanSettings.hasCustom(AzanSlot.MAIN),
            subuhAudio = azanSettings.audioName(AzanSlot.SUBUH),
            subuhCustom = azanSettings.hasCustom(AzanSlot.SUBUH),
            offlineMonths = months,
        )
    }
}
