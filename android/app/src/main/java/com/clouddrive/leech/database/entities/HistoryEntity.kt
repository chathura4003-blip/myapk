package com.clouddrive.leech.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class HistoryEntity(
    @PrimaryKey
    val url: String,
    val title: String,
    val poster: String = "",
    val source: String = "",
    val category: String = "movies",
    val watchedDurationMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
)
