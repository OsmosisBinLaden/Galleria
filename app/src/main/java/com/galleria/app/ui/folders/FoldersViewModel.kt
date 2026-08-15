package com.galleria.app.ui.folders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galleria.app.data.model.FolderItem
import com.galleria.app.data.repository.MediaStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for physical folder discovery UI state.
 */
class FoldersViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaStoreRepository(application.applicationContext)

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _uiState = MutableStateFlow<FoldersUiState>(FoldersUiState.Loading)
    val uiState: StateFlow<FoldersUiState> = _uiState.asStateFlow()

    fun onPermissionGranted() {
        _hasPermission.value = true
        loadFolders()
    }

    fun onPermissionDenied() {
        _hasPermission.value = false
        _uiState.value = FoldersUiState.PermissionRequired
    }

    fun retry() {
        if (_hasPermission.value) {
            loadFolders()
        }
    }

    private fun loadFolders() {
        viewModelScope.launch {
            _uiState.value = FoldersUiState.Loading
            repository.observeFolders()
                .catch { e ->
                    _uiState.value = FoldersUiState.Error(e.localizedMessage ?: "Failed to load folders")
                }
                .collect { rawFolders ->
                    val sortedFolders = sortFolders(rawFolders)
                    _uiState.value = FoldersUiState.Success(sortedFolders)
                }
        }
    }

    /**
     * Sorts discovered folders: Camera first, Screenshots second, Downloads third,
     * remaining folders ordered alphabetically by displayName.
     */
    private fun sortFolders(folders: List<FolderItem>): List<FolderItem> {
        val priorityMap = mapOf(
            "Camera" to 0,
            "Screenshots" to 1,
            "Downloads" to 2
        )

        return folders.sortedWith(
            compareBy<FolderItem> { item ->
                priorityMap[item.displayName] ?: 99
            }.thenBy { item ->
                item.displayName.lowercase()
            }
        )
    }
}
