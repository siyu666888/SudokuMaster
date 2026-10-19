package com.example.sudokumaster.domain.session

import com.example.sudokumaster.domain.engine.SudokuEngine
import com.example.sudokumaster.domain.model.BoardDefinitions
import com.example.sudokumaster.domain.model.CellState
import com.example.sudokumaster.domain.model.Difficulty
import com.example.sudokumaster.domain.model.GameFlags
import com.example.sudokumaster.domain.model.GameUiState
import com.example.sudokumaster.domain.model.HintMode
import com.example.sudokumaster.domain.model.SizeId
import com.example.sudokumaster.data.entity.SavedGameEntity

/**
 * 一局游戏的可变状态机。对应原 HTML 方案的 `game` 对象与各类交互函数：
 * selectCell / inputNumber / eraseCell / undo / redo / hint / autoNotes / resetGame /
 * checkWin。
 *
 * 所有写操作在内部修改后立即由 [toUiState] 派生不可变快照供 UI 观察。
 * 不依赖 Android 框架，可独立单元测试。
 */
class GameSession private constructor(
    val sizeId: SizeId,
    val difficulty: Difficulty,
    private val initialPuzzle: IntArray,   // 开局（含固定数字，空格为 0）
    val solution: IntArray,                 // 完整解
    internal var flags: GameFlags,
    val isReplay: Boolean,                  // 复玩：通关不计入成绩
    givenOverride: BooleanArray? = null,    // 恢复存档时显式传入固定格标记
    startedAtEpoch: Long = System.currentTimeMillis()
) {
    val n = sizeId.n
    private val boxRows = sizeId.boxRows
    private val boxCols = sizeId.boxCols
    private val total = n * n

    private val values = initialPuzzle.copyOf()
    private val given: BooleanArray =
        givenOverride ?: BooleanArray(total) { initialPuzzle[it] != 0 }
    private val notes = Array(total) { mutableSetOf<Int>() }

    var selected = -1
        private set
    var hintsUsed = 0
        private set
    var mistakes = 0
        private set
    var isNoteMode = false
        private set
    var hintMode: HintMode = HintMode.FILL
    var isWon = false
        private set

    // 计时：累计时长 + 上次继续时间戳（毫秒）
    var accumulatedMs = 0L
        private set
    var lastResume = startedAtEpoch
        private set
    var paused = true
        private set

    private val history = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()

    private data class Snapshot(
        val values: IntArray,
        val notes: List<Set<Int>>,
        val hintsUsed: Int
    )

    init {
        paused = false
        lastResume = System.currentTimeMillis()
    }

    // ---------------------------------------------------------------- 计时
    fun pause() {
        if (!paused && !isWon) {
            accumulatedMs += System.currentTimeMillis() - lastResume
            paused = true
        }
    }

    fun resume() {
        if (paused && !isWon) {
            lastResume = System.currentTimeMillis()
            paused = false
        }
    }

    fun elapsedNow(): Long =
        accumulatedMs + if (paused || isWon) 0L else System.currentTimeMillis() - lastResume

    /** 开局题面（含固定数字，空格为 0）。供成绩记录保存。 */
    fun initialPuzzleArray(): IntArray = initialPuzzle.copyOf()

    // ---------------------------------------------------------------- 选择
    fun select(i: Int) {
        selected = if (i in 0 until total) i else -1
    }

    fun toggleNoteMode() {
        isNoteMode = !isNoteMode
    }

    fun setNoteMode(on: Boolean) {
        isNoteMode = on
    }

    // ---------------------------------------------------------------- 输入
    fun input(digit: Int) {
        if (isWon || selected < 0) return
        if (given[selected]) return
        if (isNoteMode && values[selected] != 0) return   // 已填数字不能记笔记
        pushHistory()
        if (isNoteMode) {
            val set = notes[selected]
            if (set.contains(digit)) set.remove(digit) else set.add(digit)
        } else {
            if (values[selected] == digit) {
                values[selected] = 0
            } else {
                values[selected] = digit
                notes[selected].clear()
                // 与真解不符记一次错误（供评分罚分与结算展示）
                if (digit != solution[selected]) mistakes++
            }
        }
        afterMove()
    }

    fun erase() {
        if (isWon || selected < 0) return
        if (given[selected]) return
        if (values[selected] == 0 && notes[selected].isEmpty()) return
        pushHistory()
        values[selected] = 0
        notes[selected].clear()
        afterMove()
    }

    fun hint(): Boolean {
        if (isWon) return false
        val target = if (selected >= 0 && !given[selected] && values[selected] == 0) selected
        else values.indexOf(0)
        if (target < 0) return false
        pushHistory()
        hintsUsed++
        selected = target
        if (hintMode == HintMode.CANDIDATE) {
            notes[target].clear()
            notes[target].addAll(computeCandidates(target))
            values[target] = 0
        } else {
            values[target] = solution[target]
            notes[target].clear()
        }
        afterMove()
        return true
    }

    /** 一键自动笔记：为所有空格填充候选数。返回变化的格子数。 */
    fun autoNotes(): Int {
        if (isWon) return 0
        var changed = 0
        for (i in 0 until total) {
            if (given[i] || values[i] != 0) continue
            val cand = computeCandidates(i)
            if (cand.isNotEmpty() && cand != notes[i]) {
                notes[i].clear()
                notes[i].addAll(cand)
                changed++
            }
        }
        if (changed > 0) {
            pushHistory()
            afterMove()
        }
        return changed
    }

    fun reset() {
        for (i in 0 until total) {
            values[i] = initialPuzzle[i]
            notes[i].clear()
        }
        history.clear()
        redoStack.clear()
        hintsUsed = 0
        mistakes = 0
        selected = -1
        isWon = false
        accumulatedMs = 0L
        lastResume = System.currentTimeMillis()
        paused = false
    }

    fun canUndo() = history.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    fun undo() {
        if (history.isEmpty()) return
        redoStack.addLast(snapshot())
        restore(history.removeLast())
        afterMove()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        history.addLast(snapshot())
        restore(redoStack.removeLast())
        afterMove()
    }

    // ---------------------------------------------------------------- 内部
    private fun snapshot() = Snapshot(
        values.copyOf(),
        notes.map { it.toSet() },
        hintsUsed
    )

    private fun pushHistory() {
        history.addLast(snapshot())
        if (history.size > 50) history.removeFirst()
        redoStack.clear()
    }

    private fun restore(s: Snapshot) {
        for (i in s.values.indices) values[i] = s.values[i]
        for (i in s.notes.indices) {
            notes[i].clear()
            notes[i].addAll(s.notes[i])
        }
        hintsUsed = s.hintsUsed
    }

    private fun computeCandidates(idx: Int): Set<Int> {
        val r = idx / n
        val c = idx % n
        val used = BooleanArray(n + 1)
        for (i in 0 until n) {
            values[r * n + i].let { if (it != 0) used[it] = true }
            values[i * n + c].let { if (it != 0) used[it] = true }
        }
        val br = (r / boxRows) * boxRows
        val bc = (c / boxCols) * boxCols
        for (i in 0 until boxRows) for (j in 0 until boxCols) {
            val v = values[(br + i) * n + (bc + j)]
            if (v != 0) used[v] = true
        }
        val res = mutableSetOf<Int>()
        for (d in 1..n) if (!used[d]) res.add(d)
        return res
    }

    private fun isConflict(idx: Int, v: Int): Boolean {
        if (v == 0) return false
        val r = idx / n
        val c = idx % n
        for (i in 0 until n) {
            if (i != c && values[r * n + i] == v) return true
            if (i != r && values[i * n + c] == v) return true
        }
        val br = (r / boxRows) * boxRows
        val bc = (c / boxCols) * boxCols
        for (i in 0 until boxRows) for (j in 0 until boxCols) {
            val k = (br + i) * n + (bc + j)
            if (k != idx && values[k] == v) return true
        }
        return false
    }

    private fun afterMove() {
        checkWin()
        if (flags.autoExclude) autoExcludePeers()
    }

    /**
     * 候选数自动排除：对任意已填数字 v，清除其所在行/列/宫同伴的笔记中的 v。
     * 幂等且 O(n²)（n≤9 可忽略），仅在 [GameFlags.autoExclude] 开启时执行。
     */
    private fun autoExcludePeers() {
        for (i in 0 until total) {
            val v = values[i]
            if (v == 0) continue
            val r = i / n
            val c = i % n
            for (k in 0 until n) {
                notes[r * n + k].remove(v)   // 行
                notes[k * n + c].remove(v)   // 列
            }
            val br = (r / boxRows) * boxRows
            val bc = (c / boxCols) * boxCols
            for (bi in 0 until boxRows) for (bj in 0 until boxCols) {
                notes[(br + bi) * n + (bc + bj)].remove(v)   // 宫
            }
        }
    }

    private fun checkWin() {
        if (isWon) return
        for (i in 0 until total) if (values[i] == 0) return
        for (i in 0 until total) if (values[i] != solution[i]) return
        isWon = true
        // 冻结计时：把最后一段（上次 resume 到此刻）计入总时长。
        // 注意不能调用 pause()——其 !isWon 守卫此时会跳过累计，导致结算显示 0。
        if (!paused) {
            accumulatedMs += System.currentTimeMillis() - lastResume
            paused = true
        }
    }

    // ---------------------------------------------------------------- 快照
    fun toUiState(): GameUiState {
        val selVal = if (selected >= 0) values[selected] else 0
        val sr = if (selected >= 0) selected / n else -1
        val sc = if (selected >= 0) selected % n else -1
        val sbr = if (selected >= 0) sr / boxRows else -1
        val sbc = if (selected >= 0) sc / boxCols else -1

        val counts = IntArray(n + 1)
        for (i in 0 until total) if (values[i] != 0) counts[values[i]]++
        val digitCountsLeft = List(n + 1) { n - counts[it] }

        val cells = List(total) { i ->
            val r = i / n
            val c = i % n
            val isPeer = selected >= 0 &&
                (r == sr || c == sc || (r / boxRows == sbr && c / boxCols == sbc)) &&
                i != selected
            val isSame = flags.highlightSame && selVal != 0 && values[i] == selVal
            val isErr = flags.showError && values[i] != 0 && !given[i] && isConflict(i, values[i])
            CellState(
                index = i,
                value = values[i],
                isGiven = given[i],
                notes = notes[i].toSet(),
                isError = isErr,
                isSelected = i == selected,
                isPeer = isPeer,
                isSameValue = isSame
            )
        }

        return GameUiState(
            sizeId = sizeId,
            difficulty = difficulty,
            cells = cells,
            selectedIndex = selected,
            n = n,
            boxRows = boxRows,
            boxCols = boxCols,
            elapsedMs = elapsedNow(),
            hintsUsed = hintsUsed,
            mistakes = mistakes,
            isNoteMode = isNoteMode,
            isWon = isWon,
            isPaused = paused,
            canUndo = history.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            digitCountsLeft = digitCountsLeft,
            title = "${sizeId.label} · ${difficulty.name}"
        )
    }

    // ---------------------------------------------------------------- 持久化
    fun toSavedGameEntity(isPlaying: Boolean, savedAt: Long): SavedGameEntity {
        val valuesStr = values.joinToString("") { (if (it == 0) '0' else '0' + it).toString() }
        val givenStr = given.joinToString("") { if (it) "1" else "0" }
        val notesStr = notes.joinToString(";") { it.joinToString(",") }
        return SavedGameEntity(
            id = 1,
            sizeId = sizeId.id,
            sizeName = sizeId.label,
            diffId = difficulty.id,
            diffName = difficulty.name,
            values = valuesStr,
            given = givenStr,
            notes = notesStr,
            solution = solution.joinToString("") { (if (it == 0) '0' else '0' + it).toString() },
            selected = selected,
            hintsUsed = hintsUsed,
            mistakes = mistakes,
            elapsedMs = elapsedNow(),
            isWon = isWon,
            savedAt = savedAt
        )
    }

    companion object {
        /** 新开局。 */
        fun newGame(
            sizeId: SizeId,
            difficulty: Difficulty,
            puzzle: IntArray,
            solution: IntArray,
            flags: GameFlags
        ): GameSession = GameSession(
            sizeId = sizeId,
            difficulty = difficulty,
            initialPuzzle = puzzle,
            solution = solution,
            flags = flags,
            isReplay = false
        )

        /** 复玩（从历史题面载入，通关不计入成绩）。 */
        fun replay(
            sizeId: SizeId,
            difficulty: Difficulty,
            puzzle: IntArray,
            solution: IntArray,
            flags: GameFlags
        ): GameSession = GameSession(
            sizeId = sizeId,
            difficulty = difficulty,
            initialPuzzle = puzzle,
            solution = solution,
            flags = flags,
            isReplay = true
        )

        /** 从存档恢复。 */
        fun fromSaved(entity: SavedGameEntity): GameSession {
            val sizeId = SizeId.fromId(entity.sizeId)
            val diff = BoardDefinitions.difficulty(sizeId, entity.diffId)
            val n = sizeId.n
            val total = n * n
            val values = IntArray(total) { entity.values.getOrNull(it)?.digitToIntOrNull() ?: 0 }
            val given = BooleanArray(total) { entity.given.getOrNull(it) == '1' }
            val solution = IntArray(total) { entity.solution.getOrNull(it)?.digitToIntOrNull() ?: 0 }
            // 干净题面：仅保留固定格，供 reset() 使用（恢复的是存档时的当前盘面）
            val cleanPuzzle = IntArray(total) { if (given[it]) values[it] else 0 }
            val session = GameSession(
                sizeId = sizeId,
                difficulty = diff,
                initialPuzzle = cleanPuzzle,
                solution = solution,
                flags = GameFlags(),
                isReplay = false,
                givenOverride = given,
                startedAtEpoch = entity.savedAt
            )
            // 还原当前盘面与笔记
            for (i in 0 until total) {
                session.values[i] = values[i]
                entity.notes.split(";").getOrNull(i)?.takeIf { it.isNotEmpty() }?.split(",")
                    ?.mapNotNull { it.toIntOrNull() }?.forEach { session.notes[i].add(it) }
            }
            session.selected = entity.selected
            session.hintsUsed = entity.hintsUsed
            session.mistakes = entity.mistakes
            session.accumulatedMs = entity.elapsedMs
            session.paused = true
            session.isWon = entity.isWon
            return session
        }
    }
}
