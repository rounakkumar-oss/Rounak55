package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandHistoryDao {
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC LIMIT 50")
    fun getAllCommands(): Flow<List<CommandHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommand(command: CommandHistoryEntity): Long

    @Query("DELETE FROM command_history")
    suspend fun clearHistory()
}
