package com.example.sudokumaster.ui.theme

import androidx.compose.ui.graphics.Color

// 配色方案主色（护眼低饱和）
val BlueLight = Color(0xFF5B5EF0)
val BlueDark = Color(0xFF8B8EF5)
val GreenLight = Color(0xFF2F9E6E)
val GreenDark = Color(0xFF52C08A)
val GrayLight = Color(0xFF6B7280)
val GrayDark = Color(0xFF9AA1AD)

// 主色对应的柔和底色（用于选中/备注态）
val BlueSoftLight = Color(0xFFE9EAFE)
val BlueSoftDark = Color(0xFF2B2E4B)
val GreenSoftLight = Color(0xFFDCF2E8)
val GreenSoftDark = Color(0xFF1F3A30)
val GraySoftLight = Color(0xFFECEEF2)
val GraySoftDark = Color(0xFF2A2E36)

// 同值高亮背景
val HiBlueLight = Color(0xFFC9D2FB)
val HiBlueDark = Color(0xFF363C69)
val HiGreenLight = Color(0xFFC4ECD3)
val HiGreenDark = Color(0xFF1D3A2A)
val HiPurpleLight = Color(0xFFDDD3FB)
val HiPurpleDark = Color(0xFF3A2F5E)

// 错误 / 冲突
val ErrorColor = Color(0xFFE0483B)

// 笔记数字
val NoteColorLight = Color(0xFF7A8290)
val NoteColorDark = Color(0xFFAEB6C2)

/** 整局界面用到的派生配色。 */
data class AppColors(
    val primary: Color,
    val primarySoft: Color,
    val peer: Color,
    val highlight: Color,
    val error: Color
)
