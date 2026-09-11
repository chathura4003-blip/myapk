package com.clouddrive.leech.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val url: String,
    val title: String,
    val poster: String = "",
    val source: String = "",
    val category: String = "movies",
    val addedTimestamp: Long = System.currentTimeMillis()
)
