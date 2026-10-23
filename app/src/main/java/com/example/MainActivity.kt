package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.camera.CameraXManager
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var previewView: PreviewView? = null
    private var cameraXManager: CameraXManager? = null

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        viewModel.appendLog("Permissão de câmera: ${if (isGranted) "CONCEDIDA" else "NEGADA"}")
        if (isGranted) {
            setupCameraX()
        } else {
            viewModel.updatePhysicalCameraStatus(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Virtual Camera core pipelines & HTTP streaming server
        viewModel.initialize(this)

        // Setup physical CameraX if permission is granted
        previewView = PreviewView(this)
        cameraXManager = CameraXManager(this) { nv21Frame ->
            viewModel.onPhysicalCameraFrame(nv21Frame)
        }
        viewModel.bindCameraX(cameraXManager!!)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCameraX()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        setContent {
            MyApplicationTheme {
                MainScreen(
                    viewModel = viewModel,
                    previewView = previewView,
                    onRequireCameraPermission = {
                        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }
        }
    }

    private fun setupCameraX() {
        val manager = cameraXManager ?: return
        val preview = previewView ?: return

        manager.startCamera(
            lifecycleOwner = this,
            previewView = preview,
            onStatusChanged = { isAvailable ->
                viewModel.updatePhysicalCameraStatus(isAvailable)
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraXManager?.stop()
    }
}
