package com.galleria.app.data.model

import android.provider.MediaStore
import androidx.compose.runtime.Immutable

/**
 * Value model representing an external MediaStore media item identity
 * combining storage volume name and media ID.
 */
@Immutable
data class MediaStoreMediaKey(
    val volumeName: String = MediaStore.VOLUME_EXTERNAL_PRIMARY,
    val mediaId: Long
)
