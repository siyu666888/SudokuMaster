package com.example.sudokumaster.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 给卡片加极浅阴影 + 发丝级描边，制造「浮起」的层次感（对比页面底色）。
 * 必须在可组合作用域内调用（需读取主题色）。
 */
@Composable
fun Modifier.lifted(
    elevation: Dp = 3.dp,
    shape: Shape = RoundedCornerShape(12.dp)
): Modifier = this
    .shadow(elevation, shape)
    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.07f), shape)

/** 分段控件（如 主题 / 尺寸 切换）。 */
@Composable
fun SegmentedControl(
    items: List<Pair<String, String>>,   // (value, label)
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            items.forEach { (value, label) ->
                val isSel = value == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(value) },
                    shape = RoundedCornerShape(9.dp),
                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 设置页开关行。 */
@Composable
fun SwitchRow(
    label: String,
    desc: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 设置分组标题。 */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.padding(top = 18.dp, bottom = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
}

/**
 * 设置模块卡片：带标题的浅阴影卡片，用于「外观 / 玩法 / 反馈 / 数据」分组。
 * 既统一层级，又把同类开关/控件收纳进一个视觉单元。
 */
@Composable
fun ModuleCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth().lifted(2.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/**
 * 空状态占位：插画 + 标题 + 副标题 + 可选操作按钮。
 * 用于历史记录、成就页在无数据时给予明确引导。
 */
@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit = { DefaultEmptyIllustration() },
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        illustration()
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** 默认空状态插画：柔色卡片 + 时钟 + 星点点缀，全程使用主题色。 */
@Composable
private fun DefaultEmptyIllustration() {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier = Modifier.size(140.dp)) {
        val w = size.width
        val h = size.height
        // 背景圆角卡片
        drawRoundRect(
            color = colors.primaryContainer,
            topLeft = Offset(w * 0.12f, h * 0.16f),
            size = Size(w * 0.76f, h * 0.68f),
            cornerRadius = CornerRadius(w * 0.08f)
        )
        // 时钟外圈
        drawCircle(
            color = colors.primary,
            radius = w * 0.20f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.025f)
        )
        // 指针
        drawLine(colors.primary, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.5f, h * 0.36f), strokeWidth = w * 0.02f)
        drawLine(colors.primary, Offset(w * 0.5f, h * 0.5f), Offset(w * 0.62f, h * 0.55f), strokeWidth = w * 0.02f)
        // 星点点缀
        drawCircle(colors.primary.copy(alpha = 0.5f), radius = w * 0.03f, center = Offset(w * 0.22f, h * 0.22f))
        drawCircle(colors.primary.copy(alpha = 0.4f), radius = w * 0.02f, center = Offset(w * 0.8f, h * 0.3f))
        drawCircle(colors.primary.copy(alpha = 0.45f), radius = w * 0.018f, center = Offset(w * 0.78f, h * 0.72f))
    }
}

/** 空状态用的「放大镜 + 问号」插画，用于成就页。 */
@Composable
internal fun SearchEmptyIllustration() {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier = Modifier.size(140.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = colors.primaryContainer,
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.08f)
        )
        // 放大镜圈
        drawCircle(
            color = colors.primary,
            radius = w * 0.17f,
            center = Offset(w * 0.44f, h * 0.46f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.025f)
        )
        // 手柄
        drawLine(
            colors.primary,
            Offset(w * 0.57f, h * 0.59f),
            Offset(w * 0.7f, h * 0.72f),
            strokeWidth = w * 0.03f
        )
        // 闪星
        drawCircle(colors.primary.copy(alpha = 0.5f), radius = w * 0.025f, center = Offset(w * 0.76f, h * 0.28f))
    }
}
