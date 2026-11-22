package com.husny.autodroid.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import com.husny.autodroid.R
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.lifecycleScope
import com.husny.autodroid.settings.SettingsStore
import kotlinx.coroutines.launch

/**
 * The only car screen.
 *
 * Autodroid is registered as a navigation app, so Android Auto gives it the whole screen through
 * NavigationTemplate: the drawing surface (handled by [SurfaceRenderer]) with no content card.
 * The action strip (required by the template, drawn by Android Auto) holds one icon: Video on the
 * dashboard, and back to the dashboard in video mode, plus the pan button so the page can scroll.
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
        val builder = NavigationTemplate.Builder()
        if (video.open.value) {
            val back = Action.Builder()
                .setIcon(icon(R.drawable.ic_car_dashboard))
                .setOnClickListener { video.close(null) }
                .build()
            builder.setActionStrip(ActionStrip.Builder().addAction(back).build())
            if (carContext.carAppApiLevel >= 2) {
                builder.setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            }
        } else {
            // 12h / 24h is switched by tapping the clock on the dashboard.
            val openVideo = Action.Builder()
                .setIcon(icon(R.drawable.ic_car_video))
                .setOnClickListener { video.requestOpen() }
                .build()
            builder.setActionStrip(ActionStrip.Builder().addAction(openVideo).build())
        }
        return builder.build()
    }

    private fun icon(res: Int) = CarIcon.Builder(IconCompat.createWithResource(carContext, res)).build()
}
