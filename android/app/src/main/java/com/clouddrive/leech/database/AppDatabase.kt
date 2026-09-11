package com.clouddrive.leech.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.clouddrive.leech.database.dao.DownloadDao
import com.clouddrive.leech.database.dao.FavoritesDao
import com.clouddrive.leech.database.dao.HistoryDao
import com.clouddrive.leech.database.entities.DownloadEntity
import com.clouddrive.leech.database.entities.FavoriteEntity
import com.clouddrive.leech.database.entities.HistoryEntity

@Database(
    entities = [
        HistoryEntity::class,
        FavoriteEntity::class,
        DownloadEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clouddrive_standalone.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
