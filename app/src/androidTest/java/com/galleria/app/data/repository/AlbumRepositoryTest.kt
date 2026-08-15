package com.galleria.app.data.repository

import androidx.paging.PagingSource
import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galleria.app.data.local.GalleriaDatabase
import com.galleria.app.data.local.dao.AlbumDao
import com.galleria.app.data.local.dao.AlbumMediaCrossRefDao
import com.galleria.app.data.local.entity.AlbumMediaCrossRef
import com.galleria.app.data.model.MediaStoreMediaKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive instrumented unit tests for Phase 2.2C Album Repository + Album Paging.
 */
@RunWith(AndroidJUnit4::class)
class AlbumRepositoryTest {

    private lateinit var db: GalleriaDatabase
    private lateinit var albumDao: AlbumDao
    private lateinit var crossRefDao: AlbumMediaCrossRefDao
    private lateinit var mediaStoreRepository: MediaStoreRepository
    private lateinit var albumRepository: AlbumRepository

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<GalleriaDatabase>(context).build()
        albumDao = db.albumDao()
        crossRefDao = db.albumMediaCrossRefDao()
        mediaStoreRepository = MediaStoreRepository(context)
        albumRepository = AlbumRepository(context, albumDao, crossRefDao, mediaStoreRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testEmptyAlbumPaging() = runBlocking {
        val albumId = albumRepository.createAlbum("Empty Album")
        val pagingSource = AlbumPagingSource(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            crossRefDao = crossRefDao,
            albumId = albumId,
            generationId = 1L,
            onOrphansDetected = { _, _ -> }
        )

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = 0, loadSize = 60, placeholdersEnabled = false)
        )

