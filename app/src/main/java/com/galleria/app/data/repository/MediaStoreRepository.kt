package com.galleria.app.data.repository

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.galleria.app.data.model.MediaItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Repository responsible for providing paged photo streams from Android's MediaStore.
 * MediaStore records are loaded incrementally rather than as one eager List.
 * Paging may retain loaded pages up to the configured max size and can drop distant loaded pages outside the retention window.
 */
class MediaStoreRepository(private val context: Context) {

    private var currentPagingSource: MediaStorePagingSource? = null

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
            pagingSourceFactory = {
                MediaStorePagingSource(context).also {
                    currentPagingSource = it
                }
            }
        ).flow
    }

    /**
     * Invalidates the active PagingSource, prompting Paging 3 to refresh
     * the dataset from MediaStore without destroying scroll state.
     */
    fun invalidatePagingSource() {
        currentPagingSource?.invalidate()
    }

    /**
     * Observes MediaStore image changes via Android ContentObserver.
     * Emits Unit whenever the underlying image MediaStore content changes.
     */
    fun observeMediaStoreChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }

        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true, // notifyForDescendants
            observer
        )

        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
}
