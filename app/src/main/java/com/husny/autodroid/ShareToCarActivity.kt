package com.husny.autodroid

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.husny.autodroid.car.SharedVideo

/**
 * "Share › Autodroid" from the YouTube app (or a browser): sends the video to the car screen,
 * where it plays once the car is stopped. Has no screen of its own.
 */
class ShareToCarActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = youtubeUrl(text)
        if (url == null) {
            Toast.makeText(this, "Autodroid: that isn't a YouTube link", Toast.LENGTH_SHORT).show()
        } else {
            SharedVideo.send(url)
            Toast.makeText(this, "Sent to the car screen. It plays when the car is stopped.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    /** The first YouTube link in [text], as a mobile YouTube address; null if there isn't one. */
    private fun youtubeUrl(text: String): String? {
        val link = Regex("https?://\\S+").find(text)?.value ?: return null
        val uri = Uri.parse(link)
        val host = uri.host?.lowercase() ?: return null
        return when {
            host == "youtu.be" -> uri.lastPathSegment?.let { id ->
                "https://m.youtube.com/watch?v=$id" + (uri.getQueryParameter("t")?.let { "&t=$it" } ?: "")
            }
            host == "youtube.com" || host.endsWith(".youtube.com") ->
                uri.buildUpon().authority("m.youtube.com").scheme("https").build().toString()
            else -> null
        }
    }
}
