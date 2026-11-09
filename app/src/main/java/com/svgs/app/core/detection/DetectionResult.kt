
package com.svgs.app.core.detection

import android.graphics.RectF

data class DetectionResult(
    val className: String,
    val confidence: Float,
    val boundingBox: RectF,
    val estimatedDistance: Float?,
    val relativePosition: RelativePosition,
    val timestamp: Long
)

enum class RelativePosition {
    LEFT,
    CENTER,
    RIGHT
}
