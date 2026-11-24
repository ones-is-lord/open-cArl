package dev.opencarl.data

import android.content.Context
import dev.opencarl.appearance.AppearanceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsRepository(context: Context, private val secure: SecureStore) {

    private val prefs = context.getSharedPreferences("open_carl_settings", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val lock = Any()

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching { json.decodeFromString<AppSettings>(raw) }.getOrElse { AppSettings() }
    }

    fun update(block: (AppSettings) -> AppSettings) {
        synchronized(lock) {
            val next = block(_settings.value)
            prefs.edit().putString(KEY_SETTINGS, json.encodeToString(next)).apply()
            _settings.value = next
        }
    }

    fun updateAppearance(block: (AppearanceSettings) -> AppearanceSettings) =
        update { it.copy(appearance = block(it.appearance)) }

    fun apiKey(providerId: String): String = secure.get("provider.$providerId").orEmpty()

    fun setApiKey(providerId: String, key: String) {
        if (key.isBlank()) secure.remove("provider.$providerId") else secure.put("provider.$providerId", key.trim())
    }

    fun secret(name: String): String = secure.get(name).orEmpty()

    fun setSecret(name: String, value: String) {
        if (value.isBlank()) secure.remove(name) else secure.put(name, value.trim())
    }

    fun hasSecret(name: String): Boolean = secure.has(name)

    companion object {
        private const val KEY_SETTINGS = "settings_json"
        const val SECRET_GITHUB = "integration.github"
        const val SECRET_HF = "integration.huggingface"
        const val SECRET_TAVILY = "integration.tavily"
    }
}
