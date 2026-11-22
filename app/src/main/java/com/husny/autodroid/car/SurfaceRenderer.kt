package com.husny.autodroid.car

import android.app.Presentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.core.view.drawToBitmap
import java.io.File
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.husny.autodroid.R
import com.husny.autodroid.settings.SettingsStore
import com.husny.autodroid.ui.CarDashboard

/** What we know about the car display. Shown on screen in this test build. */
data class SurfaceInfo(
    val width: Int = 0,
    val height: Int = 0,
    val dpi: Int = 0,
    val visibleArea: Rect? = null,
    val stableArea: Rect? = null,
    val carApiLevel: Int = 0,
)

/**
 * Draws Autodroid's Compose UI onto the drawing surface Android Auto gives us.
 *
 * How it works: Android Auto hands us a Surface. We create a private VirtualDisplay on it and show a
 * Presentation (a window on that display) that hosts a normal ComposeView. Taps arrive through
 * [SurfaceCallback.onClick] and are forwarded to the Compose view as touch events.
 */
class SurfaceRenderer(
    private val carContext: CarContext,
    private val lifecycle: Lifecycle,
    private val model: DashboardModel,
    private val video: VideoMode,
) : DefaultLifecycleObserver {

    val info = mutableStateOf(SurfaceInfo())


    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var composeOwner: ComposeHostOwner? = null
    private var videoLayer: VideoLayer? = null
    private var videoJob: Job? = null

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            Log.i(TAG, "Surface ${surfaceContainer.width}x${surfaceContainer.height} @ ${surfaceContainer.dpi}dpi")
            showOn(surfaceContainer)
        }

        override fun onVisibleAreaChanged(visibleArea: Rect) {
            Log.i(TAG, "Visible area $visibleArea")
            info.value = info.value.copy(visibleArea = Rect(visibleArea))
        }

        override fun onStableAreaChanged(stableArea: Rect) {
            Log.i(TAG, "Stable area $stableArea")
            info.value = info.value.copy(stableArea = Rect(stableArea))
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            release()
        }

        override fun onClick(x: Float, y: Float) {
            forwardTap(x, y)
        }

        // Scrolling only matters for the video page (the dashboard doesn't scroll).
        override fun onScroll(distanceX: Float, distanceY: Float) {
            videoLayer?.takeIf { it.isShowing }?.scrollBy(distanceX, distanceY)
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            videoLayer?.takeIf { it.isShowing }?.fling(velocityX, velocityY)
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        Log.i(TAG, "Renderer created, asking for the car surface")
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        release()
    }

    private fun showOn(container: SurfaceContainer) {
        val surface = container.surface
        if (surface == null || container.width <= 0 || container.height <= 0 || container.dpi <= 0) {
            Log.w(TAG, "Unusable surface: surface=$surface ${container.width}x${container.height} dpi=${container.dpi}")
            return
        }
        release()

        info.value = info.value.copy(
            width = container.width,
            height = container.height,
            dpi = container.dpi,
            carApiLevel = carContext.carAppApiLevel,
        )

        val displayManager = carContext.getSystemService(DisplayManager::class.java)
        val display = displayManager.createVirtualDisplay(
            "AutodroidDisplay",
            container.width,
            container.height,
            container.dpi,
            surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        )
        virtualDisplay = display

        val owner = ComposeHostOwner().also { composeOwner = it }
        val window = Presentation(carContext, display.display, R.style.Theme_Autodroid)
        val view = ComposeView(window.context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                CarDashboard(
                    info = info.value,
                    model = model,
                    onToggleClock = { SettingsStore.update { it.copy(use24h = !it.use24h) } },
                )
            }
        }
        val layer = VideoLayer(window.context).also { videoLayer = it }
        val root = FrameLayout(window.context).apply {
            addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(layer.view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        window.setContentView(root)
        window.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(owner)
            it.setViewTreeSavedStateRegistryOwner(owner)
        }
        owner.start()
        window.show()
        presentation = window
        registerDebugCapture()

        // Video mode: show or hide the YouTube page over the dashboard.
        videoJob = lifecycle.coroutineScope.launch {
            launch {
                video.open.collect { open ->
                    if (open) {
                        layer.show(video.load.value)
                        video.loaded()
                        view.visibility = View.INVISIBLE
                    } else {
                        layer.hide()
                        view.visibility = View.VISIBLE
                    }
                }
            }
            launch {
                video.load.collect { url ->
                    if (url != null && video.open.value) {
                        layer.show(url)
                        video.loaded()
                    }
                }
            }
        }
    }

    private var captureReceiver: BroadcastReceiver? = null

    /**
     * Debug builds only: `adb shell am broadcast -a com.husny.autodroid.DEBUG_CAPTURE` saves the
     * car screen to Android/data/com.husny.autodroid/files/car_screen.png (for checking layouts,
     * since the phone can't screenshot the car display).
     */
    private fun registerDebugCapture() {
        if (carContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        if (captureReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val root = presentation?.window?.decorView ?: return
                runCatching {
                    val bitmap = root.drawToBitmap()
                    val file = File(carContext.getExternalFilesDir(null), "car_screen.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    Log.i(TAG, "Saved car screen to $file")
                }.onFailure { Log.w(TAG, "Capture failed", it) }
            }
        }
        ContextCompat.registerReceiver(
            carContext, receiver, IntentFilter(DEBUG_CAPTURE), ContextCompat.RECEIVER_EXPORTED,
        )
        captureReceiver = receiver
    }

    private fun forwardTap(x: Float, y: Float) {
        val root = presentation?.window?.decorView ?: return
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(downTime, downTime + 50, MotionEvent.ACTION_UP, x, y, 0)
        root.dispatchTouchEvent(down)
        root.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun release() {
        captureReceiver?.let { runCatching { carContext.unregisterReceiver(it) } }
        captureReceiver = null
        videoJob?.cancel()
        videoJob = null
        videoLayer?.release()
        videoLayer = null
        composeOwner?.stop()
        composeOwner = null
        presentation?.dismiss()
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private companion object {
        const val TAG = "Autodroid"
        const val DEBUG_CAPTURE = "com.husny.autodroid.DEBUG_CAPTURE"
    }
}

/** Gives the Compose view a lifecycle outside of an Activity. */
private class ComposeHostOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
