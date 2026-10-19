package com.example.sudokumaster.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sudokumaster.data.entity.GameRecordEntity
import com.example.sudokumaster.data.entity.SavedGameEntity
import com.example.sudokumaster.data.repository.GameRepository
import com.example.sudokumaster.data.repository.SettingsRepository
import com.example.sudokumaster.data.repository.StatsRepository
import com.example.sudokumaster.domain.model.AppSettings
import com.example.sudokumaster.domain.model.BoardDefinitions
import com.example.sudokumaster.domain.model.SizeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 解析后的待导入题目。 */
data class ImportedPuzzle(
    val sizeId: String,
    val diffId: Int,
    val puzzle: String,
    val solution: String?   // 缺失或与题面冲突时由 GameViewModel 自动求解
)

/** 全量备份结果（历史记录 + 进行中进度）。 */
data class BackupResult(
    val json: String,
    val recordCount: Int,
    val hasSavedGame: Boolean
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val settingsRepo = container.settingsRepository
    private val statsRepo = container.statsRepository
    private val gameRepo = container.gameRepository

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun update(transform: AppSettings.() -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            statsRepo.clearAll()
            gameRepo.clearSaved()
        }
    }

    /** 导出最近一条记录为 JSON 字符串（无则返回 null）。 */
    suspend fun exportJson(): String? {
        val rec = statsRepo.recentRecords(1).first().firstOrNull() ?: return null
        return buildString {
            append("{")
            append("\"app\":\"数独大师\",")
            append("\"version\":\"1.0\",")
            append("\"size\":\"${rec.sizeId}\",")
            append("\"sizeName\":\"${rec.sizeName}\",")
            append("\"difficulty\":\"${rec.diffName}\",")
            append("\"puzzle\":\"${rec.puzzle}\",")
            append("\"solution\":\"${rec.solution}\"")
            append("}")
        }
    }

    // ------------------------------------------------------------ 导入题目

    /**
     * 解析题目 JSON（导入题目）。字段不合法 / 题面无有效数字时返回 null。
     * 兼容「导出题目」产出的格式：size / difficulty / puzzle / solution。
     */
    suspend fun parsePuzzleImport(json: String): ImportedPuzzle? = withContext(Dispatchers.Default) {
        try {
            val obj = JSONObject(json)
            val size = SizeId.fromId(obj.optString("size").ifBlank { "9" })
            val n = size.n
            val puzzle = obj.optString("puzzle").filter { it.isDigit() }
            if (puzzle.length != n * n) return@withContext null
            if (puzzle.any { it - '0' !in 0..n }) return@withContext null
            if (puzzle.count { it != '0' } == 0) return@withContext null
            val solStr = obj.optString("solution").filter { it.isDigit() }
                .takeIf { it.length == n * n && it.all { c -> c - '0' in 1..n } }
            // 与题面给定时冲突的解不可信，弃用并交由自动求解
            val solution = solStr?.takeIf { s ->
                puzzle.indices.none { puzzle[it] != '0' && puzzle[it] != s[it] }
            }
            val diff = BoardDefinitions.difficultyByName(size, obj.optString("difficulty").ifBlank { null })
            ImportedPuzzle(size.id, diff.id, puzzle, solution)
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------ 备份 / 恢复

    /** 导出全量备份（历史记录 + 进行中进度）为 JSON。 */
    suspend fun exportBackup(): BackupResult = withContext(Dispatchers.Default) {
        val records = gameRepo.allRecordsOnce()
        val saved = gameRepo.getSaved()
        BackupResult(
            json = buildBackupJson(records, saved),
            recordCount = records.size,
            hasSavedGame = saved != null
        )
    }

    /** 从备份 JSON 恢复（覆盖现有记录与进行中进度）。返回结果文案。 */
    suspend fun importBackup(json: String): String = withContext(Dispatchers.Default) {
        try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("records") ?: org.json.JSONArray()
            val records = mutableListOf<GameRecordEntity>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val size = SizeId.fromId(o.optString("sizeId"))
                val n = size.n
                val puzzle = o.optString("puzzle").filter { it.isDigit() }
                val solution = o.optString("solution").filter { it.isDigit() }
                if (puzzle.length != n * n || solution.length != n * n) continue
                val diffId = o.optInt("diffId", 1)
                if (BoardDefinitions.get(size).diffs.none { it.id == diffId }) continue
                records += GameRecordEntity(
                    sizeId = size.id,
                    sizeName = size.label,
                    diffId = diffId,
                    diffName = BoardDefinitions.difficultyOrFirst(size, diffId).name,
                    timeMs = o.optLong("timeMs"),
                    date = o.optLong("date"),
                    puzzle = puzzle,
                    solution = solution,
                    score = o.optInt("score", 0),
                    stars = o.optInt("stars", 0)
                )
            }
            val saved = parseSavedGame(obj.optJSONObject("savedGame"))
            gameRepo.restoreBackup(records, saved)
            buildString {
                append("已恢复 ${records.size} 条记录")
                if (saved != null) append("与进行中进度")
            }
        } catch (_: Exception) {
            "备份数据解析失败，请检查 JSON 内容"
        }
    }

    private fun parseSavedGame(o: JSONObject?): SavedGameEntity? {
        if (o == null) return null
        return try {
            val size = SizeId.fromId(o.optString("sizeId"))
            val n = size.n
            val values = o.optString("values")
            val given = o.optString("given")
            val solution = o.optString("solution")
            if (values.length != n * n || given.length != n * n || solution.length != n * n) return null
            val diffId = o.optInt("diffId", 1)
            if (BoardDefinitions.get(size).diffs.none { it.id == diffId }) return null
            SavedGameEntity(
                sizeId = size.id,
                sizeName = size.label,
                diffId = diffId,
                diffName = BoardDefinitions.difficultyOrFirst(size, diffId).name,
                values = values,
                given = given,
                notes = o.optString("notes"),
                solution = solution,
                selected = o.optInt("selected", -1),
                hintsUsed = o.optInt("hintsUsed", 0),
                mistakes = o.optInt("mistakes", 0),
                elapsedMs = o.optLong("elapsedMs", 0L),
                isWon = o.optBoolean("isWon", false),
                savedAt = o.optLong("savedAt", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun buildBackupJson(records: List<GameRecordEntity>, saved: SavedGameEntity?): String =
        buildString {
            append("{")
            append("\"app\":\"数独大师\",")
            append("\"version\":\"1.0\",")
            append("\"type\":\"backup\",")
            append("\"exportedAt\":${System.currentTimeMillis()},")
            append("\"records\":[")
            records.forEachIndexed { i, r ->
                if (i > 0) append(",")
                append("{")
                append("\"sizeId\":\"${r.sizeId}\",")
                append("\"sizeName\":\"${r.sizeName}\",")
                append("\"diffId\":${r.diffId},")
                append("\"diffName\":\"${r.diffName}\",")
                append("\"timeMs\":${r.timeMs},")
                append("\"date\":${r.date},")
                append("\"puzzle\":\"${r.puzzle}\",")
                append("\"solution\":\"${r.solution}\",")
                append("\"score\":${r.score},")
                append("\"stars\":${r.stars}")
                append("}")
            }
            append("],")
            append("\"savedGame\":")
            if (saved != null) {
                append("{")
                append("\"sizeId\":\"${saved.sizeId}\",")
                append("\"sizeName\":\"${saved.sizeName}\",")
                append("\"diffId\":${saved.diffId},")
                append("\"diffName\":\"${saved.diffName}\",")
                append("\"values\":\"${saved.values}\",")
                append("\"given\":\"${saved.given}\",")
                append("\"notes\":\"${saved.notes}\",")
                append("\"solution\":\"${saved.solution}\",")
                append("\"selected\":${saved.selected},")
                append("\"hintsUsed\":${saved.hintsUsed},")
                append("\"mistakes\":${saved.mistakes},")
                append("\"elapsedMs\":${saved.elapsedMs},")
                append("\"isWon\":${saved.isWon},")
                append("\"savedAt\":${saved.savedAt}")
                append("}")
            } else append("null")
            append("}")
        }
}
