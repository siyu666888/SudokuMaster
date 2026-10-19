package com.example.sudokumaster.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.sp

/** 全局字号缩放（对应设置中的「标准 / 大」）。 */
val LocalFontScale = compositionLocalOf { 1f }

/** 当前界面派生配色，供棋盘 / 数字键盘等组件读取。 */
val LocalAppColors = compositionLocalOf<AppColors> {
    error("LocalAppColors not provided")
}

private fun scale(style: androidx.compose.ui.text.TextStyle, scale: Float): androidx.compose.ui.text.TextStyle =
    style.copy(fontSize = (style.fontSize.value * scale).sp)

/** 依据字号档位缩放 Material3 默认排版。 */
fun scaledTypography(scale: Float): Typography {
    val t = Typography()
    return t.copy(
        displayLarge = scale(t.displayLarge, scale),
        displayMedium = scale(t.displayMedium, scale),
        displaySmall = scale(t.displaySmall, scale),
        headlineLarge = scale(t.headlineLarge, scale),
        headlineMedium = scale(t.headlineMedium, scale),
        headlineSmall = scale(t.headlineSmall, scale),
        titleLarge = scale(t.titleLarge, scale),
        titleMedium = scale(t.titleMedium, scale),
        titleSmall = scale(t.titleSmall, scale),
        bodyLarge = scale(t.bodyLarge, scale),
        bodyMedium = scale(t.bodyMedium, scale),
        bodySmall = scale(t.bodySmall, scale),
        labelLarge = scale(t.labelLarge, scale),
        labelMedium = scale(t.labelMedium, scale),
        labelSmall = scale(t.labelSmall, scale)
    )
}

@Composable
fun appColors(): AppColors = LocalAppColors.current

@Composable
fun fontScale(): Float = LocalFontScale.current
