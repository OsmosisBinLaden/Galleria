package com.galleria.app.ui.photos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.galleria.app.data.repository.MediaStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing UI state and photo loading from MediaStore.
 * Inherits from AndroidViewModel to easily access Application context without complex DI.
 */
class PhotosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaStoreRepository(application.applicationContext)

    private val _uiState = MutableStateFlow<PhotosUiState>(PhotosUiState.Loading)
    val uiState: StateFlow<PhotosUiState> = _uiState.asStateFlow()

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.value = PhotosUiState.Loading
            try {
                val photos = repository.getPhotos()
                if (photos.isEmpty()) {
                    _uiState.value = PhotosUiState.Empty
                } else {
                    _uiState.value = PhotosUiState.Success(photos)
                }
            } catch (e: Exception) {
                _uiState.value = PhotosUiState.Error(e.localizedMessage ?: "Failed to load photos")
            }
        }
    }

    fun onPermissionGranted() {
        loadPhotos()
    }

    fun onPermissionDenied() {
        _uiState.value = PhotosUiState.PermissionRequired
    }
}
