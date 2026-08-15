package com.galleria.app.data.local

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import com.galleria.app.data.local.dao.AlbumDao
import com.galleria.app.data.local.dao.AlbumMediaCrossRefDao
import com.galleria.app.data.local.entity.AlbumEntity
import com.galleria.app.data.local.entity.AlbumMediaCrossRef

/**
 * Main Room 3 Database for Galleria storing app-owned metadata.
 */
@Database(
    entities = [AlbumEntity::class, AlbumMediaCrossRef::class],
    version = 1,
    exportSchema = true
)
abstract class GalleriaDatabase : RoomDatabase() {

    abstract fun albumDao(): AlbumDao
    abstract fun albumMediaCrossRefDao(): AlbumMediaCrossRefDao

    companion object {
        @Volatile
        private var INSTANCE: GalleriaDatabase? = null

        fun getDatabase(context: Context): GalleriaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder<GalleriaDatabase>(
                    context = context.applicationContext,
                    name = "galleria_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
