package com.example.sudokumaster.domain.session

/**
 * 对局的可序列化快照（纯领域对象，不含 Room / Android 依赖）。
 * 用作 domain 与 data 层之间的边界：GameSession 仅产出 / 消费本类型，
 * 与持久化实体(SavedGameEntity)的映射（slotId 计算、字段对齐）由 data 层负责。
 *
 * 字段与 SavedGameEntity 一一对应（slotId 由 data 层在映射时合成）。
 */
data class GameSnapshot(
    val sizeId: String,
    val sizeName: String,
    val diffId: Int,
    val diffName: String,
    val values: String,
    val given: String,
    val notes: String,
    val solution: String,
    val selected: Int,
    val hintsUsed: Int,
    val mistakes: Int,
    val elapsedMs: Long,
    val isWon: Boolean,
    val savedAt: Long
)