        assertTrue(result is PagingSource.LoadResult.Page)
        val page = result as PagingSource.LoadResult.Page
        assertTrue(page.data.isEmpty())
        assertNull(page.prevKey)
        assertNull(page.nextKey)
    }

    @Test
    fun testNonexistentAlbumPaging() = runBlocking {
        val pagingSource = AlbumPagingSource(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            crossRefDao = crossRefDao,
            albumId = 99999L,
            generationId = 1L,
            onOrphansDetected = { _, _ -> }
        )

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = 0, loadSize = 60, placeholdersEnabled = false)
        )

        assertTrue(result is PagingSource.LoadResult.Page)
        val page = result as PagingSource.LoadResult.Page
        assertTrue(page.data.isEmpty())
    }

    @Test
    fun testDuplicateMembershipInsertionPreservesTimestamp() = runBlocking {
        val albumId = albumRepository.createAlbum("Favorites")
        val key = MediaStoreMediaKey(volumeName = "external_primary", mediaId = 100L)

        // 1. Initial insert at timestamp 1000
        val ref1 = AlbumMediaCrossRef(albumId = albumId, mediaId = 100L, volumeName = "external_primary", addedAt = 1000L)
        crossRefDao.insert(ref1)

        // Verify initial addedAt
        val pagedInitial = crossRefDao.getMediaKeysForAlbumPaged(albumId, limit = 10, offset = 0)
        assertEquals(1, pagedInitial.size)
        assertEquals(1000L, pagedInitial[0].addedAt)

        // 2. Duplicate insert at timestamp 2000 (with OnConflictStrategy.IGNORE)
        val ref2 = AlbumMediaCrossRef(albumId = albumId, mediaId = 100L, volumeName = "external_primary", addedAt = 2000L)
        crossRefDao.insert(ref2)

        // Verify row was IGNORED and original timestamp 1000L is preserved
        val pagedAfter = crossRefDao.getMediaKeysForAlbumPaged(albumId, limit = 10, offset = 0)
        assertEquals(1, pagedAfter.size)
        assertEquals(1000L, pagedAfter[0].addedAt)
    }

    @Test
    fun testBulkMediaAdditionTransaction() = runBlocking {
        val albumId = albumRepository.createAlbum("Batch Album")
        val keys = listOf(
            MediaStoreMediaKey("external_primary", 101L),
            MediaStoreMediaKey("external_primary", 102L),
            MediaStoreMediaKey("external_primary", 103L)
        )

        albumRepository.addMediaToAlbum(albumId, keys)

        val refs = crossRefDao.getMediaKeysForAlbumPaged(albumId, limit = 10, offset = 0)
        assertEquals(3, refs.size)
    }

    @Test
    fun testDeterministicCrossVolumeOrdering() = runBlocking {
        val albumId = albumRepository.createAlbum("Volume Ordering")
        val now = 5000L

        // Insert items across volumes with identical timestamp
        val refA = AlbumMediaCrossRef(albumId = albumId, mediaId = 200L, volumeName = "external_primary", addedAt = now)
        val refB = AlbumMediaCrossRef(albumId = albumId, mediaId = 100L, volumeName = "secondary_sd", addedAt = now)
        val refC = AlbumMediaCrossRef(albumId = albumId, mediaId = 300L, volumeName = "external_primary", addedAt = now)

        crossRefDao.insertAll(listOf(refA, refB, refC))

        // ORDER BY addedAt DESC, volumeName ASC, mediaId DESC
        val orderedRefs = crossRefDao.getMediaKeysForAlbumPaged(albumId, limit = 10, offset = 0)
        assertEquals(3, orderedRefs.size)

        // external_primary (300L), external_primary (200L), secondary_sd (100L)
        assertEquals("external_primary", orderedRefs[0].volumeName)
        assertEquals(300L, orderedRefs[0].mediaId)

        assertEquals("external_primary", orderedRefs[1].volumeName)
        assertEquals(200L, orderedRefs[1].mediaId)

        assertEquals("secondary_sd", orderedRefs[2].volumeName)
        assertEquals(100L, orderedRefs[2].mediaId)
    }

    @Test
    fun testAlbumRenameAndDeleteCascade() = runBlocking {
        val albumId = albumRepository.createAlbum("Old Name")
        assertTrue(albumRepository.renameAlbum(albumId, "New Name"))

        val renamed = albumRepository.getAlbumById(albumId)
        assertNotNull(renamed)
        assertEquals("New Name", renamed?.name)

        // Add media reference
        albumRepository.addMediaToAlbum(albumId, listOf(MediaStoreMediaKey("external_primary", 501L)))

        // Delete album
        assertTrue(albumRepository.deleteAlbum(albumId))

        // Verify CASCADE deleted references in Room
        val remainingRefs = crossRefDao.getMediaKeysForAlbumPaged(albumId, limit = 10, offset = 0)
        assertTrue(remainingRefs.isEmpty())
    }

    @Test
    fun testOrphanDetectionAndNonBlockingReturn() = runBlocking {
        val albumId = albumRepository.createAlbum("Orphan Test")

        // Add dummy references not existing in MediaStore
        val orphanRef1 = AlbumMediaCrossRef(albumId = albumId, mediaId = 9999901L, volumeName = "external_primary", addedAt = 1000L)
        val orphanRef2 = AlbumMediaCrossRef(albumId = albumId, mediaId = 9999902L, volumeName = "external_primary", addedAt = 1000L)
        crossRefDao.insertAll(listOf(orphanRef1, orphanRef2))

        var detectedOrphansCount = 0
        val pagingSource = AlbumPagingSource(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            crossRefDao = crossRefDao,
            albumId = albumId,
            generationId = 1L,
            onOrphansDetected = { _, orphans ->
                detectedOrphansCount += orphans.size
            }
        )

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(key = 0, loadSize = 60, placeholdersEnabled = false)
        )

        // 1. Returns available items immediately (0 valid items)
        assertTrue(result is PagingSource.LoadResult.Page)
        val page = result as PagingSource.LoadResult.Page
        assertTrue(page.data.isEmpty())

        // 2. Orphan detection callback fired for 2 missing items
        assertEquals(2, detectedOrphansCount)
    }

    @Test
    fun testRealMediaStoreAlbumPagingIfPhotosExist() = runBlocking {
        val realFolders = mediaStoreRepository.getFolders()
        if (realFolders.isNotEmpty()) {
            val albumId = albumRepository.createAlbum("Real Media Album")
            val pagedFolderMedia = mediaStoreRepository.getPhotosInFolderPagingData(realFolders.first().key)

            // Collect single page from MediaStore folder to populate album
            val sampleMediaKeys = mutableListOf<MediaStoreMediaKey>()
            val samplePagingSource = MediaStorePagingSource(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                folderKey = realFolders.first().key
            )
            val loadResult = samplePagingSource.load(PagingSource.LoadParams.Refresh(key = 0, loadSize = 5, placeholdersEnabled = false))
            if (loadResult is PagingSource.LoadResult.Page) {
                loadResult.data.forEach { item ->
                    sampleMediaKeys.add(MediaStoreMediaKey(realFolders.first().key.volumeName, item.id))
                }
            }

            if (sampleMediaKeys.isNotEmpty()) {
                albumRepository.addMediaToAlbum(albumId, sampleMediaKeys)

                val albumPagingSource = AlbumPagingSource(
                    context = InstrumentationRegistry.getInstrumentation().targetContext,
                    crossRefDao = crossRefDao,
                    albumId = albumId,
                    generationId = 1L,
                    onOrphansDetected = { _, _ -> }
                )

                val albumResult = albumPagingSource.load(PagingSource.LoadParams.Refresh(key = 0, loadSize = 60, placeholdersEnabled = false))
                assertTrue(albumResult is PagingSource.LoadResult.Page)
                val page = albumResult as PagingSource.LoadResult.Page
                assertEquals(sampleMediaKeys.size, page.data.size)
            }
        }
    }
}
