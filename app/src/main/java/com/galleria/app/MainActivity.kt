package com.galleria.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.galleria.app.ui.photos.PhotosGridScreen
import com.galleria.app.ui.photos.PhotosViewModel
import com.galleria.app.ui.theme.GalleriaTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PhotosViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GalleriaTheme {
                val uiState by viewModel.uiState.collectAsState()

                val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        viewModel.onPermissionGranted()
                    } else {
                        viewModel.onPermissionDenied()
                    }
                }

                LaunchedEffect(Unit) {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        requiredPermission
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        viewModel.onPermissionGranted()
                    } else {
                        viewModel.onPermissionDenied()
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PhotosGridScreen(
                        uiState = uiState,
                        onRequestPermission = {
                            permissionLauncher.launch(requiredPermission)
                        },
                        onRetry = {
                            viewModel.loadPhotos()
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}