package com.example.sudokumaster.data.repository

import com.example.sudokumaster.data.dao.GameRecordDao
import com.example.sudokumaster.data.dao.SavedGameDao
import com.example.sudokumaster.data.entity.GameRecordEntity
import com.example.sudokumaster.data.entity.SavedGameEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 当前对局 (saved_game) + 完成记录 (game_records) 的读写。
 */
class GameRepository(
    private val savedDao: SavedGameDao,
    private val recordDao: GameRecordDao
) {
    fun observeSaved(): Flow<SavedGameEntity?> = savedDao.current()

    suspend fun getSaved(): SavedGameEntity? = savedDao.current().first()

    suspend fun saveCurrent(entity: SavedGameEntity) = savedDao.upsert(entity)

    suspend fun clearSaved() = savedDao.clear()

    suspend fun recordWin(entity: GameRecordEntity): Long = recordDao.insert(entity)

    suspend fun getRecord(id: Long): GameRecordEntity? = recordDao.getById(id)

    /** 一次性读取全部记录（备份导出用）。 */
    suspend fun allRecordsOnce(): List<GameRecordEntity> = recordDao.all().first()

    /**
     * 恢复备份：清空现有记录与进行中进度后整体写入。
     * @param records 备份中的历史记录（id 由数据库重新分配）
     * @param saved   备份中的进行中进度，可为 null
     */
    suspend fun restoreBackup(records: List<GameRecordEntity>, saved: SavedGameEntity?) {
        recordDao.clear()
        if (records.isNotEmpty()) recordDao.insertAll(records)
        savedDao.clear()
        if (saved != null) savedDao.upsert(saved)
    }
}
