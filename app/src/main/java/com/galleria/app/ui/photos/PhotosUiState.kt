package com.galleria.app.ui.photos

import androidx.compose.runtime.Immutable
import com.galleria.app.data.model.MediaItem

/**
 * UI State for the Photos Grid screen.
 */
@Immutable
sealed interface PhotosUiState {
    @Immutable
    object PermissionRequired : PhotosUiState
    @Immutable
    object Loading : PhotosUiState
    @Immutable
    data class Success(val photos: List<MediaItem>) : PhotosUiState
    @Immutable
    object Empty : PhotosUiState
    @Immutable
    data class Error(val message: String) : PhotosUiState
}
