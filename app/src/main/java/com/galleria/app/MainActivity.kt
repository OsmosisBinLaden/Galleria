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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.galleria.app.ui.folders.FoldersViewModel
import com.galleria.app.ui.navigation.MainScreen
import com.galleria.app.ui.photos.PhotosViewModel
import com.galleria.app.ui.theme.GalleriaTheme

class MainActivity : ComponentActivity() {

    private val photosViewModel: PhotosViewModel by viewModels()
    private val foldersViewModel: FoldersViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GalleriaTheme {
                val hasPermission by photosViewModel.hasPermission.collectAsState()

                val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        photosViewModel.onPermissionGranted()
                        foldersViewModel.onPermissionGranted()
                    } else {
                        photosViewModel.onPermissionDenied()
                        foldersViewModel.onPermissionDenied()
                    }
                }

                LaunchedEffect(Unit) {
                    val isGranted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        requiredPermission
                    ) == PackageManager.PERMISSION_GRANTED

                    if (isGranted) {
                        photosViewModel.onPermissionGranted()
                        foldersViewModel.onPermissionGranted()
                    } else {
                        photosViewModel.onPermissionDenied()
                        foldersViewModel.onPermissionDenied()
                    }
                }

                MainScreen(
                    hasPermission = hasPermission,
                    photosPagingData = photosViewModel.photosPagingData,
                    onRequestPermission = {
                        permissionLauncher.launch(requiredPermission)
                    },
                    foldersViewModel = foldersViewModel
                )
            }
        }
    }
}