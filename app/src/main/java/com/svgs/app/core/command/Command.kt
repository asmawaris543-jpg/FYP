package com.svgs.app.core.command

sealed interface Command {

    data object StartObjectDetection : Command

    data object ReadText : Command

    data object ScanCurrency : Command

    data object Stop : Command

    data object CheckBattery : Command
}
