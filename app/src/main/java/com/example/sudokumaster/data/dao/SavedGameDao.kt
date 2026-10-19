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

    /** 指定槽的进行中进度（用于恢复 / 覆盖确认）。 */
    @Query("SELECT * FROM saved_game WHERE slotId = :slotId")
    fun observeSlot(slotId: String): Flow<SavedGameEntity?>

    @Query("SELECT * FROM saved_game WHERE slotId = :slotId")
    suspend fun getBySlot(slotId: String): SavedGameEntity?

    /** 最近保存的进度（用于首页「继续游戏」）。 */
    @Query("SELECT * FROM saved_game ORDER BY savedAt DESC LIMIT 1")
    fun loadLatest(): Flow<SavedGameEntity?>

    /** 全部进行中进度（多槽），按保存时间倒序。 */
    @Query("SELECT * FROM saved_game ORDER BY savedAt DESC")
    fun allSlots(): Flow<List<SavedGameEntity>>

    @Query("DELETE FROM saved_game WHERE slotId = :slotId")
    suspend fun clearSlot(slotId: String)

    @Query("DELETE FROM saved_game")
    suspend fun clear()
}
