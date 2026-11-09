package com.svgs.app.core.detection

import androidx.camera.core.ImageProxy

class TestObjectDetector : ObjectDetector {

    override fun detect(
        image: ImageProxy
    ): List<DetectionResult> {
        // Placeholder only. No real object recognition is performed.
        // The camera analyzer owns ImageProxy cleanup.
        return emptyList()
    }

    override fun close() {
        // No model resources to release.
    }
}