package com.example.sudokumaster.ui.achievements

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sudokumaster.SudokuApplication
import com.example.sudokumaster.domain.game.StatsAnalyzer
import com.example.sudokumaster.domain.model.BoardDefinitions
import com.example.sudokumaster.domain.model.SizeId
import com.example.sudokumaster.ui.components.EmptyState
import com.example.sudokumaster.ui.components.ModuleCard
import com.example.sudokumaster.ui.components.SearchEmptyIllustration
import com.example.sudokumaster.ui.components.lifted
import com.example.sudokumaster.util.difficultyColor
import com.example.sudokumaster.util.difficultyIcon
import com.example.sudokumaster.util.fmtTime
import com.example.sudokumaster.util.historyDateLabel
import com.example.sudokumaster.viewmodel.AchRow
import com.example.sudokumaster.viewmodel.AchievementsViewModel
import com.example.sudokumaster.viewmodel.vmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    onStartLevel: (sizeId: String, diffId: Int) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SudokuApplication
    val vm: AchievementsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = vmFactory { AchievementsViewModel(app.container) }
    )
    val total by vm.totalCount.collectAsStateWithLifecycle()
    val hell by vm.hellCount.collectAsStateWithLifecycle()
    val overview by vm.overview.collectAsStateWithLifecycle()
    val lastPlayed by vm.lastPlayed.collectAsStateWithLifecycle()
    val groupStats by vm.groupStats.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("成就") },
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // 汇总卡：跟随主题主色，浅阴影浮起
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).lifted(2.dp, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryItem("$total", "总完成局数")
                    SummaryItem(overview.avgTimeMs?.let { fmtTime(it) } ?: "--", "平均用时")
                    SummaryItem("${overview.currentStreakDays}", "连续获胜(天)")
                    SummaryItem("$hell", "地狱通关")
                }
            }

            Spacer(Modifier.height(14.dp))

            // 数据统计明细
            ModuleCard(title = "数据统计") {
                StatLine("平均用时", overview.avgTimeMs?.let { fmtTime(it) } ?: "--")
                StatLine("最快用时", overview.bestTimeMs?.let { fmtTime(it) } ?: "--")
                StatLine("当前连续获胜", "${overview.currentStreakDays} 天")
                StatLine("最长连续获胜", "${overview.longestStreakDays} 天")
                StatLine("活跃天数", "${overview.activeDays} 天")
                StatLine(
                    "最近一局",
                    lastPlayed?.let { historyDateLabel(it) } ?: "--"
                )
            }

            Spacer(Modifier.height(14.dp))

            // 各难度通关分布
            ModuleCard(title = "各难度通关分布") {
                if (groupStats.isEmpty()) {
                    Text(
                        "完成任意一局后，这里会展示各难度的完成占比",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    groupStats.forEach { gs ->
                        val size = SizeId.fromId(gs.sizeId)
                        val diff = BoardDefinitions.difficultyOrFirst(size, gs.diffId)
                        DistributionBar(
                            label = "${size.label} · ${diff.name}",
                            count = gs.count,
                            total = total,
                            color = difficultyColor(gs.sizeId, gs.diffId)
                        )
                    }
                }
            }

            // 成就墙标题：与上方模块拉开间距，加粗引导
            Spacer(Modifier.height(22.dp))
            Text(
                "成就墙",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            if (total == 0) {
                EmptyState(
                    title = "还没有成就",
                    subtitle = "完成任意一局即可解锁各难度成就",
                    illustration = { SearchEmptyIllustration() }
                )
            } else {
                // 按棋盘尺寸分区，区间距 22dp：已解锁=主题色高亮+阴影，未解锁=降饱和置灰
                Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    var cardIndex = 0
                    rows.groupBy { it.sizeId }.forEach { (sizeId, items) ->
                        val size = SizeId.fromId(sizeId)
                        val done = items.count { it.count > 0 }
                        // 区块头：主色短竖条 + 尺寸名 + 通关进度
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 4.dp, height = 14.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                size.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "$done / ${items.size} 已通关",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { row ->
                                        AchievementCard(
                                            row = row,
                                            index = cardIndex++,
                                            onClick = { onStartLevel(row.sizeId, row.diffId) },
                                            onShare = { shareAchievement(context, row) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 成就网格卡片（v1.8.0：去除边框 / 阴影 / 线宽，仅保留底色与难度色条）。
 * 已解锁：主题主色淡背景，奖杯/局数统一主题色；
 * 未解锁：低饱和灰底 + 「前往挑战」引导（无多余装饰线）。
 * 左侧竖条 + 名称前图标为该难度专属色/图标（与历史记录、首页难度卡一致）。
 * 交互：点击进入该难度新局；长按（已解锁）分享成绩；按下时微缩放 + 底色加深反馈；
 * 入场时按序号错峰弹入（奖杯弹出感）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AchievementCard(
    row: AchRow,
    index: Int,
    onClick: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unlocked = row.count > 0
    val shape = RoundedCornerShape(16.dp)
    val barColor = difficultyColor(row.sizeId, row.diffId)

    // 入场动画：按全局序号错峰弹入
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 45L)
        appeared = true
    }
    val enter by animateFloatAsState(if (appeared) 1f else 0f, label = "card-enter")

    // 按下态：微缩放 + 背景微变（无发光阴影）
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, label = "card-press")

    val baseBgAlpha = if (unlocked) 0.10f else 0.35f
    val containerColor = if (unlocked) {
        MaterialTheme.colorScheme.primary.copy(alpha = baseBgAlpha + 0.05f * press)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = baseBgAlpha + 0.08f * press)
    }
    val totalScale = (0.92f + 0.08f * enter) * (1f - 0.035f * press)

    Surface(
        modifier = modifier
            .height(142.dp)
            // 无边框、无阴影、线宽为零：状态仅由底色 + 难度色条 + 图标表达
            .graphicsLayer {
                alpha = enter
                scaleX = totalScale
                scaleY = totalScale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = if (unlocked) onShare else null
            ),
        shape = shape,
        color = containerColor
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // 难度专属色条（未解锁时压暗，保持灰度观感）
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        if (unlocked) barColor else barColor.copy(alpha = 0.45f),
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            )
            Column(modifier = Modifier.weight(1f).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 难度图标（与左侧色条同色，位置与历史记录一致）
                    Icon(
                        imageVector = difficultyIcon(
                            BoardDefinitions.difficultyOrFirst(
                                SizeId.fromId(row.sizeId),
                                row.diffId
                            ).en
                        ),
                        contentDescription = null,
                        tint = if (unlocked) barColor else barColor.copy(alpha = 0.55f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "${row.sizeName} · ${row.diffName}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    // 状态图标置于行尾：奖杯（已解锁）/ 锁（未解锁）
                    Icon(
                        imageVector = if (unlocked) Icons.Outlined.EmojiEvents else Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = if (unlocked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                if (unlocked) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${row.count} 局",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    StatRow("最快", row.best?.let { fmtTime(it) } ?: "--")
                    StatRow("平均", row.avg?.let { fmtTime(it) } ?: "--")
                } else {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "未解锁",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "完成一局即可解锁",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    // 底部仅保留挑战引导，不再放置装饰线，布局干净统一
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(10.dp))
                    // 弱化挑战引导：整卡可点，此处给文字暗示
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "前往挑战",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 卡片内数据行：左对齐、标签弱化 + 数值加粗，行高加大提升可读性。 */
@Composable
private fun StatRow(label: String, value: String) {
    val style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(
            text = value,
            style = style,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 通关分布条：标签 + 占比条 + 数值。 */
@Composable
private fun DistributionBar(label: String, count: Int, total: Int, color: Color) {
    val target = if (total > 0) count.toFloat() / total else 0f
    val frac by animateFloatAsState(targetValue = target, label = "dist")
    val percent = (target * 100).toInt()
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$count 局 · $percent%",
                style = MaterialTheme.typography.labelLarge,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac.coerceIn(0.015f, 1f))
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SummaryItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 长按分享成就战绩（纯文本，走系统分享面板）。 */
private fun shareAchievement(context: Context, row: AchRow) {
    val text = buildString {
        append("我在《数独大师》完成了「${row.sizeName} · ${row.diffName}」")
        append("，共 ${row.count} 局")
        row.best?.let { append("，最快 ${fmtTime(it)}") }
        row.bestScore?.takeIf { it > 0 }?.let { append("，最高 $it 分") }
        append("！快来挑战吧～")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享成就"))
}
