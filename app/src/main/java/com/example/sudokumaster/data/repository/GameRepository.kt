package com.example.sudokumaster.data.repository

import com.example.sudokumaster.data.dao.GameRecordDao
import com.example.sudokumaster.data.dao.SavedGameDao
import com.example.sudokumaster.data.entity.GameRecordEntity
import com.example.sudokumaster.data.entity.SavedGameEntity
import com.example.sudokumaster.domain.model.BoardDefinitions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * 当前对局 (saved_game，多槽) + 完成记录 (game_records) 的读写。
 *
 * 多槽：每个 (尺寸:难度) 各有一盘进行中进度，slotId = "$sizeId:$diffId"。
 * 新开局 / 复玩 / 导入若命中已有槽，由调用方先做覆盖确认（F1）。
 */
class GameRepository(
    private val savedDao: SavedGameDao,
    private val recordDao: GameRecordDao
) {
    /** 最近保存的进度（首页「继续游戏」入口）。 */
    fun observeLatestSaved(): Flow<SavedGameEntity?> = savedDao.loadLatest()

    suspend fun getLatestSaved(): SavedGameEntity? = savedDao.loadLatest().first()

    /** 全部进行中进度（多槽，按保存时间倒序）。 */
    fun observeAllSlots(): Flow<List<SavedGameEntity>> = savedDao.allSlots()

    suspend fun getAllSlots(): List<SavedGameEntity> = savedDao.allSlots().first()

    /** 指定 (尺寸:难度) 槽位是否已有进行中进度（未通关）。 */
    suspend fun getActiveSlot(sizeId: String, diffId: Int): SavedGameEntity? =
        savedDao.getBySlot(BoardDefinitions.statsKey(sizeId, diffId))
            ?.takeIf { !it.isWon }

    fun observeSlot(sizeId: String, diffId: Int): Flow<SavedGameEntity?> =
        savedDao.observeSlot(BoardDefinitions.statsKey(sizeId, diffId))

    /** 保存当前进度到对应槽（由快照合成 slotId）。 */
    suspend fun saveSnapshot(snapshot: com.example.sudokumaster.domain.session.GameSnapshot) =
        savedDao.upsert(snapshot.toEntity())

    /** 兼容旧调用的实体保存入口（恢复备份用）。 */
    suspend fun saveCurrent(entity: SavedGameEntity) = savedDao.upsert(entity)

    suspend fun clearSlot(sizeId: String, diffId: Int) =
        savedDao.clearSlot(BoardDefinitions.statsKey(sizeId, diffId))

    suspend fun clearSaved() = savedDao.clear()

    suspend fun recordWin(entity: GameRecordEntity): Long = recordDao.insert(entity)

    suspend fun getRecord(id: Long): GameRecordEntity? = recordDao.getById(id)

    /** 一次性读取全部记录（备份导出用）。 */
    suspend fun allRecordsOnce(): List<GameRecordEntity> = recordDao.all().first()

    /**
     * 恢复备份：清空现有记录与全部进行中进度后整体写入。
     * @param records 备份中的历史记录（id 由数据库重新分配）
     * @param savedGames 备份中的进行中进度（多槽），为空表示无进度
     */
    suspend fun restoreBackup(records: List<GameRecordEntity>, savedGames: List<SavedGameEntity>) {
        recordDao.clear()
        if (records.isNotEmpty()) recordDao.insertAll(records)
        savedDao.clear()
        savedGames.forEach { savedDao.upsert(it) }
    }
}
