package com.husny.autodroid.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.lifecycleScope
import com.husny.autodroid.settings.Language
import com.husny.autodroid.settings.SettingsStore
import kotlinx.coroutines.launch

/**
 * The only car screen.
 *
 * Autodroid is registered as a navigation app, so Android Auto gives it the whole screen through
 * NavigationTemplate: the drawing surface (handled by [SurfaceRenderer]) with no content card.
 * The action strip holds the 12h/24h switch and the Video button; in video mode it holds the
 * button back to the dashboard, and the pan button so the YouTube page can be scrolled.
 */
class HomeScreen(
    carContext: CarContext,
    private val renderer: SurfaceRenderer,
    private val video: VideoMode,
) : Screen(carContext) {

    init {
        lifecycleScope.launch { video.open.collect { invalidate() } }
    }

    override fun onGetTemplate(): Template {
        val malay = SettingsStore.current.language == Language.MALAY
        val builder = NavigationTemplate.Builder()
        if (video.open.value) {
            val back = Action.Builder()
                .setTitle(if (malay) "Papan utama" else "Dashboard")
                .setOnClickListener { video.close(null) }
                .build()
            builder.setActionStrip(ActionStrip.Builder().addAction(back).build())
            if (carContext.carAppApiLevel >= 2) {
                builder.setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            }
        } else {
            val clockToggle = Action.Builder()
                .setTitle("12h / 24h")
                .setOnClickListener { SettingsStore.update { it.copy(use24h = !it.use24h) } }
                .build()
            val openVideo = Action.Builder()
                .setTitle("Video")
                .setOnClickListener { video.requestOpen() }
                .build()
            builder.setActionStrip(ActionStrip.Builder().addAction(clockToggle).addAction(openVideo).build())
        }
        return builder.build()
    }
}
