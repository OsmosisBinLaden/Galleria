package com.galleria.app.ui.navigation

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.paging.PagingData
import com.galleria.app.data.model.MediaItem
import com.galleria.app.data.model.MediaStoreFolderKey
import com.galleria.app.ui.folders.FolderDetailScreen
import com.galleria.app.ui.folders.FolderDetailViewModel
import com.galleria.app.ui.folders.FoldersGridScreen
import com.galleria.app.ui.folders.FoldersViewModel
import com.galleria.app.ui.photos.PhotosGridScreen
import kotlinx.coroutines.flow.Flow

sealed class GalleriaDestination(val route: String, val title: String) {
    data object Photos : GalleriaDestination("photos", "Photos")
    data object Albums : GalleriaDestination("albums", "Albums")
    data object Folders : GalleriaDestination("folders", "Folders")
}

sealed interface GalleriaDetailDestination {
    data class FolderDetail(val folderKey: MediaStoreFolderKey) : GalleriaDetailDestination
}

@Composable
fun MainScreen(
    hasPermission: Boolean,
    photosPagingData: Flow<PagingData<MediaItem>>,
    onRequestPermission: () -> Unit,
    foldersViewModel: FoldersViewModel? = null
) {
    var selectedDestination by remember { mutableStateOf<GalleriaDestination>(GalleriaDestination.Photos) }
    var selectedDetail by remember { mutableStateOf<GalleriaDetailDestination?>(null) }

    // Intercept system back button when viewing a detail screen
    BackHandler(enabled = selectedDetail != null) {
        selectedDetail = null
    }

    val context = LocalContext.current.applicationContext as Application
    val actualFoldersViewModel = foldersViewModel ?: remember(context) { FoldersViewModel(context) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // Hide bottom NavigationBar when viewing detail screens
            if (selectedDetail == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedDestination == GalleriaDestination.Photos,
                        onClick = { selectedDestination = GalleriaDestination.Photos },
                        label = { Text("Photos") },
                        icon = { Text("📷") }
                    )
                    NavigationBarItem(
                        selected = selectedDestination == GalleriaDestination.Albums,
                        onClick = { selectedDestination = GalleriaDestination.Albums },
                        label = { Text("Albums") },
                        icon = { Text("🖼️") }
                    )
                    NavigationBarItem(
                        selected = selectedDestination == GalleriaDestination.Folders,
                        onClick = { selectedDestination = GalleriaDestination.Folders },
                        label = { Text("Folders") },
                        icon = { Text("📁") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val currentDetail = selectedDetail
            if (currentDetail != null) {
                when (currentDetail) {
                    is GalleriaDetailDestination.FolderDetail -> {
                        val folderDetailViewModel = remember(context, currentDetail.folderKey) {
                            FolderDetailViewModel(context, currentDetail.folderKey)
                        }
                        val folderInfo by folderDetailViewModel.folderInfo.collectAsState()

                        FolderDetailScreen(
                            folderInfo = folderInfo,
                            photosPagingData = folderDetailViewModel.photosPagingData,
                            onBackClick = { selectedDetail = null }
                        )
                    }
                }
            } else {
                when (selectedDestination) {
                    GalleriaDestination.Photos -> {
                        PhotosGridScreen(
                            hasPermission = hasPermission,
                            photosPagingData = photosPagingData,
                            onRequestPermission = onRequestPermission
                        )
                    }
                    GalleriaDestination.Albums -> {
                        PlaceholderTabScreen(title = "Albums")
                    }
                    GalleriaDestination.Folders -> {
                        val foldersUiState by actualFoldersViewModel.uiState.collectAsState()

                        FoldersGridScreen(
                            uiState = foldersUiState,
                            onFolderClick = { folder ->
                                selectedDetail = GalleriaDetailDestination.FolderDetail(folder.key)
                            },
                            onRequestPermission = onRequestPermission,
                            onRetry = { actualFoldersViewModel.retry() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderTabScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
