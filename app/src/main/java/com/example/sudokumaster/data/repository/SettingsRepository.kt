package com.example.sudokumaster.data.repository

import com.example.sudokumaster.data.SettingsDataStore
import com.example.sudokumaster.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val dataStore: SettingsDataStore) {
    val settings: Flow<AppSettings> = dataStore.settings
    val dailyHintsLeft: Flow<Int> = dataStore.dailyHintsLeft
    suspend fun update(transform: AppSettings.() -> AppSettings) = dataStore.update(transform)
    suspend fun get(): AppSettings = dataStore.get()
    suspend fun consumeHint(): Boolean = dataStore.consumeHint()
}
