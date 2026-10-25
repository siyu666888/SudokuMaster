package com.example.sudokumaster.domain.game

import com.example.sudokumaster.domain.model.WinRecord
import java.util.TimeZone

/**
 * 成绩统计分析（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 覆盖成就页「丰富统计」需求：
 *  - 平均耗时
 *  - 当前 / 最长连续获胜天数（按本地时区的自然日计算）
 *  - 活跃天数
 *  - 各 (尺寸:难度) 聚合（局数 / 最快 / 平均）
 */
object StatsAnalyzer {

    /** 全局概览。 */
    data class Overview(
        val total: Int = 0,
        val avgTimeMs: Long? = null,
        val bestTimeMs: Long? = null,
        val currentStreakDays: Int = 0,
        val longestStreakDays: Int = 0,
        val activeDays: Int = 0
    )

    /** 某 (尺寸:难度) 的聚合成绩。 */
    data class GroupStat(
        val sizeId: String,
        val diffId: Int,
        val count: Int,
        val bestMs: Long?,
        val avgMs: Long?
    )

    /**
     * 汇总统计。
     *
     * @param records 通关记录（无需排序）
     * @param nowMs   「今天」的参考时间（测试可注入）
     */
    fun overview(records: List<WinRecord>, nowMs: Long = System.currentTimeMillis()): Overview {
        if (records.isEmpty()) return Overview()
        val ds = dayStats(records.map { dayNumber(it.date) }, nowMs)
        return Overview(
            total = records.size,
            avgTimeMs = records.map { it.timeMs }.average().toLong(),
            bestTimeMs = records.minOfOrNull { it.timeMs },
            currentStreakDays = ds.currentStreakDays,
            longestStreakDays = ds.longestStreakDays,
            activeDays = ds.activeDays
        )
    }

    /** 按 (尺寸:难度) 聚合，局数多的在前，用于「各难度通关分布」。 */
    fun byDifficulty(records: List<WinRecord>): List<GroupStat> =
        records.groupBy { it.sizeId to it.diffId }
            .map { (key, list) ->
                GroupStat(
                    sizeId = key.first,
                    diffId = key.second,
                    count = list.size,
                    bestMs = list.minOfOrNull { it.timeMs },
                    avgMs = list.map { it.timeMs }.average().toLong()
                )
            }
            .sortedWith(
                compareByDescending<GroupStat> { it.count }
                    .thenBy { it.sizeId }
                    .thenBy { it.diffId }
            )

    /** 只与「自然日」相关的统计：当前 / 最长连续天数、活跃天数。 */
    data class DayStats(
        val currentStreakDays: Int = 0,
        val longestStreakDays: Int = 0,
        val activeDays: Int = 0
    )

    /**
     * 由「出现过的本地自然日序号」推导连续 / 活跃统计。
     *
     * 数据来源已下推 SQL（`GameRecordDao.distinctDays()`，与 [dayNumber] 同口径），
     * 这里只保留纯函数推导逻辑，因此仍可脱离数据库单测。
     *
     * @param days 升序或无序均可，内部会去重排序
     * @param nowMs 「今天」的参考时间（测试可注入）
     */
    fun dayStats(days: List<Long>, nowMs: Long = System.currentTimeMillis()): DayStats {
        val sorted = days.distinct().sorted()
        if (sorted.isEmpty()) return DayStats()
        return DayStats(
            currentStreakDays = currentStreak(sorted, dayNumber(nowMs)),
            longestStreakDays = longestStreak(sorted),
            activeDays = sorted.size
        )
    }

    /**
     * 本地时区「第几天」序号：从 1970-01-01 数起的**本地日序号**。
     *
     * 口径 = `(时刻 + 当地时区偏移) / 86400000`。
     *
     * ⚠️ 这个式子必须与 `GameRecordDao.distinctDays()` 的 SQL **逐值一致**：
     * SQLite 的 `%s` 恒为 UTC 口径，`strftime(..., 'unixepoch', 'localtime', 'start of day')`
     * 拿到的是「本地日期的零点按 UTC 解释」，即本地零点 + 时区偏移，整除后正好等于上式。
     * （曾经这里写成「本地零点毫秒 / 86400000」，对 UTC+8 会整体小 1，与 SQL 对不上，
     * 导致连续天数在两条路径下算出不同结果。）
     *
     * 连续天数只依赖「相邻日相差 1」，绝对数值无所谓；但**两条路径的数值必须相同**。
     */
    fun dayNumber(dateMs: Long): Long =
        (dateMs + TimeZone.getDefault().getOffset(dateMs)) / 86_400_000L

    /** 最长连续：升序去重日列表中最长的相邻日串。 */
    private fun longestStreak(days: List<Long>): Int {
        var best = 0
        var run = 0
        var prev: Long? = null
        for (d in days) {
            run = if (prev != null && d == prev + 1) run + 1 else 1
            if (run > best) best = run
            prev = d
        }
        return best
    }

    /** 当前连续：最近一局在今天或昨天则接力向上数，否则已断档返回 0。 */
    private fun currentStreak(days: List<Long>, today: Long): Int {
        if (days.isEmpty()) return 0
        val latest = days.last()
        if (today - latest > 1) return 0
        var run = 1
        var prev = latest
        for (i in days.size - 2 downTo 0) {
            if (days[i] == prev - 1) {
                run++
                prev = days[i]
            } else break
        }
        return run
    }
}
