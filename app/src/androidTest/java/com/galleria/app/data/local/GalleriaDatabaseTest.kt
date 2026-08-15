package com.galleria.app.data.local

import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galleria.app.data.local.dao.AlbumDao
import com.galleria.app.data.local.dao.AlbumMediaCrossRefDao
import com.galleria.app.data.local.entity.AlbumEntity
import com.galleria.app.data.local.entity.AlbumMediaCrossRef
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
 * Instrumented Room 3 Database tests verifying Phase 2.2A Room Foundation.
 */
@RunWith(AndroidJUnit4::class)
class GalleriaDatabaseTest {

    private lateinit var db: GalleriaDatabase
    private lateinit var albumDao: AlbumDao
    private lateinit var crossRefDao: AlbumMediaCrossRefDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder<GalleriaDatabase>(context).build()
        albumDao = db.albumDao()
        crossRefDao = db.albumMediaCrossRefDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testAlbumInsertUpdateDelete() = runBlocking {
        // 1. Create album
        val album = AlbumEntity(name = "Vacation 2026", createdAt = 1000L, updatedAt = 1000L)
        val albumId = albumDao.insertAlbum(album)
        assertTrue(albumId > 0)

        // Verify retrieval
        val fetchedAlbum = albumDao.getAlbumById(albumId)
        assertNotNull(fetchedAlbum)
        assertEquals("Vacation 2026", fetchedAlbum?.name)

        // 2. Update album
        val updatedAlbum = fetchedAlbum!!.copy(name = "Summer Vacation 2026", updatedAt = 2000L)
        albumDao.updateAlbum(updatedAlbum)

        val refetchedAlbum = albumDao.getAlbumById(albumId)
        assertEquals("Summer Vacation 2026", refetchedAlbum?.name)

        // 3. Delete album
        albumDao.deleteAlbum(refetchedAlbum!!)
        val deletedAlbum = albumDao.getAlbumById(albumId)
        assertNull(deletedAlbum)
    }

    @Test
    fun testMediaMembershipAndCascadeDeletion() = runBlocking {
        // 1. Create album
        val albumId = albumDao.insertAlbum(AlbumEntity(name = "College", createdAt = 1000L, updatedAt = 1000L))

        // 2. Add media membership
        val ref1 = AlbumMediaCrossRef(albumId = albumId, mediaId = 101L, volumeName = "external_primary", addedAt = 1000L)
        val ref2 = AlbumMediaCrossRef(albumId = albumId, mediaId = 102L, volumeName = "external_primary", addedAt = 1000L)
        crossRefDao.insertAll(listOf(ref1, ref2))

        // Verify membership check
        assertTrue(crossRefDao.isMediaInAlbum(albumId, 101L, "external_primary"))
        assertTrue(crossRefDao.isMediaInAlbum(albumId, 102L, "external_primary"))
        assertFalse(crossRefDao.isMediaInAlbum(albumId, 999L, "external_primary"))

        // Verify paged retrieval
        val pagedRefs = crossRefDao.getMediaKeysForAlbumPaged(albumId, limit = 10, offset = 0)
        assertEquals(2, pagedRefs.size)

        // 3. Remove single media membership
        crossRefDao.removeMediaFromAlbum(albumId, 101L, "external_primary")
        assertFalse(crossRefDao.isMediaInAlbum(albumId, 101L, "external_primary"))

        // 4. Test CASCADE deletion when album is deleted
        val album = albumDao.getAlbumById(albumId)
        assertNotNull(album)
        albumDao.deleteAlbum(album!!)

        // Verify cross references are cascade deleted
        val remainingRefs = crossRefDao.getMediaKeysForAlbumPaged(albumId, limit = 10, offset = 0)
        assertTrue(remainingRefs.isEmpty())
    }

    @Test
    fun testCompositeUniqueness() = runBlocking {
        val albumId = albumDao.insertAlbum(AlbumEntity(name = "Favorites Sample", createdAt = 1000L, updatedAt = 1000L))

        val ref = AlbumMediaCrossRef(albumId = albumId, mediaId = 500L, volumeName = "external_primary", addedAt = 1000L)
        crossRefDao.insert(ref)

        // Re-inserting same (albumId, mediaId, volumeName) ignores duplicate record and preserves original addedAt timestamp
        val updatedRef = AlbumMediaCrossRef(albumId = albumId, mediaId = 500L, volumeName = "external_primary", addedAt = 2000L)
        crossRefDao.insert(updatedRef)

        val paged = crossRefDao.getMediaKeysForAlbumPaged(albumId, limit = 10, offset = 0)
        assertEquals(1, paged.size)
        assertEquals(1000L, paged[0].addedAt)
    }
}
