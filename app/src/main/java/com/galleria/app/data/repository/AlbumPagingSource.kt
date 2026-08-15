package com.galleria.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.galleria.app.data.local.dao.AlbumMediaCrossRefDao
import com.galleria.app.data.local.entity.AlbumMediaCrossRef
import com.galleria.app.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PagingSource that resolves custom album media items in two stages:
 * Stage 1: Room query for album references (max 60 per page).
 * Stage 2: Bounded MediaStore query grouped by volume to resolve MediaItem objects.
 * Paging bounds retained media metadata while Coil independently manages image caching and bitmap memory.
 */
class AlbumPagingSource(
    private val context: Context,
    private val crossRefDao: AlbumMediaCrossRefDao,
    private val albumId: Long,
    val generationId: Long,
    private val onOrphansDetected: (generationId: Long, orphans: List<AlbumMediaCrossRef>) -> Unit
) : PagingSource<Int, MediaItem>() {

    override fun getRefreshKey(state: PagingState<Int, MediaItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MediaItem> {
        return withContext(Dispatchers.IO) {
            try {
                val pageNumber = params.key ?: 0
                val pageSize = params.loadSize
                val offset = pageNumber * pageSize

                // Stage 1: Load Room album cross-reference page
                val pageRefs = crossRefDao.getMediaKeysForAlbumPaged(albumId, pageSize, offset)

                if (pageRefs.isEmpty()) {
                    return@withContext LoadResult.Page(
                        data = emptyList(),
                        prevKey = if (pageNumber == 0) null else pageNumber - 1,
                        nextKey = null
                    )
                }

                // Stage 2: Resolve MediaStore items grouped by volumeName
                val resolvedMediaMap = mutableMapOf<Pair<String, Long>, MediaItem>()
                val refsByVolume = pageRefs.groupBy { it.volumeName }

                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.WIDTH,
                    MediaStore.Images.Media.HEIGHT,
                    MediaStore.Images.Media.MIME_TYPE
                )

                for ((volumeName, volumeRefs) in refsByVolume) {
                    val volumeUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(volumeName)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }

                    val mediaIds = volumeRefs.map { it.mediaId }
                    val placeholders = mediaIds.joinToString(",") { "?" }
                    val selection = "${MediaStore.Images.Media._ID} IN ($placeholders)"
                    val selectionArgs = mediaIds.map { it.toString() }.toTypedArray()

                    try {
                        context.contentResolver.query(
                            volumeUri,
                            projection,
                            selection,
                            selectionArgs,
                            null
                        )?.use { cursor ->
                            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                            val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                            while (cursor.moveToNext()) {
                                val id = cursor.getLong(idColumn)
                                val displayName = cursor.getString(displayNameColumn) ?: ""
                                val rawDateTaken = cursor.getLong(dateTakenColumn)
                                val rawDateAdded = cursor.getLong(dateAddedColumn)

                                val dateTaken = if (rawDateTaken > 0) rawDateTaken else rawDateAdded * 1000L
                                val size = cursor.getLong(sizeColumn)
                                val width = cursor.getInt(widthColumn)
                                val height = cursor.getInt(heightColumn)
                                val mimeType = cursor.getString(mimeTypeColumn) ?: "image/*"

                                val contentUri = ContentUris.withAppendedId(volumeUri, id)

                                val mediaItem = MediaItem(
                                    id = id,
                                    contentUri = contentUri,
                                    displayName = displayName,
                                    dateTaken = dateTaken,
                                    size = size,
                                    width = width,
                                    height = height,
                                    mimeType = mimeType
                                )

                                resolvedMediaMap[Pair(volumeName, id)] = mediaItem
                            }
                        }
                    } catch (e: SecurityException) {
                        return@withContext LoadResult.Error(e)
                    } catch (e: Exception) {
                        return@withContext LoadResult.Error(e)
                    }
                }

                // Reconstruct items in exact Room ordering and detect orphans
                val resultList = mutableListOf<MediaItem>()
                val orphanedRefs = mutableListOf<AlbumMediaCrossRef>()

                for (ref in pageRefs) {
                    val resolvedItem = resolvedMediaMap[Pair(ref.volumeName, ref.mediaId)]
                    if (resolvedItem != null) {
                        resultList.add(resolvedItem)
                    } else {
                        orphanedRefs.add(ref)
                    }
                }

                // Schedule orphan cleanup if missing references were detected
                if (orphanedRefs.isNotEmpty()) {
                    onOrphansDetected(generationId, orphanedRefs)
                }

                val prevKey = if (pageNumber == 0) null else pageNumber - 1
                val nextKey = if (pageRefs.isEmpty() || pageRefs.size < pageSize) null else pageNumber + 1

                LoadResult.Page(
                    data = resultList,
                    prevKey = prevKey,
                    nextKey = nextKey
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }
    }
}
