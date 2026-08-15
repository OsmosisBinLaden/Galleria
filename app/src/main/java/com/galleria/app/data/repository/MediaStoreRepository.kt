package com.galleria.app.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.galleria.app.data.model.FolderItem
import com.galleria.app.data.model.MediaItem
import com.galleria.app.data.model.MediaStoreFolderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext

/**
 * Repository responsible for providing paged photo streams and physical folder discovery
 * from Android's MediaStore.
 */
class MediaStoreRepository(private val context: Context) {

    private var currentPagingSource: MediaStorePagingSource? = null

    /**
     * Creates a paged Flow of MediaItems for the main timeline.
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
     * Creates a paged Flow of MediaItems filtered by a specific volume-aware MediaStoreFolderKey.
     */
    fun getPhotosInFolderPagingData(folderKey: MediaStoreFolderKey): Flow<PagingData<MediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = 60,
                prefetchDistance = 30,
                maxSize = 420,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { MediaStorePagingSource(context, folderKey) }
        ).flow
    }

    /**
     * Invalidates the active PagingSource, prompting Paging 3 to refresh.
     */
    fun invalidatePagingSource() {
        currentPagingSource?.invalidate()
    }

    /**
     * Discovers physical media folders from MediaStore dynamically.
     * The query runs on Dispatchers.IO and aggregates folder metadata without materializing
     * MediaItem objects for the entire library. Memory usage is proportional primarily to
     * the number of discovered folders rather than the number of media items.
     */
    suspend fun getFolders(): List<FolderItem> = withContext(Dispatchers.IO) {
        val folderMap = LinkedHashMap<MediaStoreFolderKey, FolderAccumulator>()

        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH,
                MediaStore.Images.Media.VOLUME_NAME,
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED
            )
        } else {
            arrayOf(
                MediaStore.Images.Media.BUCKET_ID,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED
            )
        }

        val targetUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val queryArgs = Bundle().apply {
                putStringArray(
                    ContentResolver.QUERY_ARG_SORT_COLUMNS,
                    arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
                )
                putInt(
                    ContentResolver.QUERY_ARG_SORT_DIRECTION,
                    ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                )
            }
            context.contentResolver.query(targetUri, projection, queryArgs, null)
        } else {
            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"
            context.contentResolver.query(targetUri, projection, null, null, sortOrder)
        }

        cursor?.use { c ->
            val bucketIdCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val relativePathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            } else -1
            val volumeNameCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                c.getColumnIndex(MediaStore.Images.Media.VOLUME_NAME)
            } else -1

            while (c.moveToNext()) {
                val bucketId = c.getLong(bucketIdCol)
                val displayName = c.getString(bucketNameCol) ?: "Pictures"
                val id = c.getLong(idCol)
                val relativePath = if (relativePathCol >= 0) c.getString(relativePathCol) ?: "" else ""
                val volumeName = if (volumeNameCol >= 0) {
                    c.getString(volumeNameCol) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                } else MediaStore.VOLUME_EXTERNAL_PRIMARY

                val key = MediaStoreFolderKey(volumeName = volumeName, bucketId = bucketId)

                var accumulator = folderMap[key]
                if (accumulator == null) {
                    val volumeUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(volumeName)
                    } else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

                    val coverUri = ContentUris.withAppendedId(volumeUri, id)
                    accumulator = FolderAccumulator(
                        key = key,
                        displayName = displayName,
                        relativePath = relativePath,
                        coverThumbnailUri = coverUri,
                        count = 1
                    )
                    folderMap[key] = accumulator
                } else {
                    accumulator.count++
                }
            }
        }

        folderMap.values.map { acc ->
            FolderItem(
                key = acc.key,
                displayName = acc.displayName,
                relativePath = acc.relativePath,
                mediaCount = acc.count,
                coverThumbnailUri = acc.coverThumbnailUri
            )
        }
    }

    /**
     * Observes MediaStore image changes via Android ContentObserver.
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

    /**
     * Observes physical folder list updates when collected.
     * Emits initial folder data immediately, and re-queries folders when MediaStore changes occur.
     */
    @OptIn(FlowPreview::class)
    fun observeFolders(): Flow<List<FolderItem>> = channelFlow {
        send(getFolders())
        observeMediaStoreChanges()
            .debounce(500L)
            .collect {
                send(getFolders())
            }
    }

    private class FolderAccumulator(
        val key: MediaStoreFolderKey,
        val displayName: String,
        val relativePath: String,
        val coverThumbnailUri: Uri?,
        var count: Int
    )
}
