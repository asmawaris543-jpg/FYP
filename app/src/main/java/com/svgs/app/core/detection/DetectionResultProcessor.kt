
package com.svgs.app.core.detection

class DetectionResultProcessor(
    private val minimumConfidence: Float = 0.60f
) {
    init {
        require(minimumConfidence in 0f..1f) {
            "Minimum confidence must be between 0 and 1"
        }
    }

    fun process(
        results: List<DetectionResult>
    ): List<DetectionResult> {
        return results.filter { result ->
            result.confidence >= minimumConfidence
        }
    }
}
