package com.example.sudokumaster.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sudokumaster.data.entity.SavedGameEntity
import com.example.sudokumaster.data.repository.GameRepository
import com.example.sudokumaster.data.repository.SettingsRepository
import com.example.sudokumaster.data.repository.StatsRepository
import com.example.sudokumaster.domain.model.AppSettings
import com.example.sudokumaster.domain.model.SizeId
import com.example.sudokumaster.domain.model.StatsTuple
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val gameRepo = container.gameRepository
    private val statsRepo = container.statsRepository
    private val settingsRepo = container.settingsRepository

    /** 最近保存的进度（首页「继续游戏」卡片；多槽时取 savedAt 最大者）。 */
    val savedGame: StateFlow<SavedGameEntity?> = gameRepo.observeLatestSaved()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 有未通关进度的槽位键集合（"sizeId:diffId"），用于新开局前的覆盖确认（F1）。 */
    val activeSlotKeys: StateFlow<Set<String>> = gameRepo.observeAllSlots()
        .map { list -> list.filter { !it.isWon }.map { "${it.sizeId}:${it.diffId}" }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    /**
     * 各 (尺寸:难度) 的「通关数 + 最快用时」，由 SQL `GROUP BY` 一次算完。
     *
     * 早期实现是 `statsRepo.allRecords()` 全表拉回再 Kotlin 侧 groupBy —— 记录一多，
     * 每次表格变动都要重算整张表。现在只搬回 10 行以内的聚合结果。
     */
    val statsMap: StateFlow<Map<String, StatsTuple>> = statsRepo.groupStats()
        .map { rows ->
            rows.associate { r ->
                "${r.sizeId}:${r.diffId}" to StatsTuple(r.cnt, r.bestMs)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val selectedSize: SizeId
        get() = SizeId.fromId(settings.value.selectedSizeId)

    fun selectSize(sizeId: SizeId) {
        viewModelScope.launch {
            settingsRepo.update { copy(selectedSizeId = sizeId.id) }
        }
    }
}
