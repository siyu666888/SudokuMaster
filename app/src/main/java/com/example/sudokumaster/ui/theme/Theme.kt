package com.example.sudokumaster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.example.sudokumaster.domain.model.HighlightColor
import com.example.sudokumaster.domain.model.Palette
import com.example.sudokumaster.domain.model.ThemeMode
import com.example.sudokumaster.domain.model.AppSettings

@Composable
fun SudokuMasterTheme(
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    val dark = when (settings.theme) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val appColors = remember(settings.palette, dark) { buildAppColors(settings.palette, dark) }
    val highlight = remember(settings.highlightColor, dark) {
        highlightColor(settings.highlightColor, dark)
    }
    val colors = remember(appColors, highlight) {
        appColors.copy(peer = highlight, highlight = highlight)
    }

    val colorScheme = if (dark) {
        darkColorScheme(
            primary = colors.primary,
            error = colors.error,
            surfaceVariant = colors.primarySoft
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            error = colors.error,
            surfaceVariant = colors.primarySoft
        )
    }

    val scale = if (settings.fontScale == com.example.sudokumaster.domain.model.FontScale.LARGE) 1.18f else 1f

    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalFontScale provides scale
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography(scale),
            content = content
        )
    }
}

private fun buildAppColors(palette: Palette, dark: Boolean): AppColors {
    return when (palette) {
        Palette.BLUE -> AppColors(
            primary = if (dark) BlueDark else BlueLight,
            primarySoft = if (dark) BlueSoftDark else BlueSoftLight,
            peer = if (dark) BlueSoftDark else BlueSoftLight,
            highlight = if (dark) HiBlueDark else HiBlueLight,
            error = ErrorColor
        )
        Palette.GREEN -> AppColors(
            primary = if (dark) GreenDark else GreenLight,
            primarySoft = if (dark) GreenSoftDark else GreenSoftLight,
            peer = if (dark) GreenSoftDark else GreenSoftLight,
            highlight = if (dark) HiGreenDark else HiGreenLight,
            error = ErrorColor
        )
        Palette.GRAY -> AppColors(
            primary = if (dark) GrayDark else GrayLight,
            primarySoft = if (dark) GraySoftDark else GraySoftLight,
            peer = if (dark) GraySoftDark else GraySoftLight,
            highlight = if (dark) HiBlueDark else HiBlueLight,
            error = ErrorColor
        )
    }
}

private fun highlightColor(hl: HighlightColor, dark: Boolean): androidx.compose.ui.graphics.Color {
    return when (hl) {
        HighlightColor.BLUE -> if (dark) HiBlueDark else HiBlueLight
        HighlightColor.GREEN -> if (dark) HiGreenDark else HiGreenLight
        HighlightColor.PURPLE -> if (dark) HiPurpleDark else HiPurpleLight
    }
}
