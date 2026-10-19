package com.example.sudokumaster.domain.model

/** 游戏内视觉开关，供 GameSession 计算候选高亮与辅助。 */
data class GameFlags(
    val showError: Boolean = true,
    val highlightSame: Boolean = true,
    val autoExclude: Boolean = false   // 候选数自动排除：填入数字后清除同行列宫的该候选
)

/** 单个单元格的不可变视图状态（UI 只读）。 */
data class CellState(
    val index: Int,
    val value: Int,            // 0 表示空
    val isGiven: Boolean,      // 题目固定数字
    val notes: Set<Int>,       // 候选数
    val isError: Boolean,      // 与解冲突（仅当 showError 时）
    val isSelected: Boolean,
    val isPeer: Boolean,       // 与选中格同行/列/宫
    val isSameValue: Boolean   // 与选中格数值相同（仅当 highlightSame 时）
)

/** 整局游戏的不可变快照，由 GameSession.toUiState() 产生。 */
data class GameUiState(
    val sizeId: SizeId,
    val difficulty: Difficulty,
    val cells: List<CellState>,
    val selectedIndex: Int,
    val n: Int,
    val boxRows: Int,
    val boxCols: Int,
    val elapsedMs: Long,
    val hintsUsed: Int,
    val mistakes: Int,
    val isNoteMode: Boolean,
    val isWon: Boolean,
    val isPaused: Boolean,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val digitCountsLeft: List<Int>,  // index 1..n，剩余可填次数
    val title: String
)

/**
 * 胜利信息（用于通关庆祝界面）。
 *
 * @param levelName 关卡名称，如 "九宫格 · 地狱"
 * @param score 本局得分（见 [com.example.sudokumaster.domain.game.ScoreCalculator]）
 * @param stars 星级 1..3
 * @param rank 评级 S / A / B / C
 * @param mistakes 错误填充次数
 * @param hintsUsed 提示使用次数
 */
data class WinInfo(
    val sizeId: SizeId,
    val diffId: Int,
    val diffName: String,
    val timeMs: Long,
    val isBest: Boolean,
    val bestMs: Long,
    val levelName: String = "",
    val score: Int = 0,
    val stars: Int = 3,
    val rank: String = "S",
    val mistakes: Int = 0,
    val hintsUsed: Int = 0
)
