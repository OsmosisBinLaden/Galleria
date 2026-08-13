package com.galleria.app.ui.photos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.galleria.app.data.model.MediaItem
import com.galleria.app.data.repository.MediaStoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel managing permission state and paged photo stream from MediaStore.
 */
class PhotosViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaStoreRepository(application.applicationContext)

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    val photosPagingData: Flow<PagingData<MediaItem>> = repository
        .getPhotosPagingData()
        .cachedIn(viewModelScope)

    fun onPermissionGranted() {
        _hasPermission.value = true
    }

    fun onPermissionDenied() {
        _hasPermission.value = false
    }
}
