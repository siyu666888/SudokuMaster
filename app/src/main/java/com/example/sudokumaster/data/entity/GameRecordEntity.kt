package com.example.sudokumaster.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 一局已完成的游戏记录（历史 + 成绩）。 */
@Entity(tableName = "game_records")
data class GameRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sizeId: String,
    val sizeName: String,
    val diffId: Int,
    val diffName: String,
    val timeMs: Long,
    val date: Long,
    val puzzle: String,   // 数字串，0 表示空格
    val solution: String,
    val score: Int = 0,   // 通关得分（0 = 旧版本记录，无评分）
    val stars: Int = 0    // 星级 1..3（0 = 旧版本记录）
)
