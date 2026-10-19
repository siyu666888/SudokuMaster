package com.example.sudokumaster.data.repository

import com.example.sudokumaster.data.dao.GameRecordDao
import com.example.sudokumaster.data.entity.GameRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 成绩 / 历史记录的聚合查询。
 */
class StatsRepository(private val recordDao: GameRecordDao) {

    /** 全部完成记录（按时间倒序）。 */
    fun allRecords(): Flow<List<GameRecordEntity>> = recordDao.all()

    /** 最近 limit 条记录。 */
    fun recentRecords(limit: Int): Flow<List<GameRecordEntity>> = recordDao.recent(limit)

    suspend fun clearAll() = recordDao.clear()
}
