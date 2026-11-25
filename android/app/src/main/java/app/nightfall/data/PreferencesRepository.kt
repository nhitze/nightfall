package app.nightfall.data

import android.content.Context
import app.nightfall.domain.NightfallPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface PreferencesStorage {
    fun read(): String?

    fun write(value: String)
}

class PreferencesRepository(
    private val storage: PreferencesStorage,
    private val json: Json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        },
) {
    fun load(): NightfallPreferences {
        val saved = storage.read() ?: return NightfallPreferences.initial
        return runCatching { json.decodeFromString<NightfallPreferences>(saved) }
            .getOrDefault(NightfallPreferences.initial)
    }

    fun save(preferences: NightfallPreferences) {
        storage.write(json.encodeToString(preferences))
    }
}

class SharedPreferencesStorage(
    context: Context,
) : PreferencesStorage {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(PREFERENCES_KEY, null)

    override fun write(value: String) {
        preferences.edit().putString(PREFERENCES_KEY, value).apply()
    }

    private companion object {
        const val PREFERENCES_FILE = "nightfall"
        const val PREFERENCES_KEY = "preferences-v1"
    }
}
