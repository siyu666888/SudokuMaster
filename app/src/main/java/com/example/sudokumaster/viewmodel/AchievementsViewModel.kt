package com.example.sudokumaster.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sudokumaster.data.repository.StatsRepository
import com.example.sudokumaster.domain.game.StatsAnalyzer
import com.example.sudokumaster.domain.model.BoardDefinitions
import com.example.sudokumaster.domain.model.SizeId
import com.example.sudokumaster.domain.model.WinRecord
import com.example.sudokumaster.util.colorHex
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 成就列表中的一行。 */
data class AchRow(
    val sizeId: String,
    val diffId: Int,
    val sizeName: String,
    val diffName: String,
    val count: Int,
    val best: Long?,
    val avg: Long?,
    val bestScore: Int?,   // 最高得分（旧记录无评分时为 null）
    val color: Color
)

/**
 * 成就页唯一状态源（A3 状态收敛）。
 *
 * 所有派生数据（汇总 / 概览 / 分布 / 成就墙）由同一份 records 一次遍历算出，
 * 界面只订阅本状态，避免多个 StateFlow 各自收集同一 Flow 造成重复计算与重组。
 */
data class AchievementsUiState(
    val total: Int = 0,
    val hellCount: Int = 0,
    val bestOverall: Long? = null,
    val lastPlayed: Long? = null,
    val overview: StatsAnalyzer.Overview = StatsAnalyzer.Overview(),
    val groupStats: List<StatsAnalyzer.GroupStat> = emptyList(),
    val rows: List<AchRow> = emptyList()
)

class AchievementsViewModel(private val container: AppContainer) : ViewModel() {
    private val statsRepo = container.statsRepository

    private val records = statsRepo.allRecords()

    val uiState: StateFlow<AchievementsUiState> = records.map { list ->
        val winRecords = list.map { WinRecord(it.sizeId, it.diffId, it.timeMs, it.date) }
        val overview = StatsAnalyzer.overview(winRecords)
        val groupStats = StatsAnalyzer.byDifficulty(winRecords)
        // 成就墙：全部 (尺寸:难度) 组合，含未解锁
        val rows = BoardDefinitions.TYPES.flatMap { bt ->
            bt.diffs.map { d ->
                val same = list.filter { it.sizeId == bt.size.id && it.diffId == d.id }
                AchRow(
                    sizeId = bt.size.id,
                    diffId = d.id,
                    sizeName = bt.size.label,
                    diffName = d.name,
                    count = same.size,
                    best = same.minOfOrNull { r -> r.timeMs },
                    avg = same.map { r -> r.timeMs }.average().takeIf { same.isNotEmpty() }?.toLong(),
                    bestScore = same.map { r -> r.score }.filter { it > 0 }.maxOrNull(),
                    color = colorHex(d.color)
                )
            }
        }
        AchievementsUiState(
            total = list.size,
            hellCount = list.count { it.sizeId == SizeId.NINE.id && it.diffId == 5 },
            bestOverall = list.minOfOrNull { it.timeMs },
            lastPlayed = list.firstOrNull()?.date,
            overview = overview,
            groupStats = groupStats,
            rows = rows
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AchievementsUiState())
}
