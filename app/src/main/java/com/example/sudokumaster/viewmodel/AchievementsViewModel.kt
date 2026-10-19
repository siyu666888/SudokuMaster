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

class AchievementsViewModel(private val container: AppContainer) : ViewModel() {
    private val statsRepo = container.statsRepository

    private val records = statsRepo.allRecords()

    val totalCount: StateFlow<Int> = records.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hellCount: StateFlow<Int> = records.map { list ->
        list.count { it.sizeId == SizeId.NINE.id && it.diffId == 5 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val bestOverall: StateFlow<Long?> = records.map { list -> list.minOfOrNull { it.timeMs } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 最近一局完成时间（空列表为 null）。 */
    val lastPlayed: StateFlow<Long?> = records.map { list -> list.firstOrNull()?.date }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 丰富统计：平均耗时 / 连续获胜天数 / 活跃天数等。 */
    val overview: StateFlow<StatsAnalyzer.Overview> = records
        .map { list -> StatsAnalyzer.overview(list.map { WinRecord(it.sizeId, it.diffId, it.timeMs, it.date) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsAnalyzer.Overview())

    /** 各难度通关分布（仅含有记录的难度，局数降序）。 */
    val groupStats: StateFlow<List<StatsAnalyzer.GroupStat>> = records
        .map { list -> StatsAnalyzer.byDifficulty(list.map { WinRecord(it.sizeId, it.diffId, it.timeMs, it.date) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 成就墙：全部 (尺寸:难度) 组合，含未解锁。 */
    val rows: StateFlow<List<AchRow>> = records.map { list ->
        BoardDefinitions.TYPES.flatMap { bt ->
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
