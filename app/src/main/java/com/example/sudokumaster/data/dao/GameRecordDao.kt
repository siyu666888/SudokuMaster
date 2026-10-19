package com.example.sudokumaster.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.sudokumaster.data.entity.GameRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameRecordDao {

    @Insert
    suspend fun insert(record: GameRecordEntity): Long

    @Query("SELECT * FROM game_records ORDER BY date DESC")
    fun all(): Flow<List<GameRecordEntity>>

    @Query("SELECT * FROM game_records ORDER BY date DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<GameRecordEntity>>

    @Query("SELECT * FROM game_records WHERE id = :id")
    suspend fun getById(id: Long): GameRecordEntity?

    @Insert
    suspend fun insertAll(records: List<GameRecordEntity>)

    @Query("DELETE FROM game_records")
    suspend fun clear()
}
