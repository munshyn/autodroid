package com.husny.autodroid.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * One car-screen session. Owns the data (prayer times, music, lyrics) and the renderer that draws
 * Autodroid's own UI onto the car display.
 */
class AutodroidSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        android.util.Log.i("Autodroid", "Car session started")
        val model = DashboardModel(carContext, lifecycle)
        val renderer = SurfaceRenderer(carContext, lifecycle, model)
        return HomeScreen(carContext, renderer)
    }
}
