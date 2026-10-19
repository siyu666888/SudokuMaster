package com.example.sudokumaster.domain.model

/** 主题模式。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 选区 / 同值高亮配色。 */
enum class HighlightColor { BLUE, GREEN, PURPLE }

/** 字号档位。 */
enum class FontScale { STANDARD, LARGE }

/** 整体配色方案（护眼低饱和预设）。 */
enum class Palette { BLUE, GREEN, GRAY }

/** 提示模式：直接填答案 / 只给候选数。 */
enum class HintMode { FILL, CANDIDATE }

/**
 * 全局设置。对应原方案 settings 对象，默认值与 HTML 版本一致。
 * selectedSizeId 对应原方案的 settings.size（首页当前尺寸）。
 */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val highlightColor: HighlightColor = HighlightColor.BLUE,
    val fontScale: FontScale = FontScale.STANDARD,
    val palette: Palette = Palette.BLUE,
    val longPressNote: Boolean = true,
    val hintMode: HintMode = HintMode.FILL,
    val autoNote: Boolean = true,
    val fastGen: Boolean = false,
    val showError: Boolean = true,
    val highlightSame: Boolean = true,
    val autoExclude: Boolean = false,
    val vibrate: Boolean = true,
    val sound: Boolean = true,
    val selectedSizeId: String = "9"
)
