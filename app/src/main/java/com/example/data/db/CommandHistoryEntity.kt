package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val userQuery: String,
    val assistantResponse: String,
    val actionType: String,
    val language: String = "en",
    val success: Boolean = true
)
