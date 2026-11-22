package com.husny.autodroid.car

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * The YouTube web page shown on the car screen in video mode (parked only, see [VideoMode]).
 * One WebView per car-screen surface, kept while hidden so reopening returns to the same page.
 */
class VideoLayer(private val context: Context) {

    val view: FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(Color.BLACK)
        visibility = View.GONE
    }

    private var web: WebView? = null
    private var fullScreen: View? = null
    private var fullScreenCallback: WebChromeClient.CustomViewCallback? = null

    val isShowing: Boolean get() = view.visibility == View.VISIBLE

    /** Shows the page, loading [url] if given (or YouTube's home page the first time). */
    fun show(url: String?) {
        val w = web ?: create().also { web = it }
        view.visibility = View.VISIBLE
        w.onResume()
        w.resumeTimers()
        when {
            url != null -> w.loadUrl(url)
            w.url == null -> w.loadUrl(VideoMode.YOUTUBE_HOME)
        }
    }

    /** Hides the page and stops any video or sound on it. */
    fun hide() {
        val w = web ?: return
        w.evaluateJavascript(PAUSE_ALL, null)
        leaveFullScreen()
        w.onPause()
        view.visibility = View.GONE
    }

    fun scrollBy(dx: Float, dy: Float) {
        val target = web ?: return
        target.scrollBy(dx.toInt(), dy.toInt())
    }

    fun fling(vx: Float, vy: Float) {
        web?.flingScroll(-vx.toInt(), -vy.toInt())
    }

    fun release() {
        leaveFullScreen()
        web?.let {
            it.stopLoading()
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        web = null
        CookieManager.getInstance().flush()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView {
        val w = WebView(context)
        w.setBackgroundColor(Color.BLACK)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.webViewClient = object : WebViewClient() {
            // Keep YouTube links inside the car view instead of handing them to other apps.
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                return scheme != "http" && scheme != "https"
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            // The player's full-screen button: show the video over the whole car screen.
            override fun onShowCustomView(v: View, callback: CustomViewCallback) {
                leaveFullScreen()
                fullScreen = v
                fullScreenCallback = callback
                view.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            }

            override fun onHideCustomView() {
                fullScreen?.let { view.removeView(it) }
                fullScreen = null
                fullScreenCallback = null
            }
        }
        view.addView(w, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return w
    }

    private fun leaveFullScreen() {
        val v = fullScreen ?: return
        view.removeView(v)
        fullScreen = null
        fullScreenCallback?.onCustomViewHidden()
        fullScreenCallback = null
    }

    private companion object {
        const val PAUSE_ALL = "document.querySelectorAll('video,audio').forEach(function(m){m.pause()});"
    }
}
