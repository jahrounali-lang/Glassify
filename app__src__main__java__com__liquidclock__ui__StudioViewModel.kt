package com.liquidclock.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.liquidclock.model.ClockConfig
import com.liquidclock.model.ConfigStore
import com.liquidclock.render.Backgrounds
import com.liquidclock.render.FontProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class StudioViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = ConfigStore(app)
    private var all by mutableStateOf(store.designs())
    var config by mutableStateOf(store.active()); private set
    var rev by mutableIntStateOf(0); private set
    var message by mutableStateOf<String?>(null); private set
    var fontAvailable by mutableStateOf(FontProvider.isAvailable(app)); private set
    private var job: Job? = null

    fun designs(): List<ClockConfig> = all.map { if (it.id == config.id) config else it }

    fun update(f: (ClockConfig) -> ClockConfig) = setConfig(f(config))

    fun setConfig(c: ClockConfig) {
        config = c
        job?.cancel()
        job = viewModelScope.launch { delay(200); commit() }   // debounce: the lock screen updates live
    }

    private fun commit() { all = designs(); store.save(all, config.id) }

    fun select(id: String) {
        commit()
        all.firstOrNull { it.id == id }?.let { config = it; store.save(all, id) }
    }

    fun saveAsNew(name: String) {
        commit()
        val n = config.copy(id = UUID.randomUUID().toString(), name = name.ifBlank { "Design ${all.size + 1}" })
        all = all + n; config = n; store.save(all, n.id)
    }

    fun duplicate(id: String) {
        commit()
        val src = all.firstOrNull { it.id == id } ?: return
        val n = src.copy(id = UUID.randomUUID().toString(), name = src.name + " copy")
        all = all + n; config = n; store.save(all, n.id)
    }

    fun delete(id: String) {
        commit()
        if (all.size <= 1) return
        all = all.filter { it.id != id }
        if (config.id == id) config = all.first()
        store.save(all, config.id)
    }

    fun importFont(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { FontProvider.import(app, uri) }
            fontAvailable = FontProvider.isAvailable(app)
            message = if (ok) "Font imported" else "That file is not a valid TTF/OTF font"
            rev++; store.touch()
        }
    }

    fun importWallpaper(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Backgrounds.importUri(app, uri) }
            if (ok) update { it.copy(wallpaper = "custom") }
            message = if (ok) "Wallpaper imported" else "Could not read that image"
            rev++; store.touch()
        }
    }

    override fun onCleared() { commit() }
}
