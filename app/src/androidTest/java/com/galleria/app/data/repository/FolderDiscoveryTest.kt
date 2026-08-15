package com.galleria.app.data.repository

import androidx.paging.PagingSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.galleria.app.data.model.MediaStoreFolderKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests verifying Phase 2.2B Folder Discovery & Volume-Aware Filtering.
 */
@RunWith(AndroidJUnit4::class)
class FolderDiscoveryTest {

    private lateinit var repository: MediaStoreRepository

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repository = MediaStoreRepository(context)
    }

    @Test
    fun testFolderDiscovery() = runBlocking {
        val folders = repository.getFolders()
        assertNotNull(folders)

        // Folders list should be empty if no permissions/photos, or contain discovered items
        folders.forEach { folder ->
            assertNotNull(folder.key)
            assertNotNull(folder.key.volumeName)
            assertTrue(folder.key.volumeName.isNotEmpty())
            assertTrue(folder.displayName.isNotEmpty())
            assertTrue(folder.mediaCount > 0)
            if (folder.coverThumbnailUri != null) {
                assertTrue(folder.coverThumbnailUri.toString().startsWith("content://"))
            }
        }
    }

    @Test
    fun testFolderPagingSourceFilterByVolumeAndBucket() = runBlocking {
        val folders = repository.getFolders()
        if (folders.isNotEmpty()) {
            val targetFolder = folders.first()
            val pagingSource = MediaStorePagingSource(
                context = InstrumentationRegistry.getInstrumentation().targetContext,
                folderKey = targetFolder.key
            )

            val result = pagingSource.load(
                PagingSource.LoadParams.Refresh(
                    key = 0,
                    loadSize = 20,
                    placeholdersEnabled = false
                )
            )

            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            assertTrue(page.data.isNotEmpty())
            assertTrue(page.data.size <= targetFolder.mediaCount)
        }
    }
}
