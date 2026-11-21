package com.husny.autodroid.car

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import com.husny.autodroid.azan.Azan
import com.husny.autodroid.azan.AzanSettings
import com.husny.autodroid.data.LyricsRepository
import com.husny.autodroid.data.LyricsResult
import com.husny.autodroid.data.MALAYSIA
import com.husny.autodroid.data.NowPlayingWatcher
import com.husny.autodroid.data.PrayerDay
import com.husny.autodroid.data.PrayerRepository
import com.husny.autodroid.data.Zone
import com.husny.autodroid.data.ZoneTracker
import com.husny.autodroid.data.mainPrayerMoments
import com.husny.autodroid.settings.AzanMode
import com.husny.autodroid.settings.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.YearMonth

sealed interface PrayerStatus {
    data object Loading : PrayerStatus
    data object Offline : PrayerStatus
    data class Ready(val days: List<PrayerDay>) : PrayerStatus
}

/**
 * Everything the car dashboard shows, kept alive for one car session: prayer times for the zone
 * you're in, the music that's playing, and its lyrics.
 */
class DashboardModel(
    private val carContext: CarContext,
    private val lifecycle: Lifecycle,
) : DefaultLifecycleObserver {

    private val appContext = carContext.applicationContext
    private val azanSettings = AzanSettings(appContext)

    /** The JAKIM zone in use (from GPS; the last one found is kept with no signal). */
    val zone: StateFlow<Zone> = ZoneTracker.current

    private val prayerRepository = PrayerRepository(appContext)
    private val lyricsRepository = LyricsRepository()
    val music = NowPlayingWatcher(appContext)

    private val _prayer = MutableStateFlow<PrayerStatus>(PrayerStatus.Loading)
    val prayer: StateFlow<PrayerStatus> = _prayer.asStateFlow()

    private val _lyrics = MutableStateFlow<LyricsResult?>(null)
    val lyrics: StateFlow<LyricsResult?> = _lyrics.asStateFlow()

    private val locationManager = appContext.getSystemService(LocationManager::class.java)
    private val locationListener = LocationListener { location -> onLocation(location) }

    init {
        SettingsStore.init(appContext)
        ZoneTracker.init(appContext)
        lifecycle.addObserver(this)
        // Free the space used by map tiles from build 0.8 (the map was removed).
        Thread { File(appContext.cacheDir, "osmdroid").deleteRecursively() }.start()
    }

    override fun onCreate(owner: LifecycleOwner) {
        music.start()
        startLocation()
        val scope = lifecycle.coroutineScope
        scope.launch {
            // Reload prayer times whenever the zone changes.
            zone.collectLatest { keepPrayerTimesLoaded(it) }
        }
        scope.launch {
            while (true) {
                checkAzan()
                delay(5_000L)
            }
        }
        scope.launch {
            music.track
                .map { it?.let { t -> Triple(t.title, t.artist, t.durationMs) } }
                .distinctUntilChanged()
                .collectLatest { key ->
                    if (key == null || key.first.isBlank()) {
                        _lyrics.value = null
                        return@collectLatest
                    }
                    _lyrics.value = LyricsResult.Loading
                    _lyrics.value = lyricsRepository.find(key.first, key.second, key.third)
                }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        music.stop()
        runCatching { locationManager.removeUpdates(locationListener) }
    }

    /** Location every minute or kilometre while the car screen is open, to keep the prayer zone right. */
    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (!ZoneTracker.hasPermission(appContext)) return
        val providers = locationManager.getProviders(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in providers) {
            val request = LocationRequest.Builder(60_000L)
                .setQuality(LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
                .setMinUpdateDistanceMeters(1_000f)
                .build()
            runCatching {
                locationManager.requestLocationUpdates(
                    LocationManager.FUSED_PROVIDER, request, appContext.mainExecutor, locationListener,
                )
            }
        } else {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { it in providers }.forEach {
                runCatching { locationManager.requestLocationUpdates(it, 60_000L, 1_000f, locationListener, Looper.getMainLooper()) }
            }
        }
        providers.mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { onLocation(it) }
    }

    private fun onLocation(location: Location) {
        lifecycle.coroutineScope.launch {
            val changed = ZoneTracker.onLocation(appContext, location) ?: return@launch
            if (!SettingsStore.current.notifyZoneChange) return@launch
            CarToast.makeText(carContext, "Prayer zone: ${changed.code} · ${changed.name}", CarToast.LENGTH_LONG).show()
        }
    }

    /** Starts the azan when a prayer time arrives (within its first minute), once per prayer. */
    private fun checkAzan() {
        val mode = SettingsStore.current.azanMode
        if (mode == AzanMode.OFF) return
        val days = (_prayer.value as? PrayerStatus.Ready)?.days ?: return
        val now = LocalDateTime.now(MALAYSIA)
        val due = mainPrayerMoments(days).firstOrNull {
            !now.isBefore(it.at) && now.isBefore(it.at.plusMinutes(1))
        } ?: return
        // Keyed by day and prayer (not minute), so crossing into a zone whose time is a minute
        // later doesn't play the same azan twice.
        val key = "${due.at.toLocalDate()}-${due.prayer}"
        if (azanSettings.lastHandled == key) return
        azanSettings.lastHandled = key
        Azan.start(appContext, due.prayer, pauseOnly = mode == AzanMode.PAUSE_ONLY)
    }

    /** Loads this month and next for [zone] (so "next prayer" works on the month's last night). */
    private suspend fun keepPrayerTimesLoaded(zone: Zone) {
        var loadedFor: YearMonth? = null
        while (true) {
            val thisMonth = YearMonth.now(MALAYSIA)
            if (loadedFor != thisMonth) {
                val current = prayerRepository.month(zone.code, thisMonth)
                if (current != null) {
                    val next = prayerRepository.month(zone.code, thisMonth.plusMonths(1)).orEmpty()
                    _prayer.value = PrayerStatus.Ready(current + next)
                    loadedFor = thisMonth
                } else if (_prayer.value !is PrayerStatus.Ready) {
                    _prayer.value = PrayerStatus.Offline
                }
            }
            // Retry soon while offline; otherwise just check for a new month now and then.
            delay(if (loadedFor == thisMonth) 30 * 60_000L else 60_000L)
        }
    }
}
