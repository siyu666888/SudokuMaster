package com.example.sudokumaster.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuEngineTest {

    private fun isValidSolution(g: IntArray, n: Int, boxRows: Int, boxCols: Int): Boolean {
        if (g.any { it !in 1..n }) return false
        val seen = BooleanArray(n + 1)
        // 行
        for (r in 0 until n) {
            seen.fill(false)
            for (c in 0 until n) {
                val v = g[r * n + c]
                if (seen[v]) return false
                seen[v] = true
            }
        }
        // 列
        for (c in 0 until n) {
            seen.fill(false)
            for (r in 0 until n) {
                val v = g[r * n + c]
                if (seen[v]) return false
                seen[v] = true
            }
        }
        // 宫
        for (br in 0 until n step boxRows) for (bc in 0 until n step boxCols) {
            seen.fill(false)
            for (i in 0 until boxRows) for (j in 0 until boxCols) {
                val v = g[(br + i) * n + (bc + j)]
                if (seen[v]) return false
                seen[v] = true
            }
        }
        return true
    }

    @Test
    fun generateNine_isValidUniqueAndSolvable() {
        val config = BoardConfig(9, 3, 3)
        val puzzle = SudokuEngine.generatePuzzle(config, cluesTarget = 45, rng = kotlin.random.Random(123))

        // 解为完整合法数独
        assertTrue("solution must be a valid 9x9", isValidSolution(puzzle.solution, 9, 3, 3))
        // 题面线索数合理（<17 不可能唯一解）
        val clues = puzzle.puzzle.count { it != 0 }
        assertTrue("clue count should be > 17, was $clues", clues > 17)
        // 题面固定格与解一致
        for (i in puzzle.puzzle.indices) {
            if (puzzle.puzzle[i] != 0) assertEquals(puzzle.solution[i], puzzle.puzzle[i])
        }
        // 求解题面应得到与生成解一致的唯一解
        val solved = SudokuEngine.solve(config, puzzle.puzzle)
        assertNotNull(solved)
        assertTrue("solve() should reproduce the unique solution", solved!!.contentEquals(puzzle.solution))
    }

    @Test
    fun generateFour_isValid() {
        val config = BoardConfig(4, 2, 2)
        val puzzle = SudokuEngine.generatePuzzle(config, cluesTarget = 12, rng = kotlin.random.Random(7))
        assertTrue("4x4 solution must be valid", isValidSolution(puzzle.solution, 4, 2, 2))
        assertTrue("4x4 clue count should be > 4", puzzle.puzzle.count { it != 0 } > 4)
    }

    @Test
    fun solve_reproducesKnownFourByFour() {
        val config = BoardConfig(4, 2, 2)
        val solution = intArrayOf(1, 2, 3, 4, 3, 4, 1, 2, 2, 1, 4, 3, 4, 3, 2, 1)
        val puzzle = intArrayOf(1, 0, 0, 4, 0, 4, 0, 2, 0, 0, 4, 0, 4, 0, 0, 1)
        val solved = SudokuEngine.solve(config, puzzle)
        assertNotNull(solved)
        assertTrue(solved!!.contentEquals(solution))
    }

    @Test
    fun solve_emptyNine_returnsValidGrid() {
        val config = BoardConfig(9, 3, 3)
        val solved = SudokuEngine.solve(config, IntArray(81))
        assertNotNull(solved)
        assertTrue("empty 9x9 must solve to a valid grid", isValidSolution(solved!!, 9, 3, 3))
    }
}
