package com.svgs.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.svgs.app.core.battery.BatteryStatusManager
import com.svgs.app.core.camera.CameraPreview
import com.svgs.app.core.command.Command
import com.svgs.app.core.command.CommandRouter
import com.svgs.app.core.command.VoiceCommandParser
import com.svgs.app.core.detection.DetectionAnnouncementController
import com.svgs.app.core.detection.DetectionResult
import com.svgs.app.core.mode.Mode
import com.svgs.app.core.mode.ModeManager
import com.svgs.app.core.speech.SpeechRecognitionManager
import com.svgs.app.core.speech.TextToSpeechManager
import com.svgs.app.core.speech.WakeWordManager
import com.svgs.app.core.speech.WakeWordModelLoader
import com.svgs.app.ui.theme.SmartVisualGuidanceSystemTheme

class MainActivity : ComponentActivity() {

    private lateinit var textToSpeechManager: TextToSpeechManager
    private lateinit var batteryStatusManager: BatteryStatusManager
    private lateinit var wakeWordModelLoader: WakeWordModelLoader

    private val modeManager = ModeManager()
    private val commandRouter = CommandRouter(modeManager)
    private val voiceCommandParser = VoiceCommandParser()

    private val detectionAnnouncementController =
        DetectionAnnouncementController()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognitionManager: SpeechRecognitionManager? = null
    private var wakeWordManager: WakeWordManager? = null

    private var appInForeground = false
    private var operationVersion = 0

    private var cameraPermissionGranted by mutableStateOf(false)
    private var batteryMessage by mutableStateOf<String?>(null)
    private var voiceBusy by mutableStateOf(false)
    private var recognitionLanguageTag by mutableStateOf("en-US")

    // User intent, independent of the microphone's current owner.
    private var handsFreeEnabled by mutableStateOf(false)
    private var wakeWordReady by mutableStateOf(false)
    private var wakeListening by mutableStateOf(false)
    private var wakeStopping by mutableStateOf(false)

    private var voiceMessage by mutableStateOf(
        "Tap Voice Command, then speak."
    )

