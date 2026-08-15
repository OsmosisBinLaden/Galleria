package com.galleria.app.ui.photos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.galleria.app.data.model.MediaItem
import com.galleria.app.data.repository.MediaStoreRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel managing permission state, paged photo stream, and live MediaStore updates.
 */
@OptIn(FlowPreview::class)
class PhotosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaStoreRepository(application.applicationContext)

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    val photosPagingData: Flow<PagingData<MediaItem>> = repository
        .getPhotosPagingData()
        .cachedIn(viewModelScope)

    init {
        // Observe MediaStore changes with a 500ms debounce to coalesce rapid/multiple notifications
        repository.observeMediaStoreChanges()
            .debounce(500L)
            .onEach {
                if (_hasPermission.value) {
                    repository.invalidatePagingSource()
                }
            }
            .launchIn(viewModelScope)
    }

    fun onPermissionGranted() {
        val wasPermissionGranted = _hasPermission.value
        _hasPermission.value = true
        if (!wasPermissionGranted) {
            repository.invalidatePagingSource()
        }
    }

    fun onPermissionDenied() {
        _hasPermission.value = false
    }
}
