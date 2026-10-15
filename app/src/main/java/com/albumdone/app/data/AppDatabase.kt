package com.albumdone.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProjectEntity::class, PhotoEntity::class, FaceEntity::class, PersonEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AlbumDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "album_done.db",
                )
                    // Test builds only: a new database layout starts fresh instead of migrating.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
