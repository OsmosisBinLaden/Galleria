package com.galleria.app.ui.folders

import androidx.compose.runtime.Immutable
import com.galleria.app.data.model.FolderItem

/**
 * UI State model for FoldersGridScreen.
 */
@Immutable
sealed interface FoldersUiState {
    data object Loading : FoldersUiState
    data object PermissionRequired : FoldersUiState
    data class Success(val folders: List<FolderItem>) : FoldersUiState
    data class Error(val message: String) : FoldersUiState
}
