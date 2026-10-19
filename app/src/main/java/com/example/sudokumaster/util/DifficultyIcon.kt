package com.example.sudokumaster.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 难度英文名 -> 图标。
 * 首页 / 历史记录 / 成就墙三处共用同一映射，保证图标语义全局一致。
 */
fun difficultyIcon(en: String): ImageVector = when (en) {
    "Easy" -> Icons.Filled.SentimentVerySatisfied
    "Normal" -> Icons.Filled.SentimentNeutral
    "Hard" -> Icons.Filled.SentimentDissatisfied
    "Expert" -> Icons.Filled.MilitaryTech
    "Hell" -> Icons.Filled.LocalFireDepartment
    else -> Icons.Filled.SentimentSatisfied
}
