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

    /**
     * 展示用全量列表（按时间倒序）。
     *
     * ⚠️ 早期版本这里是 `statsRepo.recentRecords(200)`，而 `totalCount` 又由
     * `allRecords.map { it.size }` 得出 —— 记录数一过 200，**列表只剩最新 200 条、
     * 「总局数」恒显示 200、且搜索只能命中这 200 条**，界面无任何提示。
     * 现在列表走全表（配合 LazyColumn 只渲染可见项），条数改由 SQL `COUNT(*)` 单独回答。
     */
    private val allRecords = statsRepo.allRecords()

    /**
     * 未筛选时的总局数（决定「从未完成」与「无匹配」两种空状态）。
     *
     * 必须走 [StatsRepository.totalCount]（SQL COUNT），**不能**用列表 size 推导，
     * 否则任何对列表的限流都会顺手把这个数字一起限掉。
     */
    val totalCount: StateFlow<Int> = statsRepo.totalCount()
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
