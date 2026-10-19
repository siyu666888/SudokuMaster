package com.example.sudokumaster.domain.model

/** 某 (尺寸:难度) 下的成绩聚合。 */
data class StatsTuple(
    val count: Int,
    val best: Long?   // 最佳用时（毫秒），null 表示尚无记录
)

/**
 * 通关记录的轻量视图（供统计分析）。
 * 由数据库实体在 ViewModel 层映射而来，避免领域层直接依赖 Room。
 */
data class WinRecord(
    val sizeId: String,
    val diffId: Int,
    val timeMs: Long,
    val date: Long
)
