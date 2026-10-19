package com.example.sudokumaster.domain.session

import com.example.sudokumaster.domain.model.BoardDefinitions
import com.example.sudokumaster.domain.model.GameFlags
import com.example.sudokumaster.domain.model.SizeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1：计时逻辑通过注入 clock 控制，不依赖真实系统时间，保证可测、可重放。
 */
class GameSessionClockTest {

    private val size = SizeId.FOUR
    private val diff = BoardDefinitions.difficulty(size, 1)
    private val solution = intArrayOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 1, 4, 3, 4, 3, 2, 1)
    private val puzzle = intArrayOf(1, 0, 0, 4, 0, 4, 0, 2, 0, 0, 4, 0, 4, 0, 0, 1)

    @Test
    fun pauseResume_accumulatesElapsedFromInjectedClock() {
        var now = 1_000L
        val s = GameSession.newGame(size, diff, puzzle, solution, GameFlags()) { now }
        assertEquals(0L, s.elapsedNow())

        now = 5_000L
        s.pause()                       // 累计 4_000
        assertEquals(4_000L, s.elapsedNow())

        now = 9_000L
        s.resume()                      // 暂停期间不计时
        assertEquals(4_000L, s.elapsedNow())

        now = 12_000L
        assertEquals(7_000L, s.elapsedNow())   // 4_000 + 3_000
    }

    @Test
    fun reset_restartsTimerFromCurrentClock() {
        var now = 1_000L
        val s = GameSession.newGame(size, diff, puzzle, solution, GameFlags()) { now }
        now = 8_000L
        s.pause()
        assertEquals(7_000L, s.elapsedNow())

        now = 20_000L
        s.reset()
        assertEquals(0L, s.elapsedNow())       // 重置后从当前时刻重新起算

        now = 22_000L
        assertEquals(2_000L, s.elapsedNow())
    }

    @Test
    fun winning_freezesElapsedTime() {
        var now = 1_000L
        val s = GameSession.newGame(size, diff, puzzle, solution, GameFlags()) { now }
        now = 3_000L                         // 到此刻已进行 2_000
        for (i in puzzle.indices) {
            if (puzzle[i] != 0) continue
            s.select(i)
            s.input(solution[i])
        }
        assertTrue(s.isWon)
        assertEquals(2_000L, s.elapsedNow())

        now = 9_000L
        assertEquals("通关后计时冻结", 2_000L, s.elapsedNow())
    }

    @Test
    fun snapshotRoundTrip_preservesBoardAndProgress() {
        var now = 1_000L
        val s = GameSession.newGame(size, diff, puzzle, solution, GameFlags()) { now }
        s.select(1)
        s.input(2)                            // 正确填入
        s.select(2)
        s.setNoteMode(true)
        s.input(3)                            // 记一笔候选
        s.setNoteMode(false)
        val snap = s.toSnapshot(savedAt = 5_000L)

        assertEquals(size.id, snap.sizeId)
        assertEquals(diff.id, snap.diffId)
        assertEquals(0, snap.mistakes)

        val restored = GameSession.fromSnapshot(snap) { 99_999L }
        assertEquals(2, restored.toUiState().cells[1].value)
        assertTrue(restored.toUiState().cells[2].notes.contains(3))
        assertEquals(0, restored.mistakes)
        assertEquals(0, restored.hintsUsed)
        // 恢复后处于暂停态，elapsedNow 即存档时累计时长（此处 < 1ms 取 0）
        assertEquals(snap.elapsedMs, restored.elapsedNow())
        assertEquals(snap.solution, restored.solution.joinToString("") {
            (if (it == 0) '0' else '0' + it).toString()
        })
    }

    @Test
    fun fromSnapshot_restoresMistakesAndHints() {
        val s = GameSession.newGame(size, diff, puzzle, solution, GameFlags()) { 1_000L }
        s.select(1)
        s.input(9)                            // 错误 → mistakes=1
        s.hint()
        val snap = s.toSnapshot(savedAt = 2_000L)
        assertEquals(1, snap.mistakes)
        assertTrue(snap.hintsUsed >= 1)

        val restored = GameSession.fromSnapshot(snap) { 3_000L }
        assertEquals(snap.mistakes, restored.mistakes)
        assertEquals(snap.hintsUsed, restored.hintsUsed)
    }
}