    private val resumeWakeRunnable = Runnable {
        resumeWakeIfAllowed()
    }

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            cameraPermissionGranted = granted
        }

    private val microphonePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted && appInForeground) {
                startVoiceRecognition()
            } else if (!granted) {
                voiceBusy = false
                voiceMessage = "Microphone permission was denied."

                speakFeedback(
                    "Microphone permission was denied. " +
                            "Voice commands need microphone access.",
                    "مائیکروفون کی اجازت نہیں ملی۔ " +
                            "آواز کے ذریعے حکم دینے کے لیے اجازت ضروری ہے۔"
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        restoreScreenState(savedInstanceState)

        textToSpeechManager = TextToSpeechManager(this)
        textToSpeechManager.onIdle = {
            scheduleWakeResume()
        }

        batteryStatusManager = BatteryStatusManager(this)
        wakeWordModelLoader = WakeWordModelLoader(this)

        loadWakeWordModel()
        requestCameraPermission()

        setContent {
            SmartVisualGuidanceSystemTheme {
                DebugScreen(
                    modeManager = modeManager,
                    batteryMessage = batteryMessage,
                    voiceMessage = voiceMessage,
                    voiceBusy = voiceBusy,
                    recognitionLanguageTag = recognitionLanguageTag,
                    cameraPermissionGranted = cameraPermissionGranted,
                    handsFreeEnabled = handsFreeEnabled,
                    wakeWordReady = wakeWordReady,
                    wakeListening = wakeListening,
                    wakeStopping = wakeStopping,
                    onRecognitionLanguageChanged = {
                        changeRecognitionLanguage(it)
                    },
                    onDetectionResults = {
                        handleDetectionResults(it)
                    },
                    onCommand = {
                        executeCommand(it)
                    },
                    onVoiceCommand = {
                        requestVoiceInput()
                    },
                    onToggleHandsFree = {
                        toggleHandsFree()
                    },
                    onTestSpeech = {
                        afterWakeMicrophoneReleased {
                            textToSpeechManager.speakEnglish(
                                "Welcome to Smart Visual Guidance System. " +
                                        "Speech is working."
                            )
                        }
                    },
                    onTestUrduSpeech = {
                        afterWakeMicrophoneReleased {
                            textToSpeechManager.speakUrdu(
                                "السلام علیکم۔ بصری رہنمائی کی ایپ میں خوش آمدید۔"
                            )
                        }
                    }
                )
            }
        }
    }

    private fun restoreScreenState(state: Bundle?) {
        if (state == null) return

        recognitionLanguageTag =
            if (state.getString("recognitionLanguage") == "ur-PK") {
                "ur-PK"
            } else {
                "en-US"
            }

        batteryMessage = state.getString("batteryMessage")

        // Accept the previous key when upgrading from the earlier code.
        handsFreeEnabled = state.getBoolean(
            "handsFreeEnabled",
            state.getBoolean("wakeRequested", false)
        )

        val restoredMode = runCatching {
            Mode.valueOf(
                state.getString("currentMode") ?: Mode.IDLE.name
            )
        }.getOrDefault(Mode.IDLE)

        modeManager.setMode(restoredMode)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            "recognitionLanguage",
            recognitionLanguageTag
        )
        outState.putString(
            "currentMode",
            modeManager.currentMode.value.name
        )
        outState.putString("batteryMessage", batteryMessage)
        outState.putBoolean("handsFreeEnabled", handsFreeEnabled)

        super.onSaveInstanceState(outState)
    }

    private fun loadWakeWordModel() {
        voiceMessage = "Loading wake-word model..."

        wakeWordModelLoader.load(
            onReady = { model ->
                wakeWordManager = WakeWordManager(
                    model = model,
                    onWakeWord = {
                        wakeListening = false
                        wakeStopping = false

                        if (appInForeground && handsFreeEnabled) {
                            startVoiceRecognition()
                        }
                    },
                    onTranscript = {
                        voiceMessage = "Wake listener heard: $it"
                    },
                    onError = {
                        // Do not repeatedly restart a failing microphone.
                        handsFreeEnabled = false
                        wakeListening = false
                        wakeStopping = false
                        mainHandler.removeCallbacks(resumeWakeRunnable)
                        voiceMessage = it
                    }
                )

                wakeWordReady = true

                if (!voiceBusy) {
                    voiceMessage = "Wake-word model ready."
                }

                scheduleWakeResume()
            },
            onError = {
                handsFreeEnabled = false
                wakeWordReady = false
                voiceMessage = it
            }
        )
    }

    private fun scheduleWakeResume() {
        mainHandler.removeCallbacks(resumeWakeRunnable)

        if (!appInForeground || !handsFreeEnabled) return

        // A short gap after actual TTS completion avoids its audio tail.
        mainHandler.postDelayed(resumeWakeRunnable, 300L)
    }

    private fun resumeWakeIfAllowed() {
        if (
            !appInForeground ||
            !handsFreeEnabled ||
            !wakeWordReady ||
            wakeListening ||
            wakeStopping ||
            voiceBusy ||
            textToSpeechManager.isBusy
        ) {
            return
        }

        if (!hasMicrophonePermission()) {
            handsFreeEnabled = false
            voiceMessage =
                "Use Voice Command first and grant microphone permission."
            return
        }

        val manager = wakeWordManager ?: return

        wakeListening = true
        voiceMessage =
            if (recognitionLanguageTag == "ur-PK") {
                "مدد کہیں، پھر بیپ کے بعد اپنا حکم بولیں۔"
            } else {
                "Say Madad, then speak your command after the beep."
            }

        manager.start()
    }

    private fun toggleHandsFree() {
        if (!appInForeground || wakeStopping) return

        if (handsFreeEnabled) {
            handsFreeEnabled = false
            mainHandler.removeCallbacks(resumeWakeRunnable)

            afterWakeMicrophoneReleased {
                if (!voiceBusy) {
                    voiceMessage = "Hands-free mode disabled."
                }
            }

            return
        }

        if (voiceBusy || !wakeWordReady) return

        if (!hasMicrophonePermission()) {
            voiceMessage =
                "Use Voice Command first and grant microphone permission."
            return
        }

        handsFreeEnabled = true
        textToSpeechManager.stop()
        scheduleWakeResume()
    }

    private fun afterWakeMicrophoneReleased(action: () -> Unit) {
        if (!appInForeground || wakeStopping) return

        mainHandler.removeCallbacks(resumeWakeRunnable)

        if (!wakeListening) {
            action()
            scheduleWakeResume()
            return
        }

        val manager = wakeWordManager ?: return
        val version = operationVersion

        wakeStopping = true

        manager.stop {
            wakeListening = false
            wakeStopping = false

            if (
                appInForeground &&
                operationVersion == version
            ) {
                action()
                scheduleWakeResume()
            }
        }
    }

    private fun changeRecognitionLanguage(languageTag: String) {
        if (voiceBusy || wakeStopping) return

        afterWakeMicrophoneReleased {
            recognitionLanguageTag = languageTag

            voiceMessage =
                if (languageTag == "ur-PK") {
                    "اردو منتخب ہے۔"
                } else {
                    "English selected."
                }

            speakFeedback(
                "English selected.",
                "اردو منتخب ہو گئی ہے۔"
            )
        }
    }

    private fun speakFeedback(english: String, urdu: String) {
        if (recognitionLanguageTag == "ur-PK") {
            textToSpeechManager.speakUrdu(urdu)
        } else {
            textToSpeechManager.speakEnglish(english)
        }
    }

    private fun handleDetectionResults(results: List<DetectionResult>) {
        if (
            !appInForeground ||
            voiceBusy ||
            wakeStopping ||
            modeManager.currentMode.value != Mode.OBJECT_DETECTION ||
            textToSpeechManager.isBusy
        ) {
            return
        }

        val announcement =
            detectionAnnouncementController.nextAnnouncement(results)
                ?: return

        afterWakeMicrophoneReleased {
            if (modeManager.currentMode.value == Mode.OBJECT_DETECTION) {
                textToSpeechManager.speakEnglish(announcement)
            }
        }
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        cameraPermissionGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        if (!cameraPermissionGranted) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun requestVoiceInput() {
        if (!appInForeground || voiceBusy || wakeStopping) return

        if (hasMicrophonePermission()) {
            afterWakeMicrophoneReleased {
                startVoiceRecognition()
            }
        } else {
            microphonePermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    private fun startVoiceRecognition() {
        if (
            !appInForeground ||
            voiceBusy ||
            wakeListening ||
            wakeStopping
        ) {
            return
        }

        mainHandler.removeCallbacks(resumeWakeRunnable)

        // Set before stopping TTS so its idle callback cannot rearm.
        voiceBusy = true
        textToSpeechManager.stop()

        if (speechRecognitionManager == null) {
            speechRecognitionManager = SpeechRecognitionManager(
                context = this,
                onRecognizedText = { recognizedText ->
                    voiceBusy = false

                    val command = voiceCommandParser.parse(recognizedText)

                    voiceMessage =
                        if (recognitionLanguageTag == "ur-PK") {
                            "سنا: \"$recognizedText\""
                        } else {
                            "Heard: \"$recognizedText\""
                        }

                    if (command == null) {
                        voiceMessage +=
                            if (recognitionLanguageTag == "ur-PK") {
                                "\nحکم سمجھ نہیں آیا۔"
                            } else {
                                "\nCommand not recognized."
                            }

                        speakFeedback(
                            "Command not recognized. Please try again.",
                            "حکم سمجھ نہیں آیا۔ دوبارہ کوشش کریں۔"
                        )

                        scheduleWakeResume()
                    } else {
                        executeCommand(command)
                    }
                },
                onErrorMessage = {
                    voiceBusy = false
                    voiceMessage = it
                    textToSpeechManager.speakEnglish(it)
                    scheduleWakeResume()
                },
                onListeningChanged = { listening ->
                    if (listening) {
                        voiceMessage =
                            if (recognitionLanguageTag == "ur-PK") {
                                "سن رہا ہے۔ اپنا حکم بولیں۔"
                            } else {
                                "Listening. Speak your command."
                            }
                    } else if (voiceBusy) {
                        voiceMessage =
                            if (recognitionLanguageTag == "ur-PK") {
                                "آپ کے حکم پر کارروائی ہو رہی ہے۔"
                            } else {
                                "Processing your voice command."
                            }
                    }
                }
            )
        }

        voiceMessage =
            if (recognitionLanguageTag == "ur-PK") {
                "مائیکروفون شروع ہو رہا ہے۔"
            } else {
                "Starting microphone."
            }

        speechRecognitionManager?.startListening(
            languageTag = recognitionLanguageTag
        )
    }

    private fun executeCommand(command: Command) {
        if (!appInForeground || voiceBusy || wakeStopping) return

        if (command == Command.Stop) {
            handsFreeEnabled = false
            mainHandler.removeCallbacks(resumeWakeRunnable)
        }

        afterWakeMicrophoneReleased {
            detectionAnnouncementController.reset()
            commandRouter.route(command)
            batteryMessage = null

            when (command) {
                Command.StartObjectDetection -> speakFeedback(
                    "Object detection mode selected.",
                    "چیزوں کی شناخت کا موڈ منتخب ہو گیا ہے۔"
                )

                Command.ReadText -> speakFeedback(
                    "Text reading mode selected.",
                    "متن پڑھنے کا موڈ منتخب ہو گیا ہے۔"
                )

                Command.ScanCurrency -> speakFeedback(
                    "Currency scanning mode selected.",
                    "کرنسی کی شناخت کا موڈ منتخب ہو گیا ہے۔"
                )

                Command.Stop -> {
                    textToSpeechManager.stop()

                    speakFeedback(
                        "Stopped. Hands-free mode disabled.",
                        "روک دیا گیا ہے۔ ہینڈز فری موڈ بند ہے۔"
                    )
                }

                Command.CheckBattery -> announceBattery()
            }
        }
    }

    private fun announceBattery() {
        val status = batteryStatusManager.getBatteryStatus()

        val englishMessage =
            if (status.percentage >= 0) {
                "Battery: ${status.percentage}%\n" +
                        "Charging status: ${status.chargingStatus}"
            } else {
                "Battery percentage unavailable.\n" +
                        "Charging status: ${status.chargingStatus}"
            }

        val urduChargingStatus = when (status.chargingStatus) {
            "Charging" -> "بیٹری چارج ہو رہی ہے۔"
            "Fully charged" -> "بیٹری مکمل چارج ہے۔"
            "Discharging" -> "بیٹری استعمال ہو رہی ہے۔"
            "Not charging" -> "بیٹری چارج نہیں ہو رہی۔"
            else -> "چارجنگ کی حالت معلوم نہیں ہے۔"
        }

        val urduMessage =
            if (status.percentage >= 0) {
                "بیٹری ${status.percentage} فیصد ہے۔\n" +
                        urduChargingStatus
            } else {
                "بیٹری کی شرح معلوم نہیں ہے۔\n" +
                        urduChargingStatus
            }

        batteryMessage =
            if (recognitionLanguageTag == "ur-PK") {
                urduMessage
            } else {
                englishMessage
            }

        speakFeedback(
            englishMessage.replace("\n", ". "),
            urduMessage.replace("\n", " ")
        )
    }

    override fun onStart() {
        super.onStart()

        appInForeground = true
        detectionAnnouncementController.reset()

        cameraPermissionGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        scheduleWakeResume()
    }

    override fun onStop() {
        appInForeground = false
        operationVersion += 1
        mainHandler.removeCallbacks(resumeWakeRunnable)
        detectionAnnouncementController.reset()

        // Preserve enabled state only when recreating for rotation.
        if (!isChangingConfigurations) {
            handsFreeEnabled = false
        }

        val manager = wakeWordManager

        if (manager != null) {
            wakeStopping = true

            manager.stop {
                wakeListening = false
                wakeStopping = false
                scheduleWakeResume()
            }
        }

        wakeListening = false

        speechRecognitionManager?.destroy()
        speechRecognitionManager = null
        voiceBusy = false

        voiceMessage =
            if (recognitionLanguageTag == "ur-PK") {
                "وائس کمانڈ دبائیں اور بولیں۔"
            } else {
                "Tap Voice Command, then speak."
            }

        if (::textToSpeechManager.isInitialized) {
            textToSpeechManager.stop()
        }

        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)

        speechRecognitionManager?.destroy()
        speechRecognitionManager = null

        val manager = wakeWordManager
        wakeWordManager = null
        wakeWordReady = false

        if (::wakeWordModelLoader.isInitialized) {
            val loader = wakeWordModelLoader

            if (manager != null) {
                manager.close {
                    loader.close()
                }
            } else {
                loader.close()
            }
        }

        if (::textToSpeechManager.isInitialized) {
            textToSpeechManager.onIdle = null
            textToSpeechManager.shutdown()
        }

        super.onDestroy()
    }
}

@Composable
fun DebugScreen(
    modeManager: ModeManager,
    batteryMessage: String?,
    voiceMessage: String,
    voiceBusy: Boolean,
    recognitionLanguageTag: String,
    cameraPermissionGranted: Boolean,
    handsFreeEnabled: Boolean,
    wakeWordReady: Boolean,
    wakeListening: Boolean,
    wakeStopping: Boolean,
    onRecognitionLanguageChanged: (String) -> Unit,
    onDetectionResults: (List<DetectionResult>) -> Unit,
    onCommand: (Command) -> Unit,
    onVoiceCommand: () -> Unit,
    onToggleHandsFree: () -> Unit,
    onTestSpeech: () -> Unit,
    onTestUrduSpeech: () -> Unit
) {
    val currentMode by modeManager.currentMode.collectAsState()
    val controlsEnabled = !voiceBusy && !wakeStopping

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (cameraPermissionGranted) {
            CameraPreview(
                modifier = Modifier.fillMaxWidth().height(150.dp),
                // Analysis continues while the wake listener is active.
                analysisEnabled =
                    currentMode == Mode.OBJECT_DETECTION,
                onDetectionResults = onDetectionResults
            )
        } else {
            Text(
                "Camera permission is required for the preview.",
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    "Smart Visual Guidance System",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = batteryMessage?.let {
                        "Current Status:\n$it"
                    } ?: "Current Mode: $currentMode",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                val languageName =
                    if (recognitionLanguageTag == "ur-PK") {
                        "Urdu"
                    } else {
                        "English"
                    }

                Text(
                    "Voice input language: $languageName",
                    style = MaterialTheme.typography.titleMedium
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            onRecognitionLanguageChanged("en-US")
                        },
                        enabled = controlsEnabled &&
                                recognitionLanguageTag != "en-US",
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    ) {
                        Text("English")
                    }

                    Button(
                        onClick = {
                            onRecognitionLanguageChanged("ur-PK")
                        },
                        enabled = controlsEnabled &&
                                recognitionLanguageTag != "ur-PK",
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    ) {
                        Text("اردو")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Text(
                    "Wake word: Madad",
                    style = MaterialTheme.typography.titleMedium
                )

                val wakeStatus = when {
                    !handsFreeEnabled -> "Hands-free: off"
                    voiceBusy -> "Hands-free: processing command"
                    wakeStopping -> "Hands-free: releasing microphone"
                    wakeListening -> "Hands-free: listening for Madad"
                    else -> "Hands-free: waiting for speech to finish"
                }

                Text(
                    wakeStatus,
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(
                    onClick = onToggleHandsFree,
                    enabled = wakeWordReady &&
                            !wakeStopping &&
                            (!voiceBusy || handsFreeEnabled),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (handsFreeEnabled) {
                            "Disable Hands-Free"
                        } else {
                            "Enable Hands-Free"
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Button(
                    onClick = onVoiceCommand,
                    enabled = controlsEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (voiceBusy) {
                            "Voice Command in Progress"
                        } else {
                            "Voice Command"
                        }
                    )
                }

                Text(
                    voiceMessage,
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                DebugActionButton(
                    "Start Object Detection",
                    controlsEnabled
                ) {
                    onCommand(Command.StartObjectDetection)
                }
            }

            item {
                DebugActionButton("Read Text", controlsEnabled) {
                    onCommand(Command.ReadText)
                }
            }

            item {
                DebugActionButton("Scan Currency", controlsEnabled) {
                    onCommand(Command.ScanCurrency)
                }
            }

            item {
                DebugActionButton("Stop", controlsEnabled) {
                    onCommand(Command.Stop)
                }
            }

            item {
                DebugActionButton("Check Battery", controlsEnabled) {
                    onCommand(Command.CheckBattery)
                }
            }

            item {
                DebugActionButton(
                    "Test English Speech",
                    controlsEnabled,
                    onTestSpeech
                )
            }

            item {
                DebugActionButton(
                    "Test Urdu Speech",
                    controlsEnabled,
                    onTestUrduSpeech
                )
            }
        }
    }
}

@Composable
private fun DebugActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }

    Spacer(modifier = Modifier.height(12.dp))
}