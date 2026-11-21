package com.husny.autodroid.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

/** A JAKIM prayer-time zone, e.g. SGR01 · Petaling. */
data class Zone(val code: String, val name: String)

/** One entry in the full JAKIM zone list, for choosing a zone by hand. */
data class ZoneOption(val code: String, val state: String, val districts: String)

/**
 * Keeps track of which JAKIM zone the phone is in, from GPS. Coordinates are turned into a zone
 * by api.waktusolat.app (free). The last zone is remembered, so with no signal (or outside
 * Malaysia) prayer times carry on with the zone you were last in.
 *
 * One per app process, shared by the car screen and the phone screen.
 */
object ZoneTracker {

    val DEFAULT = Zone("SGR01", "Petaling")

    /** Look the zone up again only after moving this far, to keep network use tiny. */
    private const val RELOOKUP_METERS = 3_000f
    private const val TAG = "Autodroid"

    private val _current = MutableStateFlow(DEFAULT)
    val current: StateFlow<Zone> = _current.asStateFlow()

    private var loaded = false
    private val mutex = Mutex()

    fun init(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = prefs(context)
        val manualCode = prefs.getString("manual_code", null)
        val manualName = prefs.getString("manual_name", null)
        val code = prefs.getString("code", null)
        val name = prefs.getString("name", null)
        when {
            manualCode != null && manualName != null -> _current.value = Zone(manualCode, manualName)
            code != null && name != null -> _current.value = Zone(code, name)
        }
    }

    /** True when the user picked a zone by hand (GPS is then ignored). */
    fun isManual(context: Context): Boolean = prefs(context).contains("manual_code")

    fun setManual(context: Context, zone: Zone) {
        init(context)
        prefs(context).edit().putString("manual_code", zone.code).putString("manual_name", zone.name).apply()
        _current.value = zone
    }

    /** Back to GPS: shows the last zone GPS found (or the default) until the next fix. */
    fun setAuto(context: Context) {
        init(context)
        val prefs = prefs(context)
        prefs.edit().remove("manual_code").remove("manual_name").remove("lat").remove("lng").apply()
        val code = prefs.getString("code", null)
        val name = prefs.getString("name", null)
        _current.value = if (code != null && name != null) Zone(code, name) else DEFAULT
    }

    /** Every JAKIM zone (from api.waktusolat.app, saved after the first download). */
    suspend fun allZones(context: Context): List<ZoneOption>? {
        val file = java.io.File(context.applicationContext.filesDir, "zones.json")
        val body = (if (file.exists()) runCatching { file.readText() }.getOrNull() else null)
            ?: Http.get("https://api.waktusolat.app/zones")?.also { runCatching { file.writeText(it) } }
            ?: return null
        return runCatching {
            val array = org.json.JSONArray(body)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                ZoneOption(o.getString("jakimCode"), o.optString("negeri"), o.optString("daerah"))
            }
        }.getOrNull()
    }

    /** True once a zone has come from GPS at least once (rather than the built-in default). */
    fun detected(context: Context): Boolean = prefs(context).contains("code")

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Updates the zone for a new position. Returns the new zone if it changed, else null.
     */
    suspend fun onLocation(context: Context, location: Location): Zone? = mutex.withLock {
        init(context)
        if (isManual(context)) return null
        val prefs = prefs(context)
        if (prefs.contains("lat")) {
            val last = Location("last").apply {
                latitude = prefs.getFloat("lat", 0f).toDouble()
                longitude = prefs.getFloat("lng", 0f).toDouble()
            }
            if (detected(context) && last.distanceTo(location) < RELOOKUP_METERS) return null
        }
        val zone = lookup(location.latitude, location.longitude) ?: return null
        prefs.edit()
            .putFloat("lat", location.latitude.toFloat())
            .putFloat("lng", location.longitude.toFloat())
            .putString("code", zone.code)
            .putString("name", zone.name)
            .apply()
        val changed = zone != _current.value
        Log.i(TAG, "Zone lookup: ${zone.code} · ${zone.name} (changed=$changed)")
        _current.value = zone
        if (changed) zone else null
    }

    /** One-off detection for the phone screen. Returns the zone, or null if it couldn't tell. */
    suspend fun detectNow(context: Context): Zone? {
        if (!hasPermission(context)) return null
        val location = currentLocation(context) ?: return null
        // Force a fresh lookup by forgetting where we last looked.
        prefs(context).edit().remove("lat").remove("lng").apply()
        onLocation(context, location)
        return if (detected(context)) _current.value else null
    }

    private suspend fun lookup(lat: Double, lng: Double): Zone? {
        val body = Http.get("https://api.waktusolat.app/zones/$lat/$lng") ?: return null
        return runCatching {
            val o = JSONObject(body)
            Zone(o.getString("zone"), o.optString("district").ifBlank { o.getString("zone") })
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java)
        val providers = manager.getProviders(true)
        val recent = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 10 * 60_000L }
            .maxByOrNull { it.time }
        if (recent != null) return recent
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val provider = when {
            LocationManager.FUSED_PROVIDER in providers && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> LocationManager.FUSED_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        return withTimeoutOrNull(20_000L) {
            suspendCancellableCoroutine { cont ->
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
            }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("zone", Context.MODE_PRIVATE)
}
