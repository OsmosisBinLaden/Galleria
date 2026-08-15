package com.galleria.app.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.galleria.app.data.local.entity.AlbumMediaCrossRef
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for album ↔ media cross-reference operations.
 */
@Dao
interface AlbumMediaCrossRefDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(crossRef: AlbumMediaCrossRef): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<AlbumMediaCrossRef>): List<Long>

    @Query("DELETE FROM album_media_cross_ref WHERE albumId = :albumId AND mediaId = :mediaId AND volumeName = :volumeName")
    suspend fun removeMediaFromAlbum(albumId: Long, mediaId: Long, volumeName: String): Int

    @Query("SELECT * FROM album_media_cross_ref WHERE albumId = :albumId ORDER BY addedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getMediaKeysForAlbumPaged(albumId: Long, limit: Int, offset: Int): List<AlbumMediaCrossRef>

    @Query("SELECT * FROM album_media_cross_ref WHERE albumId = :albumId ORDER BY addedAt DESC")
    fun getMediaKeysForAlbum(albumId: Long): Flow<List<AlbumMediaCrossRef>>

    @Query("SELECT EXISTS(SELECT 1 FROM album_media_cross_ref WHERE albumId = :albumId AND mediaId = :mediaId AND volumeName = :volumeName)")
    suspend fun isMediaInAlbum(albumId: Long, mediaId: Long, volumeName: String): Boolean

    @Query("DELETE FROM album_media_cross_ref WHERE mediaId = :mediaId AND volumeName = :volumeName")
    suspend fun deleteReferencesForMedia(mediaId: Long, volumeName: String): Int
}
