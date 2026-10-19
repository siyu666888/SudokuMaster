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

    /** 指定 (尺寸:难度) 的历史最快用时（无记录时返回 null）。聚合下推 SQL（P2）。 */
    @Query("SELECT MIN(timeMs) FROM game_records WHERE sizeId = :sizeId AND diffId = :diffId")
    suspend fun minTimeMs(sizeId: String, diffId: Int): Long?

    @Insert
    suspend fun insertAll(records: List<GameRecordEntity>)

    @Query("DELETE FROM game_records")
    suspend fun clear()
}
