package com.clouddrive.leech.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.clouddrive.leech.database.entities.DownloadEntity
import com.clouddrive.leech.database.entities.FavoriteEntity
import com.clouddrive.leech.database.entities.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC LIMIT 100")
    suspend fun getAllHistory(): List<HistoryEntity>

    @Query("SELECT * FROM watch_history WHERE url = :url LIMIT 1")
    suspend fun getHistoryByUrl(url: String): HistoryEntity?

    @Query("SELECT * FROM watch_history WHERE watchedDurationMs > 5000 AND (totalDurationMs == 0 OR watchedDurationMs < (totalDurationMs * 95 / 100)) ORDER BY lastWatchedTimestamp DESC LIMIT 30")
    suspend fun getContinueWatching(): List<HistoryEntity>

    @Query("SELECT * FROM watch_history ORDER BY lastWatchedTimestamp DESC LIMIT 100")
    fun observeHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: HistoryEntity)

    @Query("DELETE FROM watch_history WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAll()
}

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorites ORDER BY addedTimestamp DESC")
    suspend fun getAllFavorites(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites ORDER BY addedTimestamp DESC")
    fun observeFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE url = :url)")
    suspend fun isFavorite(url: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE url = :url")
    suspend fun delete(url: String)

    @Query("DELETE FROM favorites")
    suspend fun clearAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdTimestamp DESC")
    suspend fun getAllDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM downloads ORDER BY createdTimestamp DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE downloadManagerId = :dmId LIMIT 1")
    suspend fun getByDmId(dmId: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM downloads WHERE status = 'completed' OR status = 'failed' OR status = 'cancelled'")
    suspend fun clearFinished()

    @Query("SELECT * FROM downloads WHERE status IN ('downloading', 'resolving', 'pending', 'queued', 'starting')")
    suspend fun getActiveDownloads(): List<DownloadEntity>
}
