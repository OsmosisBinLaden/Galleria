package com.galleria.app.ui.folders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.galleria.app.data.model.FolderItem
import com.galleria.app.data.model.MediaItem
import com.galleria.app.data.model.MediaStoreFolderKey
import com.galleria.app.data.repository.MediaStoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for FolderDetailScreen managing folder-filtered paged MediaItem stream.
 */
class FolderDetailViewModel(
    application: Application,
    val folderKey: MediaStoreFolderKey
) : AndroidViewModel(application) {

    private val repository = MediaStoreRepository(application.applicationContext)

    val photosPagingData: Flow<PagingData<MediaItem>> =
        repository.getPhotosInFolderPagingData(folderKey)
            .cachedIn(viewModelScope)

    private val _folderInfo = MutableStateFlow<FolderItem?>(null)
    val folderInfo: StateFlow<FolderItem?> = _folderInfo.asStateFlow()

    init {
        loadFolderInfo()
    }

    private fun loadFolderInfo() {
        viewModelScope.launch {
            repository.observeFolders().collect { folders ->
                val match = folders.find { it.key == folderKey }
                _folderInfo.value = match
            }
        }
    }

    class Factory(
        private val application: Application,
        private val folderKey: MediaStoreFolderKey
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FolderDetailViewModel(application, folderKey) as T
        }
    }
}
