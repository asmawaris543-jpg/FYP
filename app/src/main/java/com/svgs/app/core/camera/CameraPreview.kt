package com.svgs.app.core.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.svgs.app.core.detection.DetectionResult
import com.svgs.app.core.detection.DetectionResultProcessor
import com.svgs.app.core.detection.ObjectDetector
import com.svgs.app.core.detection.TestObjectDetector
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    analysisEnabled: Boolean = false,
    onDetectionResults: (List<DetectionResult>) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode =
                PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val latestResultsCallback = rememberUpdatedState(
        onDetectionResults
    )

    // The analyzer runs on a worker thread.
    val analysisAllowed = remember {
        AtomicBoolean(analysisEnabled)
    }

    SideEffect {
        analysisAllowed.set(analysisEnabled)
    }

    DisposableEffect(context, lifecycleOwner, previewView) {
        val disposed = AtomicBoolean(false)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()

        val objectDetector: ObjectDetector = TestObjectDetector()

        val resultProcessor = DetectionResultProcessor(
            minimumConfidence = 0.60f
        )

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(
                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            )
            .build()

        var boundCameraProvider: ProcessCameraProvider? = null

        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            try {
                if (
                    !disposed.get() &&
                    analysisAllowed.get()
                ) {
                    val rawResults = objectDetector.detect(imageProxy)
                    val results = resultProcessor.process(rawResults)

                    mainExecutor.execute {
                        if (
                            !disposed.get() &&
                            analysisAllowed.get()
                        ) {
                            latestResultsCallback.value(results)
                        }
                    }
                }
            } catch (exception: Exception) {
                Log.e(
                    "CameraPreview",
                    "Frame analysis failed",
                    exception
                )
            } finally {
                imageProxy.close()
            }
        }

        val cameraPermissionGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        if (cameraPermissionGranted) {
            val cameraProviderFuture =
                ProcessCameraProvider.getInstance(context)

            cameraProviderFuture.addListener(
                {
                    if (!disposed.get()) {
                        try {
                            val cameraProvider =
                                cameraProviderFuture.get()

                            boundCameraProvider = cameraProvider

                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )

                            Log.d(
                                "CameraPreview",
                                "Camera preview and analysis bound"
                            )
                        } catch (exception: Exception) {
                            Log.e(
                                "CameraPreview",
                                "Could not start camera",
                                exception
                            )
                        }
                    }
                },
                mainExecutor
            )
        } else {
            Log.w(
                "CameraPreview",
                "Camera permission not granted"
            )
        }

        onDispose {
            disposed.set(true)
            imageAnalysis.clearAnalyzer()

            boundCameraProvider?.unbind(
                preview,
                imageAnalysis
            )

            // Close the detector after already queued analysis work.
            analysisExecutor.execute {
                try {
                    objectDetector.close()
                } catch (exception: Exception) {
                    Log.e(
                        "CameraPreview",
                        "Could not close detector",
                        exception
                    )
                }
            }

            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}