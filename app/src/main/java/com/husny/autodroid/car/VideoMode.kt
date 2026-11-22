package com.husny.autodroid.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.husny.autodroid.azan.Azan
import com.husny.autodroid.settings.Language
import com.husny.autodroid.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A YouTube link shared from the phone, waiting to be played on the car screen. */
object SharedVideo {
    private val _url = MutableStateFlow<String?>(null)
    val url: StateFlow<String?> = _url.asStateFlow()

    fun send(url: String) { _url.value = url }

    fun take(): String? = _url.value.also { _url.value = null }
}

/**
 * The parked-only YouTube mode on the car screen.
 *
 * Opens only while [MotionWatcher] says the car is stopped, and closes straight away when the car
 * moves, when it can no longer tell for [UNKNOWN_GRACE_SECONDS], or when the azan starts.
 */
class VideoMode(
    private val carContext: CarContext,
    lifecycle: Lifecycle,
    private val motion: MotionWatcher,
) : DefaultLifecycleObserver {

    private val _open = MutableStateFlow(false)
    val open: StateFlow<Boolean> = _open.asStateFlow()

    /** Page the video view should load next (a shared link, or YouTube's home page). */
    private val _load = MutableStateFlow<String?>(null)
    val load: StateFlow<String?> = _load.asStateFlow()

    private var unknownFor = 0

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        owner.lifecycleScope.launch {
            motion.motion.collect { m ->
                if (!_open.value) { unknownFor = 0; return@collect }
                when (m) {
                    Motion.PARKED -> unknownFor = 0
                    Motion.MOVING -> close(tr("Video closed: the car is moving", "Video ditutup: kereta sedang bergerak"))
                    Motion.UNKNOWN -> if (++unknownFor >= UNKNOWN_GRACE_SECONDS) {
                        close(tr("Video closed: can't tell if the car is stopped", "Video ditutup: tidak pasti kereta berhenti"))
                    }
                }
            }
        }
        owner.lifecycleScope.launch {
            Azan.state.collect { if (it.phase != Azan.Phase.IDLE && _open.value) close(null) }
        }
        owner.lifecycleScope.launch {
            // A video shared from the phone opens by itself if the car is stopped.
            SharedVideo.url.collect { url ->
                if (url == null) return@collect
                if (_open.value || motion.motion.value == Motion.PARKED) {
                    SharedVideo.take()
                    _load.value = url
                    _open.value = true
                } else {
                    toast(tr("Video ready: it plays once the car is stopped", "Video sedia: dimainkan bila kereta berhenti"))
                }
            }
        }
        owner.lifecycleScope.launch {
            // A waiting shared video opens as soon as the car stops.
            motion.motion.collect { m ->
                if (m == Motion.PARKED && !_open.value && SharedVideo.url.value != null) {
                    _load.value = SharedVideo.take()
                    _open.value = true
                }
            }
        }
    }

    /** The "Video" button. */
    fun requestOpen() {
        if (motion.hasSpeedPermission && !motion.hasCarSpeed) {
            // Allowed after this car session started: start reading the speed now.
            motion.restartCarSpeed()
        }
        if (!motion.hasSpeedPermission) {
            // Ask once for the car's speed; the phone's GPS is used until (or unless) it's allowed.
            runCatching {
                carContext.requestPermissions(listOf(MotionWatcher.CAR_SPEED)) { granted, rejected ->
                    android.util.Log.i("Autodroid", "Car speed permission: granted $granted, rejected $rejected")
                    if (granted.isNotEmpty()) motion.restartCarSpeed()
                }
            }.onFailure { android.util.Log.w("Autodroid", "Couldn't ask for car speed", it) }
        }
        if (motion.motion.value != Motion.PARKED) {
            toast(
                if (motion.motion.value == Motion.MOVING) tr("Video only works when the car is stopped", "Video hanya boleh bila kereta berhenti")
                else tr("Video only works when the car is stopped (can't read the speed yet)", "Video hanya boleh bila kereta berhenti (kelajuan belum diketahui)")
            )
            return
        }
        // Reopening keeps the page that was showing; the video view opens YouTube's home page if
        // nothing has been loaded yet.
        SharedVideo.take()?.let { _load.value = it }
        _open.value = true
    }

    /** The "Dashboard" button, or closing for safety. */
    fun close(message: String?) {
        if (!_open.value) return
        _open.value = false
        unknownFor = 0
        message?.let(::toast)
    }

    /** Called by the video view once it has started loading [load]. */
    fun loaded() { _load.value = null }

    private fun toast(text: String) = CarToast.makeText(carContext, text, CarToast.LENGTH_LONG).show()

    private fun tr(en: String, ms: String) = if (SettingsStore.current.language == Language.MALAY) ms else en

    companion object {
        const val YOUTUBE_HOME = "https://m.youtube.com/"
        private const val UNKNOWN_GRACE_SECONDS = 30
    }
}
