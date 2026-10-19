package com.example.sudokumaster.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sudokumaster.SudokuApplication
import com.example.sudokumaster.domain.model.BoardDefinitions
import com.example.sudokumaster.domain.model.SizeId
import com.example.sudokumaster.domain.model.StatsTuple
import com.example.sudokumaster.util.colorHex
import com.example.sudokumaster.util.difficultyIcon
import com.example.sudokumaster.util.fmtTime
import com.example.sudokumaster.viewmodel.HomeViewModel
import com.example.sudokumaster.viewmodel.vmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewGame: (SizeId, Int) -> Unit,
    onResume: (slotId: String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SudokuApplication
    val vm: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = vmFactory { HomeViewModel(app.container) }
    )

    val savedGame by vm.savedGame.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val statsMap by vm.statsMap.collectAsStateWithLifecycle()
    val activeSlotKeys by vm.activeSlotKeys.collectAsStateWithLifecycle()
    // 待确认的新开局目标（该槽已有进行中进度时弹出覆盖确认，F1）
    var pendingNew by remember { mutableStateOf<Pair<SizeId, Int>?>(null) }
    val size = SizeId.fromId(settings.selectedSizeId)
    val boardType = BoardDefinitions.get(size)

    Scaffold(
        // 顶层由 AppNavHost 的 Scaffold 负责系统边距，这里不再重复计入窗口 inset，避免顶部留白双倍
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("数独大师", fontWeight = FontWeight.Bold) }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 续玩卡片：点击恢复「最近保存」的那一盘（指定 slotId）
            // v1.9.1：扁平化——去掉阴影/发丝描边，状态由底色（primaryContainer / surfaceVariant）表达
            val canContinue = savedGame != null && savedGame?.isWon == false
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (canContinue) Modifier.clickable { onResume(savedGame!!.slotId) } else Modifier
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (canContinue) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 继续游戏图标：简洁播放键，无多余装饰；有进行中局用主题主色，否则置灰
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = if (canContinue) "继续游戏" else null,
                        tint = if (canContinue) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            "继续游戏",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (canContinue) {
                                val g = savedGame!!
                                "${g.sizeName} · ${g.diffName} · 已玩 ${fmtTime(g.elapsedMs)}"
                            } else "暂无进行中的对局",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("棋盘尺寸", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SizeId.entries.forEach { sid ->
                    SizeBox(
                        sizeId = sid,
                        selected = sid == size,
                        onClick = { vm.selectSize(sid) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("选择难度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            boardType.diffs.forEach { diff ->
                val key = BoardDefinitions.statsKey(size.id, diff.id)
                val stats: StatsTuple = statsMap[key] ?: StatsTuple(0, null)
                DifficultyCard(
                    icon = difficultyIcon(diff.en),
                    en = diff.en,
                    name = diff.name,
                    color = colorHex(diff.color),
                    count = stats.count,
                    best = stats.best,
                    onClick = {
                        // 该 (尺寸:难度) 已有进行中进度时先确认，避免误覆盖（F1）
                        if (key in activeSlotKeys) pendingNew = size to diff.id
                        else onNewGame(size, diff.id)
                    }
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        // 覆盖确认：继续上一局 / 重新开始 / 取消
        pendingNew?.let { (pSize, pDiffId) ->
            val pDiff = BoardDefinitions.difficultyOrFirst(pSize, pDiffId)
            AlertDialog(
                onDismissRequest = { pendingNew = null },
                title = { Text("重新开始？") },
                text = {
                    Text("「${pSize.label} · ${pDiff.name}」有一盘还没下完，重新开始会覆盖该进度。")
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingNew = null
                        onNewGame(pSize, pDiffId)
                    }) { Text("重新开始") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            val slot = pendingNew
                            pendingNew = null
                            if (slot != null) onResume(BoardDefinitions.statsKey(slot.first.id, slot.second))
                        }) { Text("继续上一局") }
                        TextButton(onClick = { pendingNew = null }) { Text("取消") }
                    }
                }
            )
        }
    }
}

/**
 * 棋盘尺寸独立选择框：含迷你棋盘预览。选中时放大并强高亮（跟随主题 primary），
 * 未选中尺寸的难度不会在此区块出现（难度始终与所选尺寸对应）。
 */
@Composable
private fun SizeBox(
    sizeId: SizeId,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val scale by animateFloatAsState(if (selected) 1.07f else 1f, label = "sizebox-scale")
    // v1.9.1：扁平化——去掉阴影与描边。因 surfaceVariant 与 primaryContainer 色相近，
    // 选中态改用「实底 primaryContainer vs 淡底 surfaceVariant(45%)」+ 放大 + 加粗主色文字表达。
    Surface(
        modifier = modifier
            .scale(scale)
            .aspectRatio(0.85f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            MiniGrid(n = sizeId.n, selected = selected, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                sizeId.label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.primary else colors.onSurface
            )
            Text(
                "${sizeId.n} × ${sizeId.n}",
                fontSize = 10.sp,
                color = colors.onSurfaceVariant
            )
        }
    }
}

/** 用 Canvas 画一个迷你棋盘预览（n×n 网格 + 宫加粗线）。 */
@Composable
private fun MiniGrid(n: Int, selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val lineColor = if (selected) colors.primary.copy(alpha = 0.55f) else colors.onSurfaceVariant.copy(alpha = 0.5f)
    Canvas(modifier = modifier) {
        val cell = size.width / n
        for (i in 0..n) {
            val thick = when (n) {
                4 -> i % 2 == 0
                6 -> i % 2 == 0 || i % 3 == 0
                else -> i % 3 == 0
            }
            val w = if (thick) 1.6f else 0.8f
            drawLine(lineColor, Offset(i * cell, 0f), Offset(i * cell, size.height), strokeWidth = w)
            drawLine(lineColor, Offset(0f, i * cell), Offset(size.width, i * cell), strokeWidth = w)
        }
    }
}

@Composable
private fun DifficultyCard(
    icon: ImageVector,
    en: String,
    name: String,
    color: Color,
    count: Int,
    best: Long?,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(72.dp)
                    .background(color, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = name,
                        tint = color,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        en,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "完成 $count 局 · 最佳 ${best?.let { fmtTime(it) } ?: "--"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
