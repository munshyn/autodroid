package com.husny.autodroid.azan

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.husny.autodroid.R
import com.husny.autodroid.data.MediaListenerService
import com.husny.autodroid.data.Prayer
import com.husny.autodroid.data.YT_MUSIC_PACKAGE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * At prayer time: three alert chimes over the music, pause the music, wait 1 second, play the
 * azan, then 1 second after it ends (or is stopped) resume the music.
 *
 * One per app process, so the phone screen's "Test" button and the car screen share it.
 */
object Azan {

    /** ALERT: chimes. PLAYING: azan audio. PAUSED: "pause only" mode, music left paused. */
    enum class Phase { IDLE, ALERT, PLAYING, PAUSED }

    data class State(val phase: Phase = Phase.IDLE, val prayer: Prayer? = null)

    private const val TAG = "Autodroid"
    private const val CHIMES = 3
    private const val GAP_BETWEEN_CHIMES_MS = 250L
    private const val PAUSE_BEFORE_AZAN_MS = 1_000L
    private const val PAUSE_BEFORE_RESUME_MS = 1_000L
    /** In "pause only" mode, how long the prayer card stays amber before closing itself. */
    private const val PAUSED_CARD_MS = 60_000L

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /** [pauseOnly]: chimes and pause the music, no azan audio and no resume. */
    fun start(context: Context, prayer: Prayer, pauseOnly: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch { run(context.applicationContext, prayer, pauseOnly) }
    }

    /** Stops the azan early. The music resumes 1 second later as usual. */
    fun stop() {
        job?.cancel()
    }

    private suspend fun run(context: Context, prayer: Prayer, pauseOnly: Boolean) {
        val settings = AzanSettings(context)
        val audio = context.getSystemService(AudioManager::class.java)
        val music = findMusic(context, playingOnly = true)
        var paused = false
        val slot = if (prayer == Prayer.FAJR && settings.hasCustom(AzanSlot.SUBUH)) AzanSlot.SUBUH else AzanSlot.MAIN
        Log.i(TAG, "Azan for ${prayer.label}: music playing=${music != null} (${music?.packageName}), pauseOnly=$pauseOnly, audio=${settings.audioName(slot)}")

        try {
            // 1. Three chimes, with the music lowered (ducked) underneath.
            _state.value = State(Phase.ALERT, prayer)
            withFocus(audio, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) {
                repeat(CHIMES) { i ->
                    play(context, Source.Raw(R.raw.azan_alert), volume = 1f)
                    if (i < CHIMES - 1) delay(GAP_BETWEEN_CHIMES_MS)
                }
                // 2. Pause the music straight away.
                if (music != null) {
                    music.transportControls.pause()
                    paused = true
                }
            }

            if (pauseOnly) {
                // Leave the music paused; the card closes itself after a minute (or on tap).
                paused = false
                _state.value = State(Phase.PAUSED, prayer)
                delay(PAUSED_CARD_MS)
                return
            }

            // 3. One second of quiet, then the azan.
            delay(PAUSE_BEFORE_AZAN_MS)
            _state.value = State(Phase.PLAYING, prayer)
            val azan = if (settings.hasCustom(slot)) Source.File(settings.file(slot).path)
            else Source.Raw(R.raw.azan_default)
            withFocus(audio, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) {
                play(context, azan, settings.volume)
            }
        } finally {
            withContext(NonCancellable) {
                _state.value = State()
                // 4. One second after the azan ends (or is stopped), carry on with the music.
                if (paused && settings.resumeMusic) {
                    delay(PAUSE_BEFORE_RESUME_MS)
                    resumeMusic(context, music?.packageName)
                }
                Log.i(TAG, "Azan finished")
            }
        }
    }

    private sealed interface Source {
        data class Raw(val resId: Int) : Source
        data class File(val path: String) : Source
    }

    /** Plays one sound to the end (or until cancelled). */
    private suspend fun play(context: Context, source: Source, volume: Float) {
        val player = MediaPlayer()
        try {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { runCatching { player.stop() } }
                try {
                    player.setAudioAttributes(attributes)
                    when (source) {
                        is Source.Raw -> context.resources.openRawResourceFd(source.resId).use {
                            player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                        }
                        is Source.File -> player.setDataSource(source.path)
                    }
                    player.setVolume(volume, volume)
                    player.setOnCompletionListener { if (cont.isActive) cont.resume(Unit) }
                    player.setOnErrorListener { _, what, extra ->
                        Log.w(TAG, "Playback error $what/$extra")
                        if (cont.isActive) cont.resume(Unit)
                        true
                    }
                    player.prepare()
                    player.start()
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't play $source", e)
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        } finally {
            player.release()
        }
    }

    private suspend fun <T> withFocus(audio: AudioManager, gain: Int, block: suspend () -> T): T {
        val request = AudioFocusRequest.Builder(gain)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
        audio.requestAudioFocus(request)
        try {
            return block()
        } finally {
            audio.abandonAudioFocusRequest(request)
        }
    }

    /**
     * Presses play on the music app. Looks the session up again (the one from before the azan
     * may be stale), and if play didn't take, sends a "play" media key as a fallback.
     */
    private suspend fun resumeMusic(context: Context, packageName: String?) {
        val target = findMusic(context, playingOnly = false, preferPackage = packageName)
        if (target == null) {
            Log.w(TAG, "Resume: no music session found")
            return
        }
        target.transportControls.play()
        delay(1_500L)
        val state = target.playbackState?.state
        Log.i(TAG, "Resume: sent play to ${target.packageName}, state now $state")
        if (state != PlaybackState.STATE_PLAYING && state != PlaybackState.STATE_BUFFERING) {
            val now = SystemClock.uptimeMillis()
            target.dispatchMediaButtonEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
            target.dispatchMediaButtonEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0))
            delay(1_000L)
            Log.i(TAG, "Resume: sent media key, state now ${target.playbackState?.state}")
        }
    }

    /** The music app: YT Music first, else another app. */
    private fun findMusic(context: Context, playingOnly: Boolean, preferPackage: String? = null): MediaController? =
        runCatching {
            val manager = context.getSystemService(MediaSessionManager::class.java)
            val sessions = manager.getActiveSessions(ComponentName(context, MediaListenerService::class.java))
            fun MediaController.playing() = playbackState?.state == PlaybackState.STATE_PLAYING
            val candidates = if (playingOnly) sessions.filter { it.playing() } else sessions
            candidates.firstOrNull { it.packageName == (preferPackage ?: YT_MUSIC_PACKAGE) }
                ?: candidates.firstOrNull { it.packageName == YT_MUSIC_PACKAGE }
                ?: candidates.firstOrNull()
        }.getOrNull()
}
