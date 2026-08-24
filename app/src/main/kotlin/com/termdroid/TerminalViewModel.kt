package com.termdroid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.termdroid.probe.CapabilityProbe
import com.termdroid.terminal.ShellSession

/** Dueno de la sesion de terminal. */
class TerminalViewModel(app: Application) : AndroidViewModel(app) {

    val capabilities = CapabilityProbe(app).get()

    val session = ShellSession(app, capabilities.backend, viewModelScope)

    init {
        if (capabilities.hasShell) session.start()
    }

    override fun onCleared() {
        super.onCleared()
        session.stop()
    }
}
