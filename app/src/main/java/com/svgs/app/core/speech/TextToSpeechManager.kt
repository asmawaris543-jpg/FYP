package com.svgs.app.core.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import java.util.Locale

class TextToSpeechManager(
    context: Context
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var isShutdown = false
    private var utteranceNumber = 0L

    @Volatile
    private var activeUtteranceId: String? = null

    val isBusy: Boolean
        get() = activeUtteranceId != null

    // Assign from the main thread.
    // Called when speech finishes, stops, or fails.
    var onIdle: (() -> Unit)? = null

    init {
        textToSpeech = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        mainHandler.post {
            if (!isShutdown) {
                val engine = textToSpeech

                if (
                    status == TextToSpeech.SUCCESS &&
                    engine != null
                ) {
                    engine.setOnUtteranceProgressListener(
                        createProgressListener()
                    )

                    isInitialized = true

                    Log.d(
                        "TTSManager",
                        "Initialized. Engine: ${engine.defaultEngine}"
                    )
                } else {
                    report("Text-to-speech initialization failed.")
                }
            }
        }
    }

    private fun createProgressListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {

            override fun onStart(utteranceId: String?) {
                Log.d(
                    "TTSManager",
                    "Speech started: $utteranceId"
                )
            }

            override fun onDone(utteranceId: String?) {
                finishUtterance(utteranceId)
            }

            override fun onStop(
                utteranceId: String?,
                interrupted: Boolean
            ) {
                finishUtterance(utteranceId)
            }

            @Deprecated("Required legacy callback")
            override fun onError(utteranceId: String?) {
                finishUtterance(
                    utteranceId,
                    "Speech synthesis failed."
                )
            }

            override fun onError(
                utteranceId: String?,
                errorCode: Int
            ) {
                finishUtterance(
                    utteranceId,
                    "Speech synthesis failed. TTS error: $errorCode."
                )
            }
        }
    }

    private fun finishUtterance(
        utteranceId: String?,
        errorMessage: String? = null
    ) {
        mainHandler.post {
            if (
                !isShutdown &&
                utteranceId != null &&
                activeUtteranceId == utteranceId
            ) {
                activeUtteranceId = null

                Log.d(
                    "TTSManager",
                    "Speech finished: $utteranceId"
                )

                if (errorMessage != null) {
                    report(errorMessage)
                }

                notifyWhenIdle()
            }
        }
    }

    private fun notifyWhenIdle() {
        // Defer notification so the caller can finish updating its state.
        mainHandler.post {
            if (!isShutdown && activeUtteranceId == null) {
                onIdle?.invoke()
            }
        }
    }

    fun speakEnglish(message: String) {
        speak(message, Locale.US, "English")
    }

    fun speakUrdu(message: String) {
        speak(message, Locale("ur", "PK"), "Urdu")
    }

    // Call speech methods from the main thread.
    private fun speak(
        message: String,
        locale: Locale,
        languageName: String
    ) {
        if (isShutdown) return

        val engine = textToSpeech

        if (!isInitialized || engine == null) {
            report("Text-to-speech is not ready. Please try again.")
            notifyWhenIdle()
            return
        }

        if (message.isBlank()) {
            report("There is no text to speak.")
            notifyWhenIdle()
            return
        }

        if (message.length > TextToSpeech.getMaxSpeechInputLength()) {
            report("The text is too long for one speech request.")
            notifyWhenIdle()
            return
        }

        val languageResult = engine.setLanguage(locale)

        when (languageResult) {
            TextToSpeech.LANG_MISSING_DATA -> {
                report("$languageName speech data is missing.")
                notifyWhenIdle()
                return
            }

            TextToSpeech.LANG_NOT_SUPPORTED -> {
                report(
                    "$languageName is not supported by this TTS engine."
                )
                notifyWhenIdle()
                return
            }
        }

        if (languageResult < 0) {
            report("$languageName could not be selected.")
            notifyWhenIdle()
            return
        }

        utteranceNumber += 1
        val utteranceId = "${languageName}_$utteranceNumber"

        // Reserve before queuing, including the engine's startup delay.
        activeUtteranceId = utteranceId

        val result = engine.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )

        if (result == TextToSpeech.ERROR) {
            if (activeUtteranceId == utteranceId) {
                activeUtteranceId = null
            }

            report("$languageName speech could not be queued.")
            notifyWhenIdle()
        }
    }

    private fun report(message: String) {
        Log.e("TTSManager", message)

        mainHandler.post {
            if (!isShutdown) {
                Toast.makeText(
                    appContext,
                    message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun stop() {
        val wasBusy = activeUtteranceId != null

        activeUtteranceId = null
        textToSpeech?.stop()

        if (wasBusy) {
            notifyWhenIdle()
        }
    }

    fun shutdown() {
        isShutdown = true
        isInitialized = false
        onIdle = null
        activeUtteranceId = null

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        mainHandler.removeCallbacksAndMessages(null)
    }
}