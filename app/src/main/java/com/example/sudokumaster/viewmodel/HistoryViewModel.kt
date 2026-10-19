package com.example.sudokumaster.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sudokumaster.data.entity.GameRecordEntity
import com.example.sudokumaster.data.repository.GameRepository
import com.example.sudokumaster.data.repository.StatsRepository
import com.example.sudokumaster.domain.model.BoardDefinitions
import com.example.sudokumaster.domain.model.SizeId
import com.example.sudokumaster.util.fmtTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 历史记录：全量列表 + 搜索 / 尺寸 / 难度三重过滤。
 */
class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val statsRepo = container.statsRepository
    private val gameRepo = container.gameRepository

    /** 有未通关进度的槽位键集合（"sizeId:diffId"），复玩前用于覆盖确认（F1）。 */
    val activeSlotKeys: StateFlow<Set<String>> = gameRepo.observeAllSlots()
        .map { list -> list.filter { !it.isWon }.map { "${it.sizeId}:${it.diffId}" }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sizeFilter = MutableStateFlow<String?>(null)
    val sizeFilter: StateFlow<String?> = _sizeFilter.asStateFlow()

    private val _diffFilter = MutableStateFlow<Int?>(null)
    val diffFilter: StateFlow<Int?> = _diffFilter.asStateFlow()

    private val allRecords = statsRepo.recentRecords(200)

    /** 未筛选时的总局数（决定「从未完成」与「无匹配」两种空状态）。 */
    val totalCount: StateFlow<Int> = allRecords.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** 经搜索 + 尺寸 + 难度过滤后的展示列表。 */
    val records: StateFlow<List<GameRecordEntity>> =
        combine(allRecords, _query, _sizeFilter, _diffFilter) { list, q, size, diff ->
            list.filter { rec ->
                (size == null || rec.sizeId == size) &&
                    (diff == null || rec.diffId == diff) &&
                    matches(rec, q)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setQuery(q: String) { _query.value = q }

    /** 切换尺寸筛选；若当前难度不属于新尺寸则自动清空难度筛选。 */
    fun setSizeFilter(sizeId: String?) {
        _sizeFilter.value = sizeId
        val diff = _diffFilter.value
        if (sizeId != null && diff != null) {
            val ok = BoardDefinitions.get(SizeId.fromId(sizeId)).diffs.any { it.id == diff }
            if (!ok) _diffFilter.value = null
        }
    }

    fun setDiffFilter(diffId: Int?) { _diffFilter.value = diffId }

    fun clearFilters() {
        _query.value = ""
        _sizeFilter.value = null
        _diffFilter.value = null
    }

    private fun matches(rec: GameRecordEntity, q: String): Boolean {
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) return true
        val hay = "${rec.sizeName} ${rec.diffName} ${fmtTime(rec.timeMs)}".lowercase()
        return hay.contains(needle)
    }
}
