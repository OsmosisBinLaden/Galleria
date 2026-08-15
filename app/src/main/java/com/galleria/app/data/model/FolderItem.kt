package com.galleria.app.data.model

import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable

/**
 * Value model representing a volume-aware physical folder identity.
 */
@Immutable
data class MediaStoreFolderKey(
    val volumeName: String = MediaStore.VOLUME_EXTERNAL_PRIMARY,
    val bucketId: Long
)

/**
 * Model representing a physical folder discovered dynamically from MediaStore.
 */
@Immutable
data class FolderItem(
    val key: MediaStoreFolderKey,
    val displayName: String,
    val relativePath: String,
    val mediaCount: Int,
    val coverThumbnailUri: Uri?
)
