package com.svgs.app.core.detection

class DetectionAnnouncementFormatter {

    fun format(result: DetectionResult): String {
        val objectName = result.className
            .replace("_", " ")
            .trim()
            .ifBlank { "Object" }

        val position = when (result.relativePosition) {
            RelativePosition.LEFT -> "on your left"
            RelativePosition.CENTER -> "ahead of you"
            RelativePosition.RIGHT -> "on your right"
        }

        return "$objectName $position."
    }
}