
package com.svgs.app.core.detection

import android.graphics.RectF

object DetectionPositionHelper {

    fun getRelativePosition(
        boundingBox: RectF,
        imageWidth: Int
    ): RelativePosition {
        if (imageWidth <= 0) {
            return RelativePosition.CENTER
        }

        val objectCenterX = boundingBox.centerX()
        val imageCenterX = imageWidth / 2f

        return when {
            objectCenterX < imageWidth / 3f -> RelativePosition.LEFT
            objectCenterX > imageWidth * 2f / 3f -> RelativePosition.RIGHT
            else -> RelativePosition.CENTER
        }
    }
}
