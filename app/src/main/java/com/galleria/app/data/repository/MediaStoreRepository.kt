package com.galleria.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.galleria.app.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository responsible for querying photos from Android's MediaStore.
 */
class MediaStoreRepository(private val context: Context) {

    /**
     * Fetch photos from MediaStore chronologically descending (newest first).
     * Runs off the main thread on Dispatchers.IO.
     */
    suspend fun getPhotos(): List<MediaItem> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<MediaItem>()

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

        // Primary sort: DATE_TAKEN descending, fallback: DATE_ADDED descending
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
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

                // Fallback to DATE_ADDED (in seconds -> millis) if DATE_TAKEN is missing or 0
                val dateTaken = if (rawDateTaken > 0) {
                    rawDateTaken
                } else {
                    rawDateAdded * 1000L
                }

                val size = cursor.getLong(sizeColumn)
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val mimeType = cursor.getString(mimeTypeColumn) ?: "image/*"

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

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

        photos
    }
}
