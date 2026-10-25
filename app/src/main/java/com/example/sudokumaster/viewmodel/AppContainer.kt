package com.example.sudokumaster.viewmodel

import android.app.Application
import com.example.sudokumaster.data.SettingsDataStore
import com.example.sudokumaster.data.SudokuDatabase
import com.example.sudokumaster.data.repository.GameRepository
import com.example.sudokumaster.data.repository.SettingsRepository
import com.example.sudokumaster.data.repository.StatsRepository

/**
 * 应用级依赖容器（等价于轻量 DI）。在 [SudokuApplication] 中惰性创建，
 * 供各 ViewModel 工厂使用。
 *
 * @param app 应用上下文
 * @param databaseOverride 指定数据库实例；为 null 时走默认的文件库
 *   [SudokuDatabase.get]。**仅供测试注入内存库**，生产代码不要传。
 */
class AppContainer(
    app: Application,
    databaseOverride: SudokuDatabase? = null
) {
    private val database: SudokuDatabase by lazy { databaseOverride ?: SudokuDatabase.get(app) }
    private val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(app) }

    val gameRepository: GameRepository by lazy {
        GameRepository(database.savedGameDao(), database.recordDao())
    }
    val statsRepository: StatsRepository by lazy {
        StatsRepository(database.recordDao())
    }
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(settingsDataStore)
    }
}
