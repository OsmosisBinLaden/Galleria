package com.galleria.app.data.local.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.galleria.app.data.local.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for custom album operations.
 */
@Dao
interface AlbumDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Update
    suspend fun updateAlbum(album: AlbumEntity): Int

    @Delete
    suspend fun deleteAlbum(album: AlbumEntity): Int

    @Query("SELECT * FROM albums ORDER BY updatedAt DESC")
    fun getAlbumsFlow(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    suspend fun getAlbumById(albumId: Long): AlbumEntity?
}
