package com.svgs.app.core.detection

import android.os.SystemClock

class DetectionAnnouncementController(
    private val minimumIntervalMillis: Long = 2_000L,
    private val repeatIntervalMillis: Long = 5_000L
) {

    private val formatter = DetectionAnnouncementFormatter()

    private var lastAnnouncement: String? = null
    private var lastAnnouncementTime: Long? = null

    init {
        require(minimumIntervalMillis >= 0) {
            "Minimum interval must not be negative."
        }

        require(repeatIntervalMillis >= minimumIntervalMillis) {
            "Repeat interval must be at least the minimum interval."
        }
    }

    fun nextAnnouncement(
        results: List<DetectionResult>
    ): String? {
        val selectedResult = results
            .filter { it.confidence.isFinite() }
            .maxByOrNull { it.confidence }
            ?: return null

        val announcement = formatter.format(selectedResult)
        val now = SystemClock.elapsedRealtime()
        val previousTime = lastAnnouncementTime

        if (previousTime != null) {
            val elapsed = now - previousTime

            val requiredInterval =
                if (announcement == lastAnnouncement) {
                    repeatIntervalMillis
                } else {
                    minimumIntervalMillis
                }

            if (elapsed < requiredInterval) {
                return null
            }
        }

        lastAnnouncement = announcement
        lastAnnouncementTime = now

        return announcement
    }

    fun reset() {
        lastAnnouncement = null
        lastAnnouncementTime = null
    }
}