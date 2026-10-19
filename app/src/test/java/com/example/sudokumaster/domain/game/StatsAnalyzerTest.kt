package com.example.sudokumaster.domain.game

import com.example.sudokumaster.domain.model.WinRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StatsAnalyzerTest {

    private fun at(y: Int, m: Int, d: Int, hour: Int = 12): Long {
        val cal = Calendar.getInstance()
        cal.set(y, m - 1, d, hour, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private val DAY = 86_400_000L

    @Test
    fun emptyRecords_yieldsDefaultOverview() {
        val o = StatsAnalyzer.overview(emptyList())
        assertEquals(0, o.total)
        assertEquals(0, o.currentStreakDays)
        assertEquals(0, o.longestStreakDays)
        assertNull(o.avgTimeMs)
        assertNull(o.bestTimeMs)
    }

    @Test
    fun dayNumber_sameDayEqual_consecutiveDaysDifferByOne() {
        assertEquals(StatsAnalyzer.dayNumber(at(2026, 1, 31)), StatsAnalyzer.dayNumber(at(2026, 1, 31, 23)))
        // 跨月边界：1/31 与 2/1 仍相差 1 个自然日
        assertEquals(
            StatsAnalyzer.dayNumber(at(2026, 1, 31)) + 1,
            StatsAnalyzer.dayNumber(at(2026, 2, 1))
        )
    }

    @Test
    fun streaks_unbrokenRecentStreak() {
        // 连续 4 天，今天就是最后一天
        val base = at(2026, 3, 1)
        val records = (0..3).map { WinRecord("9", 1, 100_000L, base + it * DAY) }
        val o = StatsAnalyzer.overview(records, nowMs = base + 3 * DAY)
        assertEquals(4, o.total)
        assertEquals(4, o.longestStreakDays)
        assertEquals(4, o.currentStreakDays)
        assertEquals(4, o.activeDays)
    }

    @Test
    fun streaks_gapBreaksCurrentButKeepsLongest() {
        // 日序：b, b+1, b+2, b+4（缺 b+3）
        val base = at(2026, 3, 1)
        val records = listOf(
            WinRecord("9", 1, 100_000L, base),
            WinRecord("9", 1, 100_000L, base + DAY),
            WinRecord("9", 1, 100_000L, base + 2 * DAY),
            WinRecord("9", 1, 100_000L, base + 4 * DAY)
        )
        val o = StatsAnalyzer.overview(records, nowMs = base + 4 * DAY)
        assertEquals(3, o.longestStreakDays)   // 0,1,2 连续
        assertEquals(1, o.currentStreakDays)    // 最近一段只有 b+4 自己
    }

    @Test
    fun streaks_staleLastWin_returnsZero() {
        // 最后胜利在两天前 → 已断档
        val base = at(2026, 3, 1)
        val records = listOf(
            WinRecord("9", 1, 100_000L, base),
            WinRecord("9", 1, 100_000L, base + DAY)
        )
        val o = StatsAnalyzer.overview(records, nowMs = base + 3 * DAY)
        assertEquals(0, o.currentStreakDays)
        assertEquals(2, o.longestStreakDays)
    }

    @Test
    fun byDifficulty_groupsAndSortsByCount() {
        val records = listOf(
            WinRecord("9", 1, 100_000L, at(2026, 3, 1)),
            WinRecord("9", 1, 120_000L, at(2026, 3, 2)),
            WinRecord("9", 3, 200_000L, at(2026, 3, 1)),
            WinRecord("4", 1, 50_000L, at(2026, 3, 1))
        )
        val groups = StatsAnalyzer.byDifficulty(records)
        assertEquals(3, groups.size)
        // 局数最多者排前：9:1 有 2 局
        assertEquals("9", groups.first().sizeId)
        assertEquals(1, groups.first().diffId)
        assertEquals(2, groups.first().count)
        // 各组的 best/avg 合理
        assertTrue(groups.all { it.bestMs != null && it.avgMs != null })
    }
}
