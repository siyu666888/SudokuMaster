package com.example.sudokumaster.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 当前进行中的对局（按 尺寸:难度 分槽存储，每个槽最多一条）。
 * 用于退出后恢复进度。多槽允许不同尺寸 / 难度各保留一盘未完成进度，
 * 新开局若对应槽已有进行中进度需二次确认（F1）。
 * notes 格式：每格候选数用逗号连接，格与格用分号连接，例如 "1,3;;4,5"。
 *
 * slotId = "$sizeId:$diffId"，即该对局的唯一槽位主键。
 */
@Entity(tableName = "saved_game")
data class SavedGameEntity(
    @PrimaryKey val slotId: String,
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
