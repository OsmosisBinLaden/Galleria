package com.galleria.app.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.galleria.app.data.model.MediaItem
import com.galleria.app.data.model.MediaStoreFolderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PagingSource that queries MediaStore in paged chunks to handle large libraries
 * without loading thousands of items into memory simultaneously.
 * Supports optional filtering by volume-aware MediaStoreFolderKey.
 */
class MediaStorePagingSource(
    private val context: Context,
    private val folderKey: MediaStoreFolderKey? = null
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

                val photos = mutableListOf<MediaItem>()

                val targetUri = if (folderKey != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(folderKey.volumeName)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val queryArgs = Bundle().apply {
                        putInt(ContentResolver.QUERY_ARG_LIMIT, pageSize)
                        putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                        putStringArray(
                            ContentResolver.QUERY_ARG_SORT_COLUMNS,
                            arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
                        )
                        putInt(
                            ContentResolver.QUERY_ARG_SORT_DIRECTION,
                            ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                        )

                        if (folderKey != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                putString(
                                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                                    "${MediaStore.Images.Media.VOLUME_NAME} = ? AND ${MediaStore.Images.Media.BUCKET_ID} = ?"
                                )
                                putStringArray(
                                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                                    arrayOf(folderKey.volumeName, folderKey.bucketId.toString())
                                )
                            } else {
                                putString(
                                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                                    "${MediaStore.Images.Media.BUCKET_ID} = ?"
                                )
                                putStringArray(
                                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                                    arrayOf(folderKey.bucketId.toString())
                                )
                            }
                        }
                    }

                    context.contentResolver.query(
                        targetUri,
                        projection,
                        queryArgs,
                        null
                    )
                } else {
                    val selection = if (folderKey != null) {
                        "${MediaStore.Images.Media.BUCKET_ID} = ?"
                    } else null

                    val selectionArgs = if (folderKey != null) {
                        arrayOf(folderKey.bucketId.toString())
                    } else null

                    val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $pageSize OFFSET $offset"
                    context.contentResolver.query(
                        targetUri,
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder
                    )
                }?.use { cursor ->
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

                        val contentUri = ContentUris.withAppendedId(targetUri, id)

                        photos.add(
                            MediaItem(
                                id = id,
                                contentUri = contentUri,
                                displayName = displayName,
                                dateTaken = dateTaken,
                                size = size,
                                width = width,
                                height = height,
                                mimeType = mimeType
                            )
                        )
                    }
                }

                val prevKey = if (pageNumber == 0) null else pageNumber - 1
                val nextKey = if (photos.isEmpty() || photos.size < pageSize) null else pageNumber + 1

                LoadResult.Page(
                    data = photos,
                    prevKey = prevKey,
                    nextKey = nextKey
                )
            } catch (e: Exception) {
                LoadResult.Error(e)
            }
        }
    }
}
