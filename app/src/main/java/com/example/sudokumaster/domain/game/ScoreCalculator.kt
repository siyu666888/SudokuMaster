package com.example.sudokumaster.domain.game

import com.example.sudokumaster.domain.model.Difficulty
import com.example.sudokumaster.domain.model.SizeId

/**
 * 通关评分结果。
 *
 * @param score 得分（50 ~ 1000+，四/六宫格按尺寸倍率下调）
 * @param stars 星级 1..3（3 星 = 零失误 + 少提示 + 快于标准时间）
 * @param rank 评级 S / A / B / C
 */
data class ScoreResult(
    val score: Int,
    val stars: Int,
    val rank: String
)

/**
 * 通关计分器（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 评分模型：
 * - 基础分 = 1000 × 尺寸倍率 × 难度倍率
 * - 速度系数 = 标准用时 / 实际用时（>1 表示快于标准，封顶 1.0 计分，2.0 封顶用于星级）
 * - 时间项 = 基础分 × (0.4 + 0.6 × min(1, 速度系数))——保底 40%，越快越高
 * - 罚分 = 错误次数 × 50 + 提示次数 × 80
 * - 得分 = max(50, 时间项 - 罚分)
 *
 * 星级：
 * - ★★★ 零错误 且 提示 ≤1 且 速度系数 ≥0.8
 * - ★★☆ 错误 ≤2 且 提示 ≤3 且 速度系数 ≥0.4
 * - ★☆☆ 其余
 *
 * 评级按「得分 / 基础分」比值：≥0.9 S，≥0.7 A，≥0.5 B，其余 C。
 */
object ScoreCalculator {

    /** 各难度倍率（对应 Difficulty.id 1..5）。 */
    fun difficultyMultiplier(difficulty: Difficulty): Float = when (difficulty.id) {
        1 -> 1.0f    // 简单
        2 -> 1.15f   // 普通
        3 -> 1.35f   // 困难
        4 -> 1.6f    // 专家
        else -> 2.0f // 地狱
    }

    /** 尺寸倍率：小棋盘格子少，基础分相应下调。 */
    fun sizeMultiplier(sizeId: SizeId): Float = when (sizeId.n) {
        4 -> 0.6f
        6 -> 0.8f
        else -> 1.0f
    }

    /** 标准完成时间（毫秒）：随尺寸与难度放大，作为速度系数基准。 */
    fun parTimeMs(sizeId: SizeId, difficulty: Difficulty): Long {
        val base = when (sizeId.n) {
            4 -> 60_000L
            6 -> 180_000L
            else -> 600_000L
        }
        val factor = when (difficulty.id) {
            1 -> 1.0
            2 -> 1.2
            3 -> 1.5
            4 -> 1.8
            else -> 2.2
        }
        return (base * factor).toLong()
    }

    /**
     * 计算一局的评分结果。
     *
     * @param elapsedMs 实际用时（毫秒，>=1）
     * @param mistakes 错误填充次数（填错又改回的累计）
     * @param hintsUsed 提示使用次数
     */
    fun calculate(
        sizeId: SizeId,
        difficulty: Difficulty,
        elapsedMs: Long,
        mistakes: Int,
        hintsUsed: Int
    ): ScoreResult {
        val par = parTimeMs(sizeId, difficulty)
        val safeElapsed = elapsedMs.coerceAtLeast(1L)
        // 速度系数：1.0 = 达到标准用时；更快最高 2.0；更慢最低 0.25
        val speed = (par.toFloat() / safeElapsed.toFloat()).coerceIn(0.25f, 2.0f)

        val base = 1000f * sizeMultiplier(sizeId) * difficultyMultiplier(difficulty)
        val penalty = mistakes * 50 + hintsUsed * 80
        val timePart = base * (0.4f + 0.6f * minOf(1f, speed))
        val score = (timePart - penalty).toInt().coerceAtLeast(50)

        val stars = when {
            mistakes == 0 && hintsUsed <= 1 && speed >= 0.8f -> 3
            mistakes <= 2 && hintsUsed <= 3 && speed >= 0.4f -> 2
            else -> 1
        }

        val ratio = score / base
        val rank = when {
            ratio >= 0.9f -> "S"
            ratio >= 0.7f -> "A"
            ratio >= 0.5f -> "B"
            else -> "C"
        }
        return ScoreResult(score = score, stars = stars, rank = rank)
    }
}
