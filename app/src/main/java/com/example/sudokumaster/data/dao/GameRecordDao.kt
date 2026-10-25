package com.example.sudokumaster.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.sudokumaster.data.entity.GameRecordEntity
import kotlinx.coroutines.flow.Flow

/** 全局聚合行：一条记录概括整张表。空表时 count 为 0、其余为 null。 */
data class RecordOverviewRow(
    val cnt: Int,
    val avgMs: Long?,
    val bestMs: Long?,
    val maxScore: Int?
)

/** 按 (尺寸:难度) 聚合的一行。只返回**有记录**的组合，未解锁组合由 UI 侧补默认值。 */
data class RecordGroupRow(
    val sizeId: String,
    val diffId: Int,
    val cnt: Int,
    val bestMs: Long?,
    val avgMs: Double?,
    val maxScore: Int?
)

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

    /**
     * 总条数。
     *
     * ⚠️ 这是**不带 LIMIT 的 COUNT(\*)**。历史页用它区分「从未完成」与「无匹配结果」
     * 两种空状态；早期版本误用了 `recent(200).map { it.size }`，导致记录数超过 200 后
     * 总数恒显示 200（见 HistoryViewModel）。要条数就老老实实 COUNT。
     */
    @Query("SELECT COUNT(*) FROM game_records")
    fun countAll(): Flow<Int>

    /** 全局聚合：总局数 / 平均用时 / 最快用时 / 最高得分。 */
    @Query(
        "SELECT COUNT(*) AS cnt, AVG(timeMs) AS avgMs, MIN(timeMs) AS bestMs, MAX(score) AS maxScore " +
            "FROM game_records"
    )
    fun overview(): Flow<RecordOverviewRow>

    /** 按 (尺寸:难度) 聚合：局数 / 最快 / 平均 / 最高得分。 */
    @Query(
        "SELECT sizeId AS sizeId, diffId AS diffId, COUNT(*) AS cnt, " +
            "MIN(timeMs) AS bestMs, AVG(timeMs) AS avgMs, MAX(score) AS maxScore " +
            "FROM game_records GROUP BY sizeId, diffId"
    )
    fun groupStats(): Flow<List<RecordGroupRow>>

    /**
     * 出现过的「本地自然日」序号，升序去重。
     *
     * ⚠️ 口径必须与 `StatsAnalyzer.dayNumber` **完全一致**：后者是
     * 「本地零点毫秒 / 86400000」的**整数除法**结果。注意这个值并不是「从 1970 数起的
     * 第几个本地日」——对 UTC+8 这类非整天偏移的时区，它比本地日序号小 1。
     * 两者只要相对差为 1（相邻日）就能算对连续天数，但**数值必须逐值相同**，
     * 否则连续 / 活跃天数会静默算错。
     *
     * 因此 SQL 侧也要走到「本地零点」再整除，而不是直接 `epoch / 86400`：
     * 用 `strftime(..., 'unixepoch', 'localtime', 'start of day')` 取本地零点的
     * unix 秒，再 `/ 86400` 做整数除法。
     */
    @Query(
        "SELECT DISTINCT CAST(strftime('%s', `date` / 1000, 'unixepoch', 'localtime', 'start of day') AS INTEGER) / 86400 AS day " +
            "FROM game_records ORDER BY day"
    )
    fun distinctDays(): Flow<List<Long>>

    /** 最近一局的时间戳（无记录时 null）。 */
    @Query("SELECT MAX(date) FROM game_records")
    fun lastPlayed(): Flow<Long?>

    /** 指定 (尺寸:难度) 的历史最快用时（无记录时返回 null）。 */
    @Query("SELECT MIN(timeMs) FROM game_records WHERE sizeId = :sizeId AND diffId = :diffId")
    suspend fun minTimeMs(sizeId: String, diffId: Int): Long?

    @Insert
    suspend fun insertAll(records: List<GameRecordEntity>)

    @Query("DELETE FROM game_records")
    suspend fun clear()
}
