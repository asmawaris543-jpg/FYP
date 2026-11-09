package com.svgs.app.core.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.vosk.Model
import org.vosk.android.StorageService

class WakeWordModelLoader(
    context: Context
) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var loadedModel: Model? = null
    private var loading = false
    private var closed = false

    // Call from the main thread.
    // This loader owns the model; callers must not close it directly.
    fun load(
        onReady: (Model) -> Unit,
        onError: (String) -> Unit
    ) {
        if (closed) {
            onError("The model loader has been closed.")
            return
        }

        val existingModel = loadedModel

        if (existingModel != null) {
            onReady(existingModel)
            return
        }

        if (loading) {
            onError("The wake-word model is still loading.")
            return
        }

        loading = true

        StorageService.unpack(
            appContext,
            "vosk-model-small-hi-0.22",
            "wake-word-model",
            { model ->
                mainHandler.post {
                    loading = false

                    if (closed) {
                        // Loading finished after the owner was destroyed.
                        model.close()
                    } else {
                        loadedModel = model
                        onReady(model)
                    }
                }
            },
            { exception ->
                mainHandler.post {
                    loading = false

                    if (!closed) {
                        onError(
                            "Wake-word model could not be loaded: " +
                                    (exception.message ?: "Unknown file error")
                        )
                    }
                }
            }
        )
    }

    // Stop and release any recognizer using the model before calling this.
    // Call from the main thread.
    fun close() {
        if (closed) return

        closed = true
        loadedModel?.close()
        loadedModel = null
    }
}