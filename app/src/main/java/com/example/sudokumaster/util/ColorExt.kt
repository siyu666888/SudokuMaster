package com.example.sudokumaster.util

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import com.example.sudokumaster.domain.model.BoardDefinitions
import com.example.sudokumaster.domain.model.SizeId

/** 将 "#rrggbb" 十六进制字符串转为 Compose Color（解析失败时返回默认色）。 */
fun colorHex(hex: String, fallback: Color = Color(0xFF2F6FED)): Color =
    try {
        Color(AndroidColor.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }

/** 由 (尺寸, 难度) 取得该难度的主题色（用于成就/历史页的色条延伸）。 */
fun difficultyColor(sizeId: String, diffId: Int): Color =
    runCatching { BoardDefinitions.difficulty(SizeId.fromId(sizeId), diffId) }
        .fold(
            onSuccess = { colorHex(it.color) },
            onFailure = { Color(0xFF2F6FED) }
        )
