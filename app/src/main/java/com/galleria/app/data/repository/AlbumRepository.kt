package com.galleria.app.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.galleria.app.data.local.dao.AlbumDao
import com.galleria.app.data.local.dao.AlbumMediaCrossRefDao
import com.galleria.app.data.local.entity.AlbumEntity
import com.galleria.app.data.local.entity.AlbumMediaCrossRef
import com.galleria.app.data.model.MediaItem
import com.galleria.app.data.model.MediaStoreMediaKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Repository managing custom album CRUD operations, membership transactions,
 * and paged album media streams.
 */
class AlbumRepository(
    private val context: Context,
    private val albumDao: AlbumDao,
    private val crossRefDao: AlbumMediaCrossRefDao,
    private val mediaStoreRepository: MediaStoreRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val generationCounter = AtomicLong(0L)
    private val activePagingSources = ConcurrentHashMap<Long, AlbumPagingSource>()

    /**
     * Observes all custom albums in Galleria.
     */
    fun observeAlbums(): Flow<List<AlbumEntity>> = albumDao.getAlbumsFlow()

    /**
     * Fetches a single album by its ID.
     */
    suspend fun getAlbumById(albumId: Long): AlbumEntity? = withContext(Dispatchers.IO) {
        albumDao.getAlbumById(albumId)
    }

    /**
     * Creates a new custom album.
     */
    suspend fun createAlbum(name: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val album = AlbumEntity(name = name, createdAt = now, updatedAt = now)
        albumDao.insertAlbum(album)
    }

    /**
     * Renames an existing custom album.
     */
    suspend fun renameAlbum(albumId: Long, newName: String): Boolean = withContext(Dispatchers.IO) {
        val existing = albumDao.getAlbumById(albumId) ?: return@withContext false
        val updated = existing.copy(name = newName, updatedAt = System.currentTimeMillis())
        val rows = albumDao.updateAlbum(updated)
        if (rows > 0) {
            invalidateAlbumPagingSource(albumId)
            true
        } else false
    }

    /**
     * Deletes a custom album.
     * Room CASCADE handles deleting cross-reference rows without touching MediaStore files.
     */
    suspend fun deleteAlbum(albumId: Long): Boolean = withContext(Dispatchers.IO) {
        val existing = albumDao.getAlbumById(albumId) ?: return@withContext false
        val rows = albumDao.deleteAlbum(existing)
        if (rows > 0) {
            invalidateAlbumPagingSource(albumId)
            true
        } else false
    }

    /**
     * Bulk inserts media membership into an album atomically in a single Room 3 @Transaction operation.
     * Duplicate insertions use OnConflictStrategy.IGNORE to preserve original addedAt timestamps.
     */
    suspend fun addMediaToAlbum(albumId: Long, mediaKeys: List<MediaStoreMediaKey>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val crossRefs = mediaKeys.map { key ->
            AlbumMediaCrossRef(
                albumId = albumId,
                mediaId = key.mediaId,
                volumeName = key.volumeName,
                addedAt = now
            )
        }
        crossRefDao.addMediaToAlbumTransactional(crossRefs)
        invalidateAlbumPagingSource(albumId)
    }

    /**
     * Removes a single media item from an album.
     */
    suspend fun removeMediaFromAlbum(albumId: Long, mediaKey: MediaStoreMediaKey): Boolean = withContext(Dispatchers.IO) {
        val rows = crossRefDao.removeMediaFromAlbum(albumId, mediaKey.mediaId, mediaKey.volumeName)
        if (rows > 0) {
            invalidateAlbumPagingSource(albumId)
            true
        } else false
    }

    /**
     * Returns a paged Flow of MediaItems for a specific custom album.
     * Paging bounds retained media metadata while Coil independently manages image caching and bitmap memory.
     */
    fun getAlbumPhotosPagingData(albumId: Long): Flow<PagingData<MediaItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = 60,
                prefetchDistance = 30,
                maxSize = 420,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                val generationId = generationCounter.incrementAndGet()
                AlbumPagingSource(
                    context = context,
                    crossRefDao = crossRefDao,
                    albumId = albumId,
                    generationId = generationId,
                    onOrphansDetected = { genId, orphans ->
                        coroutineScope.launch {
                            handleOrphanedReferences(albumId, genId, orphans)
                        }
                    }
                ).also { source ->
                    activePagingSources[albumId] = source
                }
            }
        ).flow
    }

    /**
     * Asynchronously handles orphaned references missing from MediaStore.
     * Deletes orphan references from Room inside a single @Transaction without touching MediaStore files.
     * Invalidates ONLY if the generation that discovered the orphan is still the active generation.
     */
    private suspend fun handleOrphanedReferences(
        albumId: Long,
        generationId: Long,
        orphans: List<AlbumMediaCrossRef>
    ) = withContext(Dispatchers.IO) {
        crossRefDao.removeOrphansTransactional(albumId, orphans)

        val activeSource = activePagingSources[albumId]
        if (activeSource != null && activeSource.generationId == generationId) {
            activeSource.invalidate()
        }
    }

    private fun invalidateAlbumPagingSource(albumId: Long) {
        activePagingSources[albumId]?.invalidate()
    }
}
