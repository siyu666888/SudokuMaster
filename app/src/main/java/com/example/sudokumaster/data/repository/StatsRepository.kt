package com.example.sudokumaster.data.repository

import com.example.sudokumaster.data.dao.GameRecordDao
import com.example.sudokumaster.data.dao.RecordGroupRow
import com.example.sudokumaster.data.dao.RecordOverviewRow
import com.example.sudokumaster.data.entity.GameRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 成绩 / 历史记录的聚合查询。
 *
 * 统计类读取一律走 SQL 聚合（COUNT / AVG / MIN / MAX / GROUP BY / DISTINCT），
 * 只把结果集搬进内存；`allRecords()` 仅留给「需要逐行展示或导出」的场景。
 */
class StatsRepository(private val recordDao: GameRecordDao) {

    /** 全部完成记录（按时间倒序）。**慎用**：整表入内存，仅备份导出等场景使用。 */
    fun allRecords(): Flow<List<GameRecordEntity>> = recordDao.all()

    /** 最近 limit 条记录。 */
    fun recentRecords(limit: Int): Flow<List<GameRecordEntity>> = recordDao.recent(limit)

    /** 总条数（COUNT，不随列表截断而变化）。 */
    fun totalCount(): Flow<Int> = recordDao.countAll()

    /** 全局聚合：总局数 / 平均用时 / 最快用时 / 最高得分。 */
    fun overview(): Flow<RecordOverviewRow> = recordDao.overview()

    /** 按 (尺寸:难度) 的聚合列表，仅含有记录的组合。 */
    fun groupStats(): Flow<List<RecordGroupRow>> = recordDao.groupStats()

    /** 出现过的本地自然日序号（升序去重），供连续 / 活跃天数计算。 */
    fun distinctDays(): Flow<List<Long>> = recordDao.distinctDays()

    /** 最近一局时间戳。 */
    fun lastPlayed(): Flow<Long?> = recordDao.lastPlayed()

    /** 指定 (尺寸:难度) 的历史最快用时（无记录时 null）。 */
    suspend fun minTimeMs(sizeId: String, diffId: Int): Long? =
        recordDao.minTimeMs(sizeId, diffId)

    suspend fun clearAll() = recordDao.clear()
}
