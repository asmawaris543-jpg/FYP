package com.svgs.app.core.mode

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModeManager {

    private val _currentMode = MutableStateFlow(Mode.IDLE)

    val currentMode: StateFlow<Mode> = _currentMode.asStateFlow()

    fun setMode(mode: Mode) {
        _currentMode.value = mode
    }

    fun stop() {
        _currentMode.value = Mode.IDLE
    }
}
