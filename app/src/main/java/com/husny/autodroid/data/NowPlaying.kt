package com.husny.autodroid.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android only shares other apps' music sessions with an app the user has allowed under
 * "Notification access". Autodroid never reads notifications; this service exists only to hold
 * that permission.
 */
class MediaListenerService : NotificationListenerService()

const val YT_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"

data class Track(
    val title: String,
    val artist: String,
    val durationMs: Long,
    val art: Bitmap?,
    val sourcePackage: String,
)

data class Playback(
    val isPlaying: Boolean,
    val positionMs: Long,
    /** SystemClock.elapsedRealtime() when [positionMs] was measured. */
    val measuredAt: Long,
    val speed: Float,
) {
    fun positionNow(): Long =
        if (!isPlaying) positionMs
        else positionMs + ((SystemClock.elapsedRealtime() - measuredAt) * speed).toLong()
}

fun hasMusicAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** Follows whichever music app is playing (YT Music first) and offers play/pause/skip. */
class NowPlayingWatcher(private val context: Context) {

    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track.asStateFlow()

    private val _playback = MutableStateFlow<Playback?>(null)
    val playback: StateFlow<Playback?> = _playback.asStateFlow()

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, MediaListenerService::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        choose(list.orEmpty())
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publish()
            // A different app may have started playing; re-pick.
            refreshSessions()
        }
        override fun onSessionDestroyed() = refreshSessions()
    }

    /** Re-checks access every few seconds until granted, so it works right after the user allows it. */
    private val accessPoll = object : Runnable {
        override fun run() {
            if (startListening()) return
            handler.postDelayed(this, 3_000L)
        }
    }

    fun start() {
        handler.post(accessPoll)
    }

    fun stop() {
        handler.removeCallbacks(accessPoll)
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }

    fun previous() { controller?.transportControls?.skipToPrevious() }

    private fun startListening(): Boolean {
        if (!hasMusicAccess(context)) {
            _hasAccess.value = false
            return false
        }
        return try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            _hasAccess.value = true
            refreshSessions()
            true
        } catch (_: SecurityException) {
            _hasAccess.value = false
            false
        }
    }

    private fun refreshSessions() {
        val sessions = try {
            manager.getActiveSessions(listenerComponent)
        } catch (_: SecurityException) {
            _hasAccess.value = false
            return
        }
        choose(sessions)
    }

    private fun choose(sessions: List<MediaController>) {
        fun MediaController.playing() = playbackState?.state == PlaybackState.STATE_PLAYING
        val pick = sessions.firstOrNull { it.packageName == YT_MUSIC_PACKAGE && it.playing() }
            ?: sessions.firstOrNull { it.playing() }
            ?: sessions.firstOrNull { it.packageName == YT_MUSIC_PACKAGE }
            ?: sessions.firstOrNull()

        if (pick?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = pick
            pick?.registerCallback(controllerCallback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val metadata = c?.metadata
        if (c == null || metadata == null) {
            _track.value = null
            _playback.value = null
            return
        }
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val current = _track.value
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        if (current == null || current.title != title || current.artist != artist ||
            current.durationMs != duration || current.art !== art
        ) {
            _track.value = Track(title, artist, duration, art, c.packageName)
        }

        val state = c.playbackState
        _playback.value = state?.let {
            Playback(
                isPlaying = it.state == PlaybackState.STATE_PLAYING,
                positionMs = it.position,
                measuredAt = it.lastPositionUpdateTime.takeIf { t -> t > 0 } ?: SystemClock.elapsedRealtime(),
                speed = if (it.playbackSpeed == 0f) 1f else it.playbackSpeed,
            )
        }
    }
}
