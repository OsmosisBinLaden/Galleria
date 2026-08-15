package com.galleria.app.data.local.entity

import android.provider.MediaStore
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

/**
 * Cross-reference entity mapping media items to custom albums.
 * Album deletion CASCADE-deletes references without affecting underlying MediaStore files.
 */
@Entity(
    tableName = "album_media_cross_ref",
    primaryKeys = ["albumId", "mediaId", "volumeName"],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["albumId", "addedAt"]),
        Index(value = ["mediaId", "volumeName"])
    ]
)
data class AlbumMediaCrossRef(
    val albumId: Long,
    val mediaId: Long,
    val volumeName: String,
    val addedAt: Long
)
