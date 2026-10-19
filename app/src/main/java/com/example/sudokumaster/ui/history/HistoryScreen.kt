package com.example.sudokumaster.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sudokumaster.SudokuApplication
import com.example.sudokumaster.data.entity.GameRecordEntity
import com.example.sudokumaster.domain.game.StatsAnalyzer
import com.example.sudokumaster.domain.model.BoardDefinitions
import com.example.sudokumaster.domain.model.SizeId
import com.example.sudokumaster.ui.components.EmptyState
import com.example.sudokumaster.ui.components.SearchEmptyIllustration
import com.example.sudokumaster.ui.components.lifted
import com.example.sudokumaster.util.difficultyColor
import com.example.sudokumaster.util.difficultyIcon
import com.example.sudokumaster.util.fmtTime
import com.example.sudokumaster.util.historyDateLabel
import com.example.sudokumaster.viewmodel.HistoryViewModel
import com.example.sudokumaster.viewmodel.vmFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 星级金色（评分星标用）。 */
private val StarGold = Color(0xFFFFC53D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onReplay: (Long) -> Unit,
    onResume: (slotId: String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SudokuApplication
    val vm: HistoryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = vmFactory { HistoryViewModel(app.container) }
    )
    val records by vm.records.collectAsStateWithLifecycle()
    val total by vm.totalCount.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sizeFilter by vm.sizeFilter.collectAsStateWithLifecycle()
    val diffFilter by vm.diffFilter.collectAsStateWithLifecycle()
    val activeSlotKeys by vm.activeSlotKeys.collectAsStateWithLifecycle()
    val filterActive = query.isNotBlank() || sizeFilter != null || diffFilter != null
    // 复玩覆盖确认：目标 (尺寸:难度) 有进行中进度时先询问（F1）
    var pendingReplay by remember { mutableStateOf<GameRecordEntity?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("历史记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            // 紧凑搜索栏：46dp 圆角胶囊，按尺寸 / 难度 / 用时（mm:ss）模糊匹配
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .height(46.dp),
                shape = RoundedCornerShape(23.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "搜索历史记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "搜索尺寸 / 难度 / 用时",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = vm::setQuery,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { vm.setQuery("") },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "清空搜索",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }

            // 尺寸筛选
            ChipRow(
                options = listOf(
                    null to "全部",
                    SizeId.FOUR.id to "四宫格",
                    SizeId.SIX.id to "六宫格",
                    SizeId.NINE.id to "九宫格"
                ),
                selected = sizeFilter,
                onSelect = vm::setSizeFilter
            )

            // 难度筛选（随尺寸联动：仅展示该尺寸可用难度）
            val size = sizeFilter?.let { SizeId.fromId(it) }
            val diffOptions: List<Pair<Int?, String>> = if (size != null) {
                BoardDefinitions.get(size).diffs.map { it.id to it.name }
            } else {
                listOf(
                    null to "全部难度",
                    1 to "简单",
                    2 to "普通",
                    3 to "困难",
                    4 to "专家",
                    5 to "地狱"
                )
            }
            ChipRow(
                options = diffOptions,
                selected = diffFilter,
                onSelect = vm::setDiffFilter
            )

            // 结果计数 + 清除筛选
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "共 ${records.size} 局" + if (filterActive) "（已筛选）" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (filterActive) {
                    TextButton(onClick = vm::clearFilters) { Text("清除筛选") }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    total == 0 -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                title = "还没有完成的对局",
                                subtitle = "去首页开始第一局，记录会出现在这里",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    records.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                title = "没有匹配的记录",
                                subtitle = "换个关键词，或清除筛选条件试试",
                                illustration = { SearchEmptyIllustration() },
                                actionLabel = "清除筛选",
                                onAction = vm::clearFilters,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    else -> {
                        // 按本地自然日分组（groupBy 保持日期倒序）
                        val groups = records.groupBy { StatsAnalyzer.dayNumber(it.date) }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groups.forEach { (dayNum, items) ->
                                item(key = "day_$dayNum") {
                                    Text(
                                        text = historyDateLabel(items.first().date),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
                                    )
                                }
                                items(items, key = { it.id }) { rec ->
                                    HistoryRecordCard(
                                        rec = rec,
                                        onReplay = {
                                            // 该 (尺寸:难度) 已有进行中进度时先确认，避免误覆盖（F1）
                                            if (BoardDefinitions.statsKey(rec.sizeId, rec.diffId) in activeSlotKeys) {
                                                pendingReplay = rec
                                            } else onReplay(rec.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 复玩覆盖确认：继续上一局 / 仍要复玩 / 取消
        pendingReplay?.let { rec ->
            AlertDialog(
                onDismissRequest = { pendingReplay = null },
                title = { Text("该难度还有未完成的对局") },
                text = {
                    Text("「${rec.sizeName} · ${rec.diffName}」有一盘进行中的进度，复玩会覆盖它。")
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingReplay = null
                        onReplay(rec.id)
                    }) { Text("仍要复玩") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            pendingReplay = null
                            onResume(BoardDefinitions.statsKey(rec.sizeId, rec.diffId))
                        }) { Text("继续上一局") }
                        TextButton(onClick = { pendingReplay = null }) { Text("取消") }
                    }
                }
            )
        }
    }
}

/** 单条历史记录卡片（难度色条 + 时间 + 分数星级 + 复玩入口）。 */
@Composable
private fun HistoryRecordCard(
    rec: com.example.sudokumaster.data.entity.GameRecordEntity,
    onReplay: (Long) -> Unit
) {
    val dateStr = SimpleDateFormat("M/d HH:mm", Locale.getDefault()).format(Date(rec.date))
    val color = difficultyColor(rec.sizeId, rec.diffId)
    val hasScore = rec.score > 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .lifted(2.dp, RoundedCornerShape(12.dp))
            .clickable { onReplay(rec.id) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 难度色条延伸（与首页难度卡一致）
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 难度图标（与左侧色条同色，位置与成就墙一致）
                        Icon(
                            imageVector = difficultyIcon(
                                BoardDefinitions.difficultyOrFirst(
                                    SizeId.fromId(rec.sizeId),
                                    rec.diffId
                                ).en
                            ),
                            contentDescription = "${rec.diffName}难度图标",
                            tint = color,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${rec.sizeName} · ${rec.diffName}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Text(
                        dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            fmtTime(rec.timeMs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (hasScore) {
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StarRow(stars = rec.stars)
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    "${rec.score} 分",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Text(
                                "点击复玩",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "复玩本局",
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/** 三星评分：实心金星 / 空心暗星。 */
@Composable
private fun StarRow(stars: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..3) {
            val earned = i <= stars
            Icon(
                imageVector = if (earned) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = if (earned) StarGold
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

/** 横向筛选 chip 行（value 为 null 表示「全部」）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipRow(
    options: List<Pair<T?, String>>,
    selected: T?,
    onSelect: (T?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) }
            )
        }
    }
}
