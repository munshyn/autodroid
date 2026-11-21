package com.husny.autodroid.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Tiny HTTP helper: GET a URL and return the body, or null on any failure. */
internal object Http {
    private const val USER_AGENT = "Autodroid/0.4 (personal Android Auto app)"

    suspend fun get(url: String, readTimeoutMs: Int = 15_000): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = readTimeoutMs
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode !in 200..299) return@runCatching null
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
