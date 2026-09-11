package com.clouddrive.leech.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val url: String,
    val filename: String = "",
    val localFilePath: String = "",
    val downloadManagerId: Long = -1L,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = "pending", // pending, downloading, completed, paused, failed
    val category: String = "media",
    val speed: String = "",
    val progress: Int = 0,
    val errorMessage: String = "",
    val createdTimestamp: Long = System.currentTimeMillis()
)
