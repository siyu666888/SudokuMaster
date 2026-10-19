package com.example.sudokumaster.domain.session

import com.example.sudokumaster.domain.model.BoardDefinitions
import com.example.sudokumaster.domain.model.GameFlags
import com.example.sudokumaster.domain.model.SizeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionTest {

    private val size = SizeId.FOUR
    private val diff = BoardDefinitions.difficulty(size, 1)
    private val solution = intArrayOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 1, 4, 3, 4, 3, 2, 1)
    private val puzzle = intArrayOf(1, 0, 0, 4, 0, 4, 0, 2, 0, 0, 4, 0, 4, 0, 0, 1)

    @Test
    fun givenCell_isImmutable() {
        val s = GameSession.newGame(size, diff, puzzle, solution, GameFlags())
        s.select(0)              // index0 是固定数字 1
        s.input(3)               // 试图修改固定格
        assertEquals(1, s.toUiState().cells[0].value)
    }

    @Test
    fun inputUndoRedo_roundTrips() {
        val s = GameSession.newGame(size, diff, puzzle, solution, GameFlags())
        s.select(1)
        s.input(2)               // 正确填入 2
        assertEquals(2, s.toUiState().cells[1].value)
        assertEquals(0, s.mistakes)
        assertTrue(s.canUndo())

        s.undo()
        assertEquals(0, s.toUiState().cells[1].value)
        assertTrue(s.canRedo())

        s.redo()
        assertEquals(2, s.toUiState().cells[1].value)
    }

    @Test
    fun noteMode_toggleAddsAndRemovesNote() {
        val s = GameSession.newGame(size, diff, puzzle, solution, GameFlags())
        s.setNoteMode(true)
        s.select(2)              // 空格
        s.input(5)
        assertTrue(s.toUiState().cells[2].notes.contains(5))
        s.input(5)              // 再按一次移除
        assertFalse(s.toUiState().cells[2].notes.contains(5))
    }

    @Test
    fun wrongInput_incrementsMistakes() {
        val s = GameSession.newGame(size, diff, puzzle, solution, GameFlags())
        s.select(1)
        s.input(9)              // 与解(2)不符 → 失误
        assertEquals(1, s.mistakes)
    }

    @Test
    fun autoNotes_populatesCandidates() {
        val s = GameSession.newGame(size, diff, puzzle, solution, GameFlags())
        val changed = s.autoNotes()
        assertTrue("autoNotes should fill some candidate cells", changed > 0)
        // 找一个空格，其笔记应为该格合法候选数之一（非空）
        val emptyIdx = puzzle.indexOf(0)
        assertTrue(s.toUiState().cells[emptyIdx].notes.isNotEmpty())
    }

    @Test
    fun autoExclude_removesPlacedDigitFromPeerNotes() {
        val s = GameSession.newGame(size, diff, puzzle, solution, GameFlags(autoExclude = true))
        // 手动给同行同伴 (index2) 记一个笔记 2
        s.setNoteMode(true)
        s.select(2)
        s.input(2)
        s.setNoteMode(false)
        // 在 index1 填入正确值 2 → 触发自动排除，移除同行(index2)笔记中的 2
        s.select(1)
        s.input(2)
        assertEquals(2, s.toUiState().cells[1].value)
        assertFalse("peer note 2 should be auto-excluded", s.toUiState().cells[2].notes.contains(2))
    }

    @Test
    fun withoutAutoExclude_manualNotePersists() {
        val s = GameSession.newGame(size, diff, puzzle, solution, GameFlags(autoExclude = false))
        s.setNoteMode(true)
        s.select(2)
        s.input(2)
        s.setNoteMode(false)
        s.select(1)
        s.input(2)
        assertTrue("without autoExclude, manual note 2 should persist", s.toUiState().cells[2].notes.contains(2))
    }

    @Test
    fun fillingAllCorrectCells_wins() {
        val s = GameSession.newGame(size, diff, puzzle, solution, GameFlags())
        for (i in puzzle.indices) {
            if (puzzle[i] != 0) continue
            s.select(i)
            s.input(solution[i])
        }
        assertTrue(s.isWon)
        assertTrue(s.toUiState().isWon)
    }
}
