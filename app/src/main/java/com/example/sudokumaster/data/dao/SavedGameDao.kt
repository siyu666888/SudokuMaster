package com.example.sudokumaster.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.sudokumaster.data.entity.SavedGameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedGameDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: SavedGameEntity)

    @Query("SELECT * FROM saved_game WHERE id = 1")
    fun current(): Flow<SavedGameEntity?>

    @Query("DELETE FROM saved_game")
    suspend fun clear()
}
