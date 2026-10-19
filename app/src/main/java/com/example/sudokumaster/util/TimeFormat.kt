package com.example.sudokumaster.util

import com.example.sudokumaster.domain.game.StatsAnalyzer
import java.util.Calendar

/** 时长格式化为 mm:ss（与原 HTML 方案一致）。 */
fun fmtTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val s = ms / 1000
    val mm = s / 60
    val ss = s % 60
    return "%02d:%02d".format(mm, ss)
}

/**
 * 历史分组标题：今天 / 昨天 / M月d日 / yyyy年M月d日（同年省略年份）。
 * @param dateMs 记录完成时间
 * @param nowMs  「今天」的参考时间
 */
fun historyDateLabel(dateMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
    val diffDays = StatsAnalyzer.dayNumber(nowMs) - StatsAnalyzer.dayNumber(dateMs)
    return when (diffDays) {
        0L -> "今天"
        1L -> "昨天"
        else -> {
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val nowYear = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.YEAR)
            if (cal.get(Calendar.YEAR) == nowYear) "${month}月${day}日"
            else "${cal.get(Calendar.YEAR)}年${month}月${day}日"
        }
    }
}
