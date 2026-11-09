package com.svgs.app.core.speech

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.concurrent.Executors

class WakeWordManager(
    private val model: Model,
    private val onWakeWord: () -> Unit,
    private val onTranscript: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    // Access model-dependent resources only on the worker thread.
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null

    // Public methods and recognition callbacks run on the main thread.
    private var generation = 0
    private var listening = false
    private var closed = false

    fun start() {
        if (closed || listening) return

        generation += 1
        val session = generation
        listening = true

        worker.execute {
            try {
                releaseResources()

                val grammar = JSONArray()
                    .put("मदद")
                    .put("[unk]")
                    .toString()

                val newRecognizer = Recognizer(
                    model,
                    16_000f,
                    grammar
                )

                recognizer = newRecognizer

                val newService = SpeechService(
                    newRecognizer,
                    16_000f
                )

                speechService = newService

                check(
                    newService.startListening(
                        createListener(session)
                    )
                ) {
                    "Wake-word listening could not start."
                }
            } catch (exception: Exception) {
                releaseResources()

                mainHandler.post {
                    if (!closed && generation == session) {
                        listening = false

                        onError(
                            "Wake-word listener failed: " +
                                    (exception.message ?: "Unknown error")
                        )
                    }
                }
            }
        }
    }

    private fun createListener(
        session: Int
    ): RecognitionListener {
        return object : RecognitionListener {

            override fun onPartialResult(hypothesis: String?) {
                // Wait for completed speech to reduce premature triggers.
            }

            override fun onResult(hypothesis: String?) {
                handleResult(session, hypothesis)
            }

            override fun onFinalResult(hypothesis: String?) {
                handleResult(session, hypothesis)
            }

            override fun onError(exception: Exception?) {
                if (
                    !closed &&
                    generation == session &&
                    listening
                ) {
                    stop {
                        onError(
                            "Wake-word microphone error: " +
                                    (exception?.message ?: "Unknown error")
                        )
                    }
                }
            }

            override fun onTimeout() {
                if (
                    !closed &&
                    generation == session &&
                    listening
                ) {
                    stop {
                        onError("Wake-word listening timed out.")
                    }
                }
            }
        }
    }

    private fun handleResult(
        session: Int,
        hypothesis: String?
    ) {
        if (
            closed ||
            !listening ||
            generation != session ||
            hypothesis == null
        ) {
            return
        }

        val text = try {
            JSONObject(hypothesis)
                .optString("text")
                .replace(Regex("\\s+"), " ")
                .trim()
        } catch (_: Exception) {
            return
        }

        if (text.isBlank()) return

        onTranscript(text)

        // Unknown speech must never activate the command recognizer.
        if (text == "मदद") {
            stop {
                onWakeWord()
            }
        }
    }

    // Callback runs after recording stops and the microphone is released.
    fun stop(onStopped: () -> Unit = {}) {
        if (closed) return

        generation += 1
        val session = generation
        listening = false

        worker.execute {
            releaseResources()

            mainHandler.post {
                if (!closed && generation == session) {
                    onStopped()
                }
            }
        }
    }

    // The borrowed model must remain open until this callback runs.
    fun close(onClosed: () -> Unit = {}) {
        if (closed) return

        closed = true
        listening = false
        generation += 1

        worker.execute {
            releaseResources()

            mainHandler.post {
                onClosed()
            }
        }

        worker.shutdown()
    }

    private fun releaseResources() {
        val service = speechService
        speechService = null

        try {
            service?.cancel()
        } finally {
            try {
                service?.shutdown()
            } finally {
                recognizer?.close()
                recognizer = null
            }
        }
    }
}