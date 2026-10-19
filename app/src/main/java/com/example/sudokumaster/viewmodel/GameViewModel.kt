package com.example.sudokumaster.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sudokumaster.data.entity.GameRecordEntity
import com.example.sudokumaster.data.repository.GameRepository
import com.example.sudokumaster.data.repository.SettingsRepository
import com.example.sudokumaster.data.repository.StatsRepository
import com.example.sudokumaster.data.repository.toSnapshot
import com.example.sudokumaster.domain.engine.BoardConfig
import com.example.sudokumaster.domain.engine.SudokuEngine
import com.example.sudokumaster.domain.game.ScoreCalculator
import com.example.sudokumaster.domain.model.AppSettings
import com.example.sudokumaster.domain.model.BoardDefinitions
import com.example.sudokumaster.domain.model.Difficulty
import com.example.sudokumaster.domain.model.GameFlags
import com.example.sudokumaster.domain.model.SizeId
import com.example.sudokumaster.domain.model.WinInfo
import com.example.sudokumaster.domain.session.GameSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

class GameViewModel(private val container: AppContainer) : ViewModel() {

    private val gameRepo = container.gameRepository
    private val statsRepo = container.statsRepository
    private val settingsRepo = container.settingsRepository

    private val _uiState = MutableStateFlow<com.example.sudokumaster.domain.model.GameUiState?>(null)
    val uiState: StateFlow<com.example.sudokumaster.domain.model.GameUiState?> = _uiState.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _winInfo = MutableStateFlow<WinInfo?>(null)
    val winInfo: StateFlow<WinInfo?> = _winInfo.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    val settingsFlow: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    /** 今日剩余提示次数（每日重置）。 */
    val hintsLeft: StateFlow<Int> = settingsRepo.dailyHintsLeft
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.sudokumaster.data.DAILY_HINT_LIMIT)

    private var session: GameSession? = null
    private var settings: AppSettings = AppSettings()

    init {
        viewModelScope.launch {
            settingsRepo.settings.collect { s ->
                settings = s
                session?.let { sess ->
                    sess.flags = GameFlags(s.showError, s.highlightSame, s.autoExclude)
                    sess.hintMode = s.hintMode
                }
                reEmit()
            }
        }
    }

    // ---------------------------------------------------------- 开局
    fun startNew(sizeId: SizeId, diffId: Int) {
        val diff = BoardDefinitions.difficulty(sizeId, diffId)
        generate(sizeId, diff) { puzzle ->
            GameSession.newGame(
                sizeId = sizeId,
                difficulty = diff,
                puzzle = puzzle.puzzle,
                solution = puzzle.solution,
                flags = GameFlags(settings.showError, settings.highlightSame, settings.autoExclude)
            ).apply { hintMode = settings.hintMode }
        }
    }

    fun replayFromRecord(recordId: Long) {
        viewModelScope.launch {
            _isGenerating.value = true
            val rec = gameRepo.getRecord(recordId) ?: run {
                _isGenerating.value = false
                _toast.value = "记录不存在"
                return@launch
            }
            val sizeId = SizeId.fromId(rec.sizeId)
            val diff = BoardDefinitions.difficulty(sizeId, rec.diffId)
            val n = sizeId.n
            val puzzle = IntArray(n * n) { rec.puzzle.getOrNull(it)?.digitToIntOrNull() ?: 0 }
            val solution = IntArray(n * n) { rec.solution.getOrNull(it)?.digitToIntOrNull() ?: 0 }
            session = GameSession.replay(sizeId, diff, puzzle, solution, GameFlags(settings.showError, settings.highlightSame, settings.autoExclude)).apply {
                hintMode = settings.hintMode
                resume()
            }
            _isGenerating.value = false
            reEmit()
            saveCurrent()
        }
    }

    fun resumeFromSaved(slotId: String? = null) {
        viewModelScope.launch {
            val entity = if (slotId != null) {
                val sid = slotId.substringBefore(':')
                val did = slotId.substringAfter(':').toIntOrNull() ?: 1
                gameRepo.observeSlot(sid, did).first()
            } else {
                gameRepo.getLatestSaved()
            }
            if (entity == null) {
                _toast.value = "没有进行中的对局"
                return@launch
            }
            session = GameSession.fromSnapshot(entity.toSnapshot()).apply {
                flags = GameFlags(settings.showError, settings.highlightSame, settings.autoExclude)
                hintMode = settings.hintMode
                if (!isWon) resume()
            }
            reEmit()
        }
    }

    /**
     * 导入题目开局：优先采用 JSON 自带解；缺失或与题面冲突时自动求解。
     * 通关计入成绩（与普通新局一致）。
     */
    fun startImported(sizeId: SizeId, diffId: Int, puzzleStr: String, solutionStr: String?) {
        viewModelScope.launch {
            _isGenerating.value = true
            val n = sizeId.n
            val puzzle = IntArray(n * n) { puzzleStr.getOrNull(it)?.digitToIntOrNull() ?: 0 }
            if (puzzle.count { it != 0 } == 0) {
                _isGenerating.value = false
                _toast.value = "题面无有效数字"
                return@launch
            }
            val diff = BoardDefinitions.difficultyOrFirst(sizeId, diffId)
            val provided = solutionStr?.takeIf { it.length >= n * n }?.let { s ->
                IntArray(n * n) { s.getOrNull(it)?.digitToIntOrNull() ?: 0 }
            }
            val consistent = provided != null &&
                provided.none { it == 0 || it > n } &&
                puzzle.indices.none { puzzle[it] != 0 && puzzle[it] != provided[it] }
            val solution = if (consistent) provided else withContext(Dispatchers.Default) {
                SudokuEngine.solve(BoardConfig(n, sizeId.boxRows, sizeId.boxCols), puzzle)
            }
            if (solution == null) {
                _isGenerating.value = false
                _toast.value = "题目无解，无法开始"
                return@launch
            }
            session = GameSession.newGame(
                sizeId = sizeId,
                difficulty = diff,
                puzzle = puzzle,
                solution = solution,
                flags = GameFlags(settings.showError, settings.highlightSame, settings.autoExclude)
            ).apply { hintMode = settings.hintMode }
            _isGenerating.value = false
            reEmit()
            session?.resume()
            saveCurrent()
        }
    }

    private fun generate(sizeId: SizeId, diff: Difficulty, builder: (com.example.sudokumaster.domain.engine.Puzzle) -> GameSession) {
        viewModelScope.launch {
            _isGenerating.value = true
            val config = BoardConfig(sizeId.n, sizeId.boxRows, sizeId.boxCols)
            val clues = if (settings.fastGen) {
                diff.clues + max(5, (diff.clues * 0.2).roundToInt())
            } else diff.clues
            val puzzle = withContext(Dispatchers.Default) {
                SudokuEngine.generatePuzzle(config, clues)
            }
            session = builder(puzzle)
            _isGenerating.value = false
            reEmit()
            session?.resume()
            saveCurrent()
        }
    }

    // ---------------------------------------------------------- 交互
    fun select(i: Int) { session?.select(i); reEmit() }
    fun input(d: Int) { session?.input(d); afterAction() }
    fun erase() { session?.erase(); afterAction() }
    fun toggleNote() { session?.toggleNoteMode(); reEmit() }
    fun setNoteMode(on: Boolean) { session?.setNoteMode(on); reEmit() }
    fun undo() { session?.undo(); reEmit() }
    fun redo() { session?.redo(); reEmit() }
    fun reset() { session?.reset(); reEmit(); saveCurrent() }

    fun hint() {
        if (hintsLeft.value <= 0) {
            _toast.value = "今日提示次数已用完"
            return
        }
        if (session?.hint() == true) {
            viewModelScope.launch { settingsRepo.consumeHint() }
            afterAction()
        } else _toast.value = "棋盘已填满"
    }

    fun autoNotes() {
        val changed = session?.autoNotes() ?: 0
        afterAction()
        _toast.value = if (changed > 0) "已为 $changed 格填入候选" else "没有可填充的空格"
    }

    /** UI 定时刷新（仅更新已用时间显示，不触发逻辑）。 */
    fun refreshTimer() = reEmit()

    fun pause() { session?.pause() }
    fun resumeTimer() { session?.resume() }

    /** 手动暂停 / 继续（对局内暂停按钮）。 */
    fun togglePause() {
        session?.let { s ->
            if (s.paused) s.resume() else s.pause()
            reEmit()
            saveCurrent()
        }
    }

    fun saveCurrent() {
        session?.let { s ->
            viewModelScope.launch {
                gameRepo.saveSnapshot(s.toSnapshot(System.currentTimeMillis()))
            }
        }
    }

    fun dismissWin() { _winInfo.value = null }
    fun consumeToast() { _toast.value = null }

    fun exitToHome() { saveCurrent() }

    // ---------------------------------------------------------- 内部
    private fun reEmit() {
        session?.let { _uiState.value = it.toUiState() }
    }

    private fun afterAction() {
        val s = session ?: return
        reEmit()
        if (s.isWon) handleWin(s)
    }

    private fun handleWin(s: GameSession) {
        viewModelScope.launch {
            val puzzleStr = s.initialPuzzleArray().joinToString("") { (if (it == 0) '0' else '0' + it).toString() }
            val solutionStr = s.solution.joinToString("") { (if (it == 0) '0' else '0' + it).toString() }
            val ms = s.elapsedNow()
            val result = ScoreCalculator.calculate(
                sizeId = s.sizeId,
                difficulty = s.difficulty,
                elapsedMs = ms,
                mistakes = s.mistakes,
                hintsUsed = s.hintsUsed
            )
            if (!s.isReplay) {
                gameRepo.recordWin(
                    GameRecordEntity(
                        sizeId = s.sizeId.id,
                        sizeName = s.sizeId.label,
                        diffId = s.difficulty.id,
                        diffName = s.difficulty.name,
                        timeMs = ms,
                        date = System.currentTimeMillis(),
                        puzzle = puzzleStr,
                        solution = solutionStr,
                        score = result.score,
                        stars = result.stars
                    )
                )
            }
            // 只清除本难度这一槽，其他尺寸/难度的进行中进度保留（F1 多槽）
            gameRepo.clearSlot(s.sizeId.id, s.difficulty.id)

            // 最快用时由 SQL 聚合下推完成（P2），避免把全表读进内存再 minOf
            val best = statsRepo.minTimeMs(s.sizeId.id, s.difficulty.id)
            _winInfo.value = WinInfo(
                sizeId = s.sizeId,
                diffId = s.difficulty.id,
                diffName = s.difficulty.name,
                timeMs = ms,
                isBest = best == ms,
                bestMs = best ?: ms,
                levelName = "${s.sizeId.label} · ${s.difficulty.name}",
                score = result.score,
                stars = result.stars,
                rank = result.rank,
                mistakes = s.mistakes,
                hintsUsed = s.hintsUsed
            )
        }
    }
}
