package com.svgs.app.core.command

import com.svgs.app.core.mode.Mode
import com.svgs.app.core.mode.ModeManager

class CommandRouter(
    private val modeManager: ModeManager
) {

    fun route(command: Command) {
        when (command) {

            Command.StartObjectDetection -> {
                modeManager.setMode(Mode.OBJECT_DETECTION)
            }

            Command.ReadText -> {
                modeManager.setMode(Mode.OCR)
            }

            Command.ScanCurrency -> {
                modeManager.setMode(Mode.CURRENCY)
            }

            Command.Stop -> {
                modeManager.stop()
            }

            Command.CheckBattery -> {
                // Battery functionality will be implemented later.
            }
        }
    }
}
