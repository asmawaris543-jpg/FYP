package com.svgs.app

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.svgs.app.core.camera.CameraPreview
import com.svgs.app.core.command.Command
import com.svgs.app.core.command.CommandRouter
import com.svgs.app.core.mode.ModeManager
import com.svgs.app.ui.theme.SmartVisualGuidanceSystemTheme

class MainActivity : ComponentActivity() {

    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (isGranted) {
                // Camera permission granted.
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestCameraPermission()

        setContent {
            SmartVisualGuidanceSystemTheme {

                val modeManager = remember {
                    ModeManager()
                }

                val commandRouter = remember {
                    CommandRouter(modeManager)
                }

                DebugScreen(
                    modeManager = modeManager,
                    commandRouter = commandRouter
                )
            }
        }
    }

    private fun requestCameraPermission() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }
}

@androidx.compose.runtime.Composable
fun DebugScreen(
    modeManager: ModeManager,
    commandRouter: CommandRouter
) {

    val currentMode by modeManager.currentMode.collectAsState()

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        item {
            Text(
                text = "Smart Visual Guidance System",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Current Mode: $currentMode",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Button(
                onClick = {
                    commandRouter.route(Command.StartObjectDetection)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Object Detection")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Button(
                onClick = {
                    commandRouter.route(Command.ReadText)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Read Text")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Button(
                onClick = {
                    commandRouter.route(Command.ScanCurrency)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan Currency")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Button(
                onClick = {
                    commandRouter.route(Command.Stop)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Button(
                onClick = {
                    commandRouter.route(Command.CheckBattery)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Check Battery")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            CameraPreview(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            )
        }
    }
}