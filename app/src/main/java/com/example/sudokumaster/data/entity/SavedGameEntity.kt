package com.example.sudokumaster.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 当前进行中的对局（最多一条，id 固定为 1）。用于退出后恢复进度。
 * notes 格式：每格候选数用逗号连接，格与格用分号连接，例如 "1,3;;4,5"。
 */
@Entity(tableName = "saved_game")
data class SavedGameEntity(
    @PrimaryKey val id: Int = 1,
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
