package com.example.sudokumaster.ui.game

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sudokumaster.domain.model.CellState
import com.example.sudokumaster.ui.theme.LocalFontScale
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * 数独棋盘。宫边界用较粗线条绘制（与原方案一致：最后一列/行交由外框负责）。
 */
@Composable
fun SudokuBoard(
    n: Int,
    boxRows: Int,
    boxCols: Int,
    cells: List<CellState>,
    onCellClick: (Int) -> Unit,
    onCellLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(colors.surface, RoundedCornerShape(8.dp))
            .border(2.dp, colors.outline, RoundedCornerShape(8.dp))
            .padding(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (r in 0 until n) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    for (c in 0 until n) {
                        val idx = r * n + c
                        BoardCell(
                            cell = cells[idx],
                            n = n,
                            isThickRight = (c + 1) % boxCols == 0 && c != n - 1,
                            isThickBottom = (r + 1) % boxRows == 0 && r != n - 1,
                            lineColor = colors.outline.copy(alpha = 0.35f),
                            thickColor = colors.outline,
                            onClick = { onCellClick(idx) },
                            onLongClick = { onCellLongClick(idx) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoardCell(
    cell: CellState,
    n: Int,
    isThickRight: Boolean,
    isThickBottom: Boolean,
    lineColor: Color,
    thickColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = com.example.sudokumaster.ui.theme.LocalAppColors.current
    val scale = LocalFontScale.current

    val bg = when {
        cell.isError -> appColors.error.copy(alpha = 0.18f)
        cell.isSelected -> MaterialTheme.colorScheme.primary
        cell.isPeer -> appColors.primarySoft
        cell.isSameValue -> appColors.highlight
        else -> Color.Transparent
    }

    val thick = 2.dp
    val thin = 0.7.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .drawBehind {
                if (isThickRight) {
                    drawLine(thickColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = thick.toPx())
                } else {
                    drawLine(lineColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = thin.toPx())
                }
                if (isThickBottom) {
                    drawLine(thickColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = thick.toPx())
                } else {
                    drawLine(lineColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = thin.toPx())
                }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        if (cell.value != 0) {
            val size = when (n) {
                4 -> 34.sp
                6 -> 28.sp
                else -> 22.sp
            }
            val color = when {
                cell.isError -> appColors.error
                cell.isSelected -> MaterialTheme.colorScheme.onPrimary
                cell.isGiven -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.primary
            }
            Text(
                text = cell.value.toString(),
                fontSize = (size.value * scale).sp,
                fontWeight = if (cell.isGiven) FontWeight.Bold else FontWeight.SemiBold,
                color = color
            )
        } else if (cell.notes.isNotEmpty()) {
            val noteColor = if (cell.isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
            NotesGrid(notes = cell.notes, n = n, scale = scale, noteColor = noteColor)
        }
    }
}

@Composable
private fun NotesGrid(
    notes: Set<Int>,
    n: Int,
    scale: Float,
    noteColor: Color
) {
    val cols = ceil(sqrt(n.toFloat())).toInt()
    val rows = ceil(n.toFloat() / cols).toInt()
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(1.dp)) {
        // 单个候选格子的实测可用边长（dp）
        val box = minOf(maxWidth / cols, maxHeight / rows)
        // 基准上限随用户字号档缩放，再按实测空间收紧：任何屏幕/字号档下都放得下、不重叠
        val cap = when (n) {
            4 -> 16f
            6 -> 14f
            else -> 13f
        } * scale
        val noteSize = minOf(box.value * 0.8f, cap).sp
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (c in 0 until cols) {
                        val digit = r * cols + c + 1
                        val show = digit <= n && notes.contains(digit)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (show) {
                                Text(
                                    text = digit.toString(),
                                    fontSize = noteSize,
                                    // 行高=字号并关闭字体额外间距，避免数字被内建 padding 撑出格子
                                    lineHeight = noteSize,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(
                                            includeFontPadding = false
                                        )
                                    ),
                                    fontWeight = FontWeight.Medium,
                                    color = noteColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
