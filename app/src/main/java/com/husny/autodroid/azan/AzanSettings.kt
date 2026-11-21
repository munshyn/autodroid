package com.husny.autodroid.azan

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.husny.autodroid.settings.SettingsStore
import java.io.File

/** Which azan recording slot: the one for all prayers, or a separate one for Subuh. */
enum class AzanSlot(val fileName: String, val nameKey: String) {
    MAIN("azan_audio", "audio_name"),
    SUBUH("azan_subuh_audio", "subuh_audio_name"),
}

/**
 * Azan recordings (built in or chosen by the user) plus the azan-related values from
 * [SettingsStore]. Chosen recordings are copied into the app so they always work offline.
 */
class AzanSettings(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("azan", Context.MODE_PRIVATE)

    init {
        SettingsStore.init(appContext)
    }

    val volume: Float get() = SettingsStore.current.azanVolumePercent / 100f
    val resumeMusic: Boolean get() = SettingsStore.current.resumeMusic

    fun file(slot: AzanSlot): File = File(appContext.filesDir, "azan/${slot.fileName}")
    fun hasCustom(slot: AzanSlot): Boolean = file(slot).let { it.exists() && it.length() > 0 }

    fun audioName(slot: AzanSlot): String =
        prefs.getString(slot.nameKey, null)?.takeIf { hasCustom(slot) }
            ?: if (slot == AzanSlot.MAIN) DEFAULT_AUDIO_NAME else "Same as other prayers"

    fun useDefault(slot: AzanSlot) {
        file(slot).delete()
        prefs.edit().remove(slot.nameKey).apply()
    }

    /** Last azan handled ("2026-11-21-MAGHRIB"), so a reconnect doesn't repeat it. */
    var lastHandled: String?
        get() = prefs.getString("last_handled", null)
        set(value) = prefs.edit().putString("last_handled", value).apply()

    /** Copies the chosen file into app storage. Returns false if it couldn't be read. */
    fun importAudio(slot: AzanSlot, uri: Uri): Boolean {
        val resolver = appContext.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "azan audio"
        val target = file(slot)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${slot.fileName}.tmp")
        val ok = runCatching {
            resolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } } != null
        }.getOrDefault(false)
        if (!ok || temp.length() == 0L) {
            temp.delete()
            return false
        }
        temp.renameTo(target)
        prefs.edit().putString(slot.nameKey, name).apply()
        return true
    }

    companion object {
        const val DEFAULT_AUDIO_NAME = "Adzan Mekkah – Abdul Aziz Zahrani (built in)"
    }
}
