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

    val savedGame: StateFlow<SavedGameEntity?> = gameRepo.observeSaved()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val statsMap: StateFlow<Map<String, StatsTuple>> = statsRepo.allRecords()
        .map { list ->
            list.groupBy { "${it.sizeId}:${it.diffId}" }
                .mapValues { (_, v) -> StatsTuple(v.size, v.minOf { r -> r.timeMs }) }
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
