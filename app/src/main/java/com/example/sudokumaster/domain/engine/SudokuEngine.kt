package com.example.sudokumaster.domain.engine

import kotlin.random.Random

/**
 * 数独核心算法（本地回溯）。
 *
 * 该实现与单机 HTML 版本逐行对应（setSize / canPlace / fillGrid / solve /
 * countSolutions / generatePuzzle），仅将全局可变状态改为显式参数传递，
 * 以支持 4×4 / 6×6 / 9×9 多尺寸。
 *
 * 生成保证唯一解：逐格挖洞，每步用 countSolutions(limit=2) 校验，
 * 若挖掉后不再唯一解则还原该格。
 */

data class BoardConfig(val n: Int, val boxRows: Int, val boxCols: Int)

data class Puzzle(
    val puzzle: IntArray,   // 题目（0 = 空格）
    val solution: IntArray  // 完整解
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Puzzle
        return puzzle.contentEquals(other.puzzle) && solution.contentEquals(other.solution)
    }

    override fun hashCode(): Int {
        var result = puzzle.contentHashCode()
        result = 31 * result + solution.contentHashCode()
        return result
    }
}

object SudokuEngine {

    private fun shuffle(arr: IntArray, rng: Random): IntArray {
        for (i in arr.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = arr[i]; arr[i] = arr[j]; arr[j] = t
        }
        return arr
    }

    private fun digits(n: Int): IntArray = IntArray(n) { it + 1 }

    private fun canPlace(g: IntArray, n: Int, boxRows: Int, boxCols: Int, idx: Int, `val`: Int): Boolean {
        val r = idx / n
        val c = idx % n
        for (i in 0 until n) {
            if (g[r * n + i] == `val`) return false
            if (g[i * n + c] == `val`) return false
        }
        val br = (r / boxRows) * boxRows
        val bc = (c / boxCols) * boxCols
        for (i in 0 until boxRows) for (j in 0 until boxCols) {
            if (g[(br + i) * n + (bc + j)] == `val`) return false
        }
        return true
    }

    /** 生成一个完整合法解（原地填充 g）。 */
    private fun fillGrid(g: IntArray, n: Int, boxRows: Int, boxCols: Int, rng: Random): Boolean {
        val idx = g.indexOf(0)
        if (idx == -1) return true
        val order = shuffle(digits(n), rng)
        for (v in order) {
            if (canPlace(g, n, boxRows, boxCols, idx, v)) {
                g[idx] = v
                if (fillGrid(g, n, boxRows, boxCols, rng)) return true
                g[idx] = 0
            }
        }
        return false
    }

    /** 统计解的数量（最多到 limit）。 */
    private fun countSolutions(g: IntArray, n: Int, boxRows: Int, boxCols: Int, limit: Int, rng: Random): Int {
        val idx = g.indexOf(0)
        if (idx == -1) return 1
        var count = 0
        for (v in 1..n) {
            if (canPlace(g, n, boxRows, boxCols, idx, v)) {
                g[idx] = v
                count += countSolutions(g, n, boxRows, boxCols, limit, rng)
                g[idx] = 0
                if (count >= limit) return count
            }
        }
        return count
    }

    /**
     * 生成题目：返回 {puzzle, solution}。
     * cluesTarget 为期望保留的线索数（数字个数）。
     */
    fun generatePuzzle(config: BoardConfig, cluesTarget: Int, rng: Random = Random): Puzzle {
        val total = config.n * config.n
        val solution = IntArray(total)
        fillGrid(solution, config.n, config.boxRows, config.boxCols, rng)
        val puzzle = solution.copyOf()

        val positions = IntArray(total) { it }
        shuffle(positions, rng)

        for (pos in positions) {
            if (puzzle.count { it != 0 } <= cluesTarget) break
            val backup = puzzle[pos]
            if (backup == 0) continue
            puzzle[pos] = 0
            val test = puzzle.copyOf()
            if (countSolutions(test, config.n, config.boxRows, config.boxCols, 2, rng) != 1) {
                puzzle[pos] = backup
            }
        }
        return Puzzle(puzzle, solution)
    }

    /**
     * 求解任意题面（回溯填满）。用于「导入题目」未附带 solution 时自动求解。
     * @return 完整解（与 puzzle 等长的新数组），无解返回 null。
     */
    fun solve(config: BoardConfig, puzzle: IntArray): IntArray? {
        val g = puzzle.copyOf()
        return if (fillGrid(g, config.n, config.boxRows, config.boxCols, Random.Default)) g else null
    }
}
