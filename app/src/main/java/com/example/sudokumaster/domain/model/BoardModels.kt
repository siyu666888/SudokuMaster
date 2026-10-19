package com.example.sudokumaster.domain.model

/**
 * 棋盘尺寸标识。对应原 HTML 方案的 BOARD_TYPES：
 *  - 四宫格 (4×4, 2×2 宫)
 *  - 六宫格 (6×6, 2×3 宫)
 *  - 九宫格 (9×9, 3×3 宫)
 */
enum class SizeId(
    val id: String,
    val label: String,
    val n: Int,
    val boxRows: Int,
    val boxCols: Int
) {
    FOUR("4", "四宫格", 4, 2, 2),
    SIX("6", "六宫格", 6, 2, 3),
    NINE("9", "九宫格", 9, 3, 3);

    companion object {
        fun fromId(id: String?): SizeId =
            entries.firstOrNull { it.id == id } ?: NINE
    }
}

/** 难度等级。clues = 目标线索数（保留数字），color 为卡片主题色（十六进制字符串）。 */
data class Difficulty(
    val id: Int,
    val name: String,
    val en: String,
    val clues: Int,
    val color: String
)

/** 一种棋盘尺寸下的全部难度。 */
data class BoardType(
    val size: SizeId,
    val diffs: List<Difficulty>
)

/** 全局棋盘定义，对应原方案 BOARD_TYPES（含各尺寸线索数 / 配色）。 */
object BoardDefinitions {

    val TYPES: List<BoardType> = listOf(
        BoardType(
            SizeId.FOUR, listOf(
                Difficulty(1, "简单", "Easy", 12, "#3fb950"),
                Difficulty(2, "普通", "Normal", 10, "#2f6fed")
            )
        ),
        BoardType(
            SizeId.SIX, listOf(
                Difficulty(1, "简单", "Easy", 26, "#3fb950"),
                Difficulty(2, "普通", "Normal", 23, "#2f6fed"),
                Difficulty(3, "困难", "Hard", 21, "#d29922")
            )
        ),
        BoardType(
            SizeId.NINE, listOf(
                Difficulty(1, "简单", "Easy", 45, "#3fb950"),
                Difficulty(2, "普通", "Normal", 38, "#2f6fed"),
                Difficulty(3, "困难", "Hard", 32, "#d29922"),
                Difficulty(4, "专家", "Expert", 28, "#bc4cff"),
                Difficulty(5, "地狱", "Hell", 25, "#e0483b")
            )
        )
    )

    fun get(size: SizeId): BoardType = TYPES.first { it.size == size }

    fun difficulty(size: SizeId, diffId: Int): Difficulty =
        get(size).diffs.first { it.id == diffId }

    /** 安全版：diffId 不存在时回退该尺寸首个难度（导入/路由防崩）。 */
    fun difficultyOrFirst(size: SizeId, diffId: Int): Difficulty =
        get(size).diffs.firstOrNull { it.id == diffId } ?: get(size).diffs.first()

    /** 按名称匹配难度（导入题目用），找不到回退该尺寸首个难度。 */
    fun difficultyByName(size: SizeId, name: String?): Difficulty {
        val diffs = get(size).diffs
        return diffs.firstOrNull { name != null && it.name == name } ?: diffs.first()
    }

    /** "sizeId:diffId" —— 与原方案的 statsKey 保持一致。 */
    fun statsKey(sizeId: String, diffId: Int): String = "$sizeId:$diffId"
}
