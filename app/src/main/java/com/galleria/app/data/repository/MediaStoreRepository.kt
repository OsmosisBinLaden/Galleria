package com.galleria.app.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.galleria.app.data.model.MediaItem
import kotlinx.coroutines.flow.Flow

/**
 * Repository responsible for providing paged photo streams from Android's MediaStore.
 * MediaStore records are loaded incrementally rather than as one eager List.
 * Paging may retain loaded pages up to the configured max size and can drop distant loaded pages outside the retention window.
 */
class MediaStoreRepository(private val context: Context) {

    /**
     * Creates a paged Flow of MediaItems from MediaStore.
     *
     * Configuration:
     * - pageSize: 60 items (20 rows in a 3-column grid)
     * - prefetchDistance: 30 items (10 rows ahead)
     * - maxSize: 420 items (7 pages / 140 rows retained in memory for smooth back-scrolling)
     */
    fun getPhotosPagingData(): Flow<PagingData<MediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = 60,
                prefetchDistance = 30,
                maxSize = 420,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { MediaStorePagingSource(context) }
        ).flow
    }
}
