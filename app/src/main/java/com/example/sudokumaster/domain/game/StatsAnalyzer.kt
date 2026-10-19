package com.example.sudokumaster.domain.game

import com.example.sudokumaster.domain.model.WinRecord
import java.util.Calendar

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
        val days = records.map { dayNumber(it.date) }.distinct().sorted()
        return Overview(
            total = records.size,
            avgTimeMs = records.map { it.timeMs }.average().toLong(),
            bestTimeMs = records.minOfOrNull { it.timeMs },
            currentStreakDays = currentStreak(days, dayNumber(nowMs)),
            longestStreakDays = longestStreak(days),
            activeDays = days.size
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

    /**
     * 本地时区「第几天」序号（UTC 日起始，毫秒/86400000）。
     * 相差 1 即相邻自然日，用于连续天数判断。
     */
    fun dayNumber(dateMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = dateMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 86_400_000L
    }

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
