package com.galleria.app.data.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Entity representing a custom user-created album owned by Galleria.
 */
@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)
