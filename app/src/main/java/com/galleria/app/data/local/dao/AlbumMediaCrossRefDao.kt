package com.galleria.app.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.galleria.app.data.local.entity.AlbumMediaCrossRef
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for album ↔ media cross-reference operations.
 */
@Dao
interface AlbumMediaCrossRefDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(crossRef: AlbumMediaCrossRef): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(crossRefs: List<AlbumMediaCrossRef>): List<Long>

    @Transaction
    suspend fun addMediaToAlbumTransactional(crossRefs: List<AlbumMediaCrossRef>): List<Long> {
        return insertAll(crossRefs)
    }

    @Query("DELETE FROM album_media_cross_ref WHERE albumId = :albumId AND mediaId = :mediaId AND volumeName = :volumeName")
    suspend fun removeMediaFromAlbum(albumId: Long, mediaId: Long, volumeName: String): Int

    @Transaction
    suspend fun removeOrphansTransactional(albumId: Long, orphans: List<AlbumMediaCrossRef>) {
        orphans.forEach { orphan ->
            removeMediaFromAlbum(albumId, orphan.mediaId, orphan.volumeName)
        }
    }

    @Query("SELECT * FROM album_media_cross_ref WHERE albumId = :albumId ORDER BY addedAt DESC, volumeName ASC, mediaId DESC LIMIT :limit OFFSET :offset")
    suspend fun getMediaKeysForAlbumPaged(albumId: Long, limit: Int, offset: Int): List<AlbumMediaCrossRef>

    @Query("SELECT * FROM album_media_cross_ref WHERE albumId = :albumId ORDER BY addedAt DESC, volumeName ASC, mediaId DESC")
    fun getMediaKeysForAlbum(albumId: Long): Flow<List<AlbumMediaCrossRef>>

    @Query("SELECT EXISTS(SELECT 1 FROM album_media_cross_ref WHERE albumId = :albumId AND mediaId = :mediaId AND volumeName = :volumeName)")
    suspend fun isMediaInAlbum(albumId: Long, mediaId: Long, volumeName: String): Boolean

    @Query("DELETE FROM album_media_cross_ref WHERE mediaId = :mediaId AND volumeName = :volumeName")
    suspend fun deleteReferencesForMedia(mediaId: Long, volumeName: String): Int
}
