package com.husny.autodroid.car

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Motion { PARKED, MOVING, UNKNOWN }

/**
 * Works out whether the car is stopped, for the parked-only video.
 *
 * Uses the car's own speed when Android Auto passes it on; otherwise the phone's GPS speed. When
 * neither is known (no car speed and no recent GPS fix), the answer is [Motion.UNKNOWN], which
 * counts as "not parked".
 */
class MotionWatcher(
    private val carContext: CarContext,
    private val lifecycle: Lifecycle,
) : DefaultLifecycleObserver {

    private val _motion = MutableStateFlow(Motion.UNKNOWN)
    val motion: StateFlow<Motion> = _motion.asStateFlow()

    /** Last car speed in m/s, or null if the car doesn't report it. */
    @Volatile private var carSpeed: Float? = null

    /** Last phone GPS fix with a speed. */
    @Volatile private var gpsSpeed: Float? = null
    @Volatile private var gpsAt: Long = 0L

    private var speedListener: OnCarDataAvailableListener<Speed>? = null
    private var gpsListener: LocationListener? = null
    private var ticker: Job? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        startCarSpeed()
        startGps()
        ticker = owner.lifecycleScope.launch {
            while (true) {
                _motion.value = decide()
                delay(1_000L)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        ticker?.cancel()
        speedListener?.let { l ->
            runCatching { carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(l) }
        }
        gpsListener?.let { l ->
            runCatching { carContext.getSystemService(LocationManager::class.java).removeUpdates(l) }
        }
    }

    private fun decide(): Motion {
        carSpeed?.let { return if (it < CAR_STOPPED) Motion.PARKED else Motion.MOVING }
        val gps = gpsSpeed
        if (gps != null && SystemClock.elapsedRealtime() - gpsAt < GPS_FRESH_MS) {
            return if (gps < GPS_STOPPED) Motion.PARKED else Motion.MOVING
        }
        return Motion.UNKNOWN
    }

    /** Starts reading the car's speed again, e.g. after the speed permission was granted. */
    fun restartCarSpeed() {
        speedListener?.let { l ->
            runCatching { carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(l) }
        }
        speedListener = null
        carSpeed = null
        startCarSpeed()
    }

    /** True once the car has reported a speed. */
    val hasCarSpeed: Boolean get() = carSpeed != null

    val hasSpeedPermission: Boolean
        get() = ContextCompat.checkSelfPermission(carContext, CAR_SPEED) == PackageManager.PERMISSION_GRANTED

    private fun startCarSpeed() {
        if (!hasSpeedPermission) return
        runCatching {
            val info = carContext.getCarService(CarHardwareManager::class.java).carInfo
            val listener = OnCarDataAvailableListener<Speed> { speed ->
                // Raw speed if the car gives it, else the speed shown on the car's speedometer.
                val raw = speed.rawSpeedMetersPerSecond
                val shown = speed.displaySpeedMetersPerSecond
                val value = when {
                    raw.status == CarValue.STATUS_SUCCESS -> raw.value
                    shown.status == CarValue.STATUS_SUCCESS -> shown.value
                    else -> null
                }
                if ((value == null) != (carSpeed == null)) {
                    Log.i(TAG, "Car speed: raw status ${raw.status}, shown status ${shown.status}, value $value")
                }
                carSpeed = value?.let { kotlin.math.abs(it) }
            }
            info.addSpeedListener(ContextCompat.getMainExecutor(carContext), listener)
            speedListener = listener
            Log.i(TAG, "Listening for car speed")
        }.onFailure { Log.i(TAG, "Car speed not available, using phone GPS: ${it.message}") }
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = carContext.getSystemService(LocationManager::class.java) ?: return
        val listener = LocationListener { location: Location ->
            if (location.hasSpeed()) {
                if (gpsSpeed == null) Log.i(TAG, "GPS speed available")
                gpsSpeed = location.speed
                gpsAt = SystemClock.elapsedRealtime()
            }
        }
        runCatching {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
            gpsListener = listener
        }.onFailure { Log.w(TAG, "GPS updates failed", it) }
    }

    companion object {
        private const val TAG = "Autodroid"
        /** Car speed below this (m/s) counts as stopped: about 2 km/h. */
        private const val CAR_STOPPED = 0.6f
        /** GPS speed is noisier when standing still: about 5 km/h. */
        private const val GPS_STOPPED = 1.5f
        private const val GPS_FRESH_MS = 10_000L
        const val CAR_SPEED = "com.google.android.gms.permission.CAR_SPEED"
    }
}
