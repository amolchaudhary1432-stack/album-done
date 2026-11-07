package com.albumdone.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 6 to 7: "how many photos of this person", kept with everything already made. */
val PEOPLE_WANTED = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE people ADD COLUMN wantedPhotos INTEGER")
    }
}

/** 7 to 8: what each photo shows (SigLIP numbers) for the album brain; older albums keep the rules. */
val SCENE_VECTORS = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `scenes` (`photoId` INTEGER NOT NULL, `projectId` INTEGER NOT NULL, `vector` BLOB NOT NULL, PRIMARY KEY(`photoId`))")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_scenes_projectId` ON `scenes` (`projectId`)")
    }
}

@Database(entities = [ProjectEntity::class, PhotoEntity::class, FaceEntity::class, PersonEntity::class, SceneEntity::class], version = 8, exportSchema = false)
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
                    .addMigrations(PEOPLE_WANTED, SCENE_VECTORS)
                    // Test builds only: an older layout without a migration starts fresh.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
