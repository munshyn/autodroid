package com.husny.autodroid.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Malaysia's prayer times are published in local time. */
val MALAYSIA: ZoneId = ZoneId.of("Asia/Kuala_Lumpur")

enum class Prayer(val label: String, val isMain: Boolean) {
    IMSAK("Imsak", false),
    FAJR("Subuh", true),
    SYURUK("Syuruk", false),
    DHUHR("Zohor", true),
    ASR("Asar", true),
    MAGHRIB("Maghrib", true),
    ISHA("Isyak", true),
}

data class PrayerDay(
    val date: LocalDate,
    /** JAKIM's Hijri date, "yyyy-MM-dd", e.g. "1448-05-21". */
    val hijri: String,
    val times: Map<Prayer, LocalTime>,
)

data class PrayerMoment(val prayer: Prayer, val at: LocalDateTime)

/** The prayer coming up next (main five only) and the one after it. */
data class UpcomingPrayers(val next: PrayerMoment, val then: PrayerMoment?)

fun upcomingPrayers(days: List<PrayerDay>, now: LocalDateTime): UpcomingPrayers? {
    val moments = mainPrayerMoments(days).filter { it.at.isAfter(now) }
    val next = moments.firstOrNull() ?: return null
    return UpcomingPrayers(next, moments.getOrNull(1))
}

/** Every Subuh, Zohor, Asar, Maghrib and Isyak in [days], in time order. */
fun mainPrayerMoments(days: List<PrayerDay>): List<PrayerMoment> = days
    .sortedBy { it.date }
    .flatMap { day ->
        Prayer.entries.filter { it.isMain }.mapNotNull { p ->
            day.times[p]?.let { PrayerMoment(p, LocalDateTime.of(day.date, it)) }
        }
    }

private val HIJRI_MONTHS = listOf(
    "Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir",
    "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijjah",
)

/** "1448-05-21" → "21 Jamadilawal 1448". */
fun formatHijri(hijri: String): String {
    val parts = hijri.split("-")
    if (parts.size != 3) return hijri
    val month = parts[1].toIntOrNull()?.let { HIJRI_MONTHS.getOrNull(it - 1) } ?: return hijri
    return "${parts[2].trimStart('0')} $month ${parts[0]}"
}

/**
 * Loads JAKIM prayer times for one zone, a month at a time, and keeps them on the phone so they
 * work without signal. Source: JAKIM e-solat (official); backup: api.waktusolat.app (a free
 * mirror of the same JAKIM data).
 */
class PrayerRepository(context: Context) {

    private val cacheDir = File(context.filesDir, "prayer").apply { mkdirs() }

    suspend fun month(zone: String, month: YearMonth): List<PrayerDay>? {
        readCache(zone, month)?.let { return it }
        val fetched = fetchEsolat(zone, month) ?: fetchWaktuSolat(zone, month) ?: return null
        writeCache(zone, month, fetched)
        return fetched
    }

    private fun cacheFile(zone: String, month: YearMonth) = File(cacheDir, "${zone}_$month.json")

    private suspend fun readCache(zone: String, month: YearMonth): List<PrayerDay>? =
        withContext(Dispatchers.IO) {
            val file = cacheFile(zone, month)
            if (!file.exists()) return@withContext null
            runCatching {
                val array = JSONArray(file.readText())
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    val times = o.getJSONObject("times")
                    PrayerDay(
                        date = LocalDate.parse(o.getString("date")),
                        hijri = o.getString("hijri"),
                        times = Prayer.entries.mapNotNull { p ->
                            times.optString(p.name).takeIf { it.isNotEmpty() }?.let { p to LocalTime.parse(it) }
                        }.toMap(),
                    )
                }.takeIf { it.isNotEmpty() }
            }.getOrNull()
        }

    private suspend fun writeCache(zone: String, month: YearMonth, days: List<PrayerDay>) =
        withContext(Dispatchers.IO) {
            val array = JSONArray()
            days.forEach { day ->
                val times = JSONObject()
                day.times.forEach { (p, t) -> times.put(p.name, t.toString()) }
                array.put(
                    JSONObject().put("date", day.date.toString()).put("hijri", day.hijri).put("times", times)
                )
            }
            runCatching { cacheFile(zone, month).writeText(array.toString()) }
        }

    /** e-solat only serves the current month (`period=month`), so only use it for that. */
    private suspend fun fetchEsolat(zone: String, month: YearMonth): List<PrayerDay>? {
        if (month != YearMonth.now(MALAYSIA)) return null
        val body = Http.get(
            "https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=month&zone=${Http.encode(zone)}"
        ) ?: return null
        return runCatching {
            val list = JSONObject(body).getJSONArray("prayerTime")
            val dateFormat = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
            val keys = mapOf(
                Prayer.IMSAK to "imsak", Prayer.FAJR to "fajr", Prayer.SYURUK to "syuruk",
                Prayer.DHUHR to "dhuhr", Prayer.ASR to "asr", Prayer.MAGHRIB to "maghrib", Prayer.ISHA to "isha",
            )
            (0 until list.length()).map { i ->
                val o = list.getJSONObject(i)
                PrayerDay(
                    date = LocalDate.parse(o.getString("date"), dateFormat),
                    hijri = o.getString("hijri"),
                    times = keys.mapNotNull { (p, key) ->
                        o.optString(key).takeIf { it.isNotEmpty() }?.let { p to LocalTime.parse(it) }
                    }.toMap(),
                )
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private suspend fun fetchWaktuSolat(zone: String, month: YearMonth): List<PrayerDay>? {
        val body = Http.get(
            "https://api.waktusolat.app/v2/solat/${Http.encode(zone)}?year=${month.year}&month=${month.monthValue}"
        ) ?: return null
        return runCatching {
            val list = JSONObject(body).getJSONArray("prayers")
            val keys = mapOf(
                Prayer.IMSAK to "imsak", Prayer.FAJR to "fajr", Prayer.SYURUK to "syuruk",
                Prayer.DHUHR to "dhuhr", Prayer.ASR to "asr", Prayer.MAGHRIB to "maghrib", Prayer.ISHA to "isha",
            )
            (0 until list.length()).map { i ->
                val o = list.getJSONObject(i)
                PrayerDay(
                    date = month.atDay(o.getInt("day")),
                    hijri = o.getString("hijri"),
                    times = keys.mapNotNull { (p, key) ->
                        if (!o.has(key)) return@mapNotNull null
                        p to Instant.ofEpochSecond(o.getLong(key)).atZone(MALAYSIA).toLocalTime()
                    }.toMap(),
                )
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}
