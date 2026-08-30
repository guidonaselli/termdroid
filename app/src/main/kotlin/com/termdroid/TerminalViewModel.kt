package com.termdroid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termdroid.probe.CapabilityProbe
import com.termdroid.terminal.ShellSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    val capabilities = CapabilityProbe(app).get()

    private val _sessions = MutableStateFlow<List<ShellSession>>(emptyList())
    val sessions: StateFlow<List<ShellSession>> = _sessions

    private val _activeIndex = MutableStateFlow(0)
    val activeIndex: StateFlow<Int> = _activeIndex

    val active: ShellSession? get() = _sessions.value.getOrNull(_activeIndex.value)

    init {
        com.termdroid.rootfs.RootfsManager(app).ensureBaseEnvironment()
        if (capabilities.hasShell) nueva()
    }

    fun nueva() {
        val s = ShellSession(getApplication(), capabilities.backend, viewModelScope)
        s.start()
        _sessions.update { it + s }
        _activeIndex.value = _sessions.value.lastIndex
    }

    fun seleccionar(index: Int) {
        if (index in _sessions.value.indices) _activeIndex.value = index
    }

    fun cerrar(index: Int) {
        val actuales = _sessions.value
        val s = actuales.getOrNull(index) ?: return
        s.stop()
        val restantes = actuales.filterIndexed { i, _ -> i != index }
        _sessions.value = restantes
        _activeIndex.value = _activeIndex.value.coerceIn(0, maxOf(restantes.lastIndex, 0))
        if (restantes.isEmpty() && capabilities.hasShell) nueva()
    }

    override fun onCleared() {
        super.onCleared()
        _sessions.value.forEach { it.stop() }
    }
}
