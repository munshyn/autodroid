package com.husny.autodroid.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import com.husny.autodroid.settings.SettingsStore

/**
 * The only car screen for now.
 *
 * Autodroid is registered as a navigation app, so Android Auto gives it the whole screen through
 * NavigationTemplate: the drawing surface (handled by [SurfaceRenderer]) with no content card.
 * The template requires an action strip; ours holds one button that switches the clock 12h/24h,
 * which also tests that template buttons reach our UI.
 */
class HomeScreen(
    carContext: CarContext,
    private val renderer: SurfaceRenderer,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val clockToggle = Action.Builder()
            .setTitle("12h / 24h")
            .setOnClickListener {
                SettingsStore.update { it.copy(use24h = !it.use24h) }
            }
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(clockToggle).build())
            .build()
    }
}
