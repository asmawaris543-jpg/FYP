package com.svgs.app.core.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechRecognitionManager(
    context: Context,
    private val onRecognizedText: (String) -> Unit,
    private val onErrorMessage: (String) -> Unit,
    private val onListeningChanged: (Boolean) -> Unit
) {

    private val appContext = context.applicationContext

    private var speechRecognizer: SpeechRecognizer? = null
    private var toneGenerator: ToneGenerator? = null

    private var recognitionInProgress = false
    private var readyTonePlayed = false
    private var isDestroyed = false

    private val recognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            if (isDestroyed || !recognitionInProgress) return

            if (!readyTonePlayed) {
                readyTonePlayed = true
                playReadyTone()
            }

            onListeningChanged(true)
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            if (isDestroyed || !recognitionInProgress) return

            stopReadyTone()

            // Recording ended, but recognition is still processing.
            onListeningChanged(false)
        }

        override fun onError(error: Int) {
            if (isDestroyed || !recognitionInProgress) return

            finishRecognition()

            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO ->
                    "Microphone audio could not be recorded."

                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Microphone permission is required."

                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Speech recognition network connection failed."

                SpeechRecognizer.ERROR_NO_MATCH ->
                    "Speech was not understood. Please try again."

                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "Speech recognition is busy. Please try again."

                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "No speech was heard. Please try again."

                SpeechRecognizer.ERROR_CLIENT ->
                    "Speech recognition could not complete."

                SpeechRecognizer.ERROR_SERVER ->
                    "The speech recognition service failed."

                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                    "The selected recognition language is not supported."

                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    "The selected recognition language is currently " +
                            "unavailable. Check your internet connection " +
                            "and speech language settings."

                else ->
                    "Speech recognition failed. Error code: $error."
            }

            onErrorMessage(message)
        }

        override fun onResults(results: Bundle?) {
            if (isDestroyed || !recognitionInProgress) return

            finishRecognition()

            val recognizedText = results
                ?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                ?.firstOrNull()
                ?.trim()

            if (recognizedText.isNullOrBlank()) {
                onErrorMessage("No recognized text was returned.")
            } else {
                onRecognizedText(recognizedText)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // Call from the main thread after microphone permission is granted.
    fun startListening(languageTag: String = "en-US") {
        if (isDestroyed) return

        if (recognitionInProgress) {
            onErrorMessage("Please wait for the current voice command.")
            return
        }

        if (
            appContext.checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            onErrorMessage("Microphone permission is required.")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onErrorMessage(
                "No speech recognition service is available on this device."
            )
            return
        }

        prepareReadyTone()

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                languageTag
            )
            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
            )
            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                1
            )
        }

        try {
            val recognizer = speechRecognizer
                ?: SpeechRecognizer.createSpeechRecognizer(
                    appContext
                ).also {
                    it.setRecognitionListener(recognitionListener)
                    speechRecognizer = it
                }

            readyTonePlayed = false
            recognitionInProgress = true
            recognizer.startListening(intent)
        } catch (exception: RuntimeException) {
            Log.e(
                "SpeechRecognition",
                "Could not start recognition",
                exception
            )

            finishRecognition()
            speechRecognizer?.destroy()
            speechRecognizer = null

            onErrorMessage(
                "Speech recognition could not start. Please try again."
            )
        }
    }

    private fun prepareReadyTone() {
        if (toneGenerator != null) return

        try {
            toneGenerator = ToneGenerator(
                AudioManager.STREAM_MUSIC,
                60
            )
        } catch (exception: RuntimeException) {
            // A cue failure should not prevent voice recognition.
            Log.w(
                "SpeechRecognition",
                "Ready tone is unavailable",
                exception
            )
        }
    }

    private fun playReadyTone() {
        try {
            val started = toneGenerator?.startTone(
                ToneGenerator.TONE_PROP_BEEP,
                120
            )

            if (started == false) {
                Log.w(
                    "SpeechRecognition",
                    "Ready tone could not start"
                )
            }
        } catch (exception: RuntimeException) {
            Log.w(
                "SpeechRecognition",
                "Ready tone playback failed",
                exception
            )
        }
    }

    private fun stopReadyTone() {
        try {
            toneGenerator?.stopTone()
        } catch (exception: RuntimeException) {
            Log.w(
                "SpeechRecognition",
                "Could not stop ready tone",
                exception
            )
        }
    }

    private fun finishRecognition() {
        recognitionInProgress = false
        stopReadyTone()
        onListeningChanged(false)
    }

    // Call from the main thread when the owner leaves the foreground.
    fun destroy() {
        if (isDestroyed) return

        isDestroyed = true
        recognitionInProgress = false

        speechRecognizer?.destroy()
        speechRecognizer = null

        stopReadyTone()
        toneGenerator?.release()
        toneGenerator = null

        onListeningChanged(false)
    }
}