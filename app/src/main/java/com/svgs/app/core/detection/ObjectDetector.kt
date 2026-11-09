
package com.svgs.app.core.detection

import androidx.camera.core.ImageProxy

interface ObjectDetector {
    fun detect(image: ImageProxy): List<DetectionResult>

    fun close()
}
