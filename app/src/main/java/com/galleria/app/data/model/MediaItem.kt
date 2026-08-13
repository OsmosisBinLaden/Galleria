package com.galleria.app.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

/**
 * Data model representing a photo item retrieved from MediaStore.
 */
@Immutable
data class MediaItem(
    val id: Long,
    val contentUri: Uri,
    val displayName: String,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val mimeType: String
)
