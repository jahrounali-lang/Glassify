package com.liquidclock.model

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Persists saved designs. The wallpaper service listens to this store, so edits reach the lock screen live. */
class ConfigStore(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("liquid_clock", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun designs(): List<ClockConfig> =
        runCatching { json.decodeFromString<List<ClockConfig>>(sp.getString("designs", null)!!) }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(ClockConfig())

    fun active(): ClockConfig {
        val all = designs(); val id = sp.getString("active", null)
        return all.firstOrNull { it.id == id } ?: all.first()
    }

    fun save(all: List<ClockConfig>, activeId: String) {
        sp.edit().putString("designs", json.encodeToString(all)).putString("active", activeId).apply()
    }

    fun touch() { sp.edit().putLong("rev", System.nanoTime()).apply() }
    fun register(l: SharedPreferences.OnSharedPreferenceChangeListener) = sp.registerOnSharedPreferenceChangeListener(l)
    fun unregister(l: SharedPreferences.OnSharedPreferenceChangeListener) = sp.unregisterOnSharedPreferenceChangeListener(l)
}
