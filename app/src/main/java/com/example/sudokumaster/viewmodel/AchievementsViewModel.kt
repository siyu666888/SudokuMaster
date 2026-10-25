package com.example.sudokumaster.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sudokumaster.data.repository.StatsRepository
import com.example.sudokumaster.domain.game.StatsAnalyzer
import com.example.sudokumaster.domain.model.BoardDefinitions
import com.example.sudokumaster.domain.model.SizeId
import com.example.sudokumaster.util.colorHex
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 * 所有派生数据由 SQL 聚合（COUNT / AVG / MIN / MAX / GROUP BY / DISTINCT 自然日）算出，
 * 界面只订阅本状态。早期实现是 `statsRepo.allRecords()` 把整表读进内存再反复遍历，
 * 记录数增长后每次表格变动都要全量重算。
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

    private val overview = statsRepo.overview()
    private val groupStats = statsRepo.groupStats()
    private val dayStats = statsRepo.distinctDays().map { StatsAnalyzer.dayStats(it) }
    private val lastPlayed = statsRepo.lastPlayed()

    val uiState: StateFlow<AchievementsUiState> =
        combine(overview, groupStats, dayStats, lastPlayed) { ov, groups, days, last ->
            // 成就墙：全部 (尺寸:难度) 组合，含未解锁（无记录时补默认值）
            val rows = BoardDefinitions.TYPES.flatMap { bt ->
                bt.diffs.map { d ->
                    val g = groups.firstOrNull { it.sizeId == bt.size.id && it.diffId == d.id }
                    AchRow(
                        sizeId = bt.size.id,
                        diffId = d.id,
                        sizeName = bt.size.label,
                        diffName = d.name,
                        count = g?.cnt ?: 0,
                        best = g?.bestMs,
                        avg = g?.avgMs?.toLong(),
                        // score 为 0 是「旧版本记录，无评分」，不能当成 0 分展示
                        bestScore = g?.maxScore?.takeIf { it > 0 },
                        color = colorHex(d.color)
                    )
                }
            }
            AchievementsUiState(
                total = ov.cnt,
                hellCount = groups.firstOrNull {
                    it.sizeId == SizeId.NINE.id && it.diffId == 5
                }?.cnt ?: 0,
                bestOverall = ov.bestMs,
                lastPlayed = last,
                overview = StatsAnalyzer.Overview(
                    total = ov.cnt,
                    avgTimeMs = ov.avgMs,
                    bestTimeMs = ov.bestMs,
                    currentStreakDays = days.currentStreakDays,
                    longestStreakDays = days.longestStreakDays,
                    activeDays = days.activeDays
                ),
                groupStats = groups
                    .map {
                        StatsAnalyzer.GroupStat(
                            sizeId = it.sizeId,
                            diffId = it.diffId,
                            count = it.cnt,
                            bestMs = it.bestMs,
                            avgMs = it.avgMs?.toLong()
                        )
                    }
                    .sortedWith(
                        compareByDescending<StatsAnalyzer.GroupStat> { it.count }
                            .thenBy { it.sizeId }
                            .thenBy { it.diffId }
                    ),
                rows = rows
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AchievementsUiState())
}
