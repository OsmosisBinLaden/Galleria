package com.galleria.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.paging.PagingData
import com.galleria.app.data.model.MediaItem
import com.galleria.app.ui.photos.PhotosGridScreen
import kotlinx.coroutines.flow.Flow

sealed class GalleriaDestination(val route: String, val title: String) {
    data object Photos : GalleriaDestination("photos", "Photos")
    data object Albums : GalleriaDestination("albums", "Albums")
    data object Folders : GalleriaDestination("folders", "Folders")
}

@Composable
fun MainScreen(
    hasPermission: Boolean,
    photosPagingData: Flow<PagingData<MediaItem>>,
    onRequestPermission: () -> Unit
) {
    var selectedDestination by remember { mutableStateOf<GalleriaDestination>(GalleriaDestination.Photos) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                    PlaceholderTabScreen(title = "Folders")
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
