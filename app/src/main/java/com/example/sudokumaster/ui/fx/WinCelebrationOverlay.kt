package com.example.sudokumaster.ui.fx

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sudokumaster.domain.model.WinInfo
import com.example.sudokumaster.util.fmtTime
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 通关界面的用户操作。 */
private enum class WinAction { REPLAY, HOME, DISMISS }

/** 面板退场时长（毫秒），与退出动画保持一致。 */
private const val EXIT_ANIM_MS = 240L

private var playKeySeq = 0
private fun nextPlayKey(): Int = ++playKeySeq

/**
 * 通关庆祝全屏界面。
 *
 * 组成与行为：
 * 1. **居中信息面板**：关卡名称、得分（滚动计数）、评级徽章、星级、用时/错误/提示、操作按钮；
 * 2. **视觉反馈**：面板弹簧入场 + 元素错峰淡入、吉祥物弹跳/落地挤压/挥手庆祝动作、
 *    双角礼花炮 + 中央爆发 + 顶部飘落的三层粒子系统（[CelebrationParticles]）；
 * 3. **音频同步**：出现瞬间由 [CelebrationController] 触发胜利提示音与 BGM 淡入，
 *    退场时 BGM 淡出；
 * 4. **非阻塞**：粒子轨迹在后台线程生成、帧时钟驱动；音频全异步；得分计数走动画框架；
 * 5. **生命周期**：入场（[LaunchedEffect] 触发）→ 退场（先播退出动画再回调）→
 *    [DisposableEffect] 停止音频。
 *
 * @param info 胜利信息（关卡名/得分/星级/评级/用时等）
 * @param config 庆祝配置
 * @param soundEnabled 设置页声音开关（经控制器联动）
 * @param controller 庆祝控制器（音频与生命周期）
 * @param onReplay 再来一局
 * @param onHome 返回首页
 * @param onDismiss 直接关闭（点击遮罩）
 */
@Composable
fun WinCelebrationOverlay(
    info: WinInfo,
    config: CelebrationConfig,
    soundEnabled: Boolean,
    controller: CelebrationController,
    onReplay: () -> Unit,
    onHome: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var visible by remember(info) { mutableStateOf(false) }
    var pending by remember(info) { mutableStateOf<WinAction?>(null) }
    val dismissing = pending != null
    val playKey = remember(info) { nextPlayKey() }

    // 入场：音频与视觉礼花在同一帧触发（精确同步）
    LaunchedEffect(info) {
        controller.soundEnabled = soundEnabled
        visible = true
        controller.play(info)
    }
    // 退场：先播退出动画，再执行用户操作；音频同步淡出
    LaunchedEffect(pending) {
        val action = pending ?: return@LaunchedEffect
        delay(EXIT_ANIM_MS)
        when (action) {
            WinAction.REPLAY -> onReplay()
            WinAction.HOME -> onHome()
            WinAction.DISMISS -> onDismiss()
        }
    }
    // 离开组合时停止音频（BGM 淡出）
    DisposableEffect(info) {
        onDispose { controller.stop() }
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible && !dismissing) 0.82f else 0f,
        animationSpec = tween(300),
        label = "scrim"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { if (pending == null) pending = WinAction.DISMISS },
        contentAlignment = Alignment.Center
    ) {
        // 粒子层（面板之后）
        CelebrationParticles(
            playKey = playKey,
            config = config,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = visible && !dismissing,
            enter = fadeIn(tween(240)) +
                scaleIn(
                    animationSpec = spring(
                        dampingRatio = 0.55f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    initialScale = 0.7f
                ) +
                slideInVertically(tween(config.panelEntranceMs.toInt())) { it / 4 },
            exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.85f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 角色庆祝动作
                CelebratingMascot(
                    modifier = Modifier
                        .size(112.dp)
                        .padding(bottom = 4.dp)
                )
                // 居中通关信息面板
                WinPanel(
                    info = info,
                    config = config,
                    onReplay = { if (pending == null) pending = WinAction.REPLAY },
                    onHome = { if (pending == null) pending = WinAction.HOME },
                    modifier = Modifier.widthIn(max = 340.dp)
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 面板

@Composable
private fun WinPanel(
    info: WinInfo,
    config: CelebrationConfig,
    onReplay: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val goldEdge = Brush.linearGradient(
        listOf(Color(0xFFFFD700), Color(0xFFFF9F43), Color(0xFFFFE066), Color(0xFFFFD700))
    )

    Box(
        modifier = modifier
            .shadow(18.dp, RoundedCornerShape(24.dp))
            .background(scheme.surface, RoundedCornerShape(24.dp))
            .border(1.6.dp, goldEdge, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题 + 关卡名
            StaggerIn(0, config.elementStaggerMs) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "通关！",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.size(2.dp))
                    Text(
                        info.levelName.ifBlank { info.diffName },
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.size(10.dp))

            // 星级
            StaggerIn(1, config.elementStaggerMs) {
                StarRow(stars = info.stars)
            }

            Spacer(Modifier.size(10.dp))

            // 得分（滚动计数）+ 评级徽章
            StaggerIn(2, config.elementStaggerMs) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val scoreAnim = remember(info) { Animatable(0f) }
                    LaunchedEffect(info) {
                        scoreAnim.animateTo(
                            targetValue = info.score.toFloat(),
                            animationSpec = tween(
                                durationMillis = config.scoreCountUpMs.toInt(),
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    Text(
                        text = scoreAnim.value.toInt().toString(),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary
                    )
                    Text(
                        " 分",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(10.dp))
                    RankBadge(rank = info.rank)
                }
            }

            Spacer(Modifier.size(12.dp))

            // 数据行：用时 / 错误 / 提示
            StaggerIn(3, config.elementStaggerMs) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCell("用时", fmtTime(info.timeMs))
                    StatCell("错误", info.mistakes.toString())
                    StatCell("提示", info.hintsUsed.toString())
                }
            }

            // 新纪录徽章
            if (info.isBest) {
                Spacer(Modifier.size(10.dp))
                StaggerIn(4, config.elementStaggerMs) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFFC107).copy(alpha = 0.18f)
                    ) {
                        Text(
                            "🏆 刷新本难度最佳记录",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB8860B)
                        )
                    }
                }
            }

            Spacer(Modifier.size(16.dp))

            // 操作按钮
            StaggerIn(if (info.isBest) 5 else 4, config.elementStaggerMs) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onReplay) { Text("再来一局") }
                    OutlinedButton(onClick = onHome) { Text("返回首页") }
                }
            }
        }
    }
}

@Composable
private fun StarRow(stars: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..3) {
            val filled = i <= stars
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(260L + i * 150L)
                shown = true
            }
            AnimatedVisibility(
                visible = shown,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = 0.35f,
                        stiffness = Spring.StiffnessMedium
                    ),
                    initialScale = 0.2f
                ) + fadeIn(tween(160))
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (filled) Color(0xFFFFC107) else Color.Gray.copy(alpha = 0.45f),
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(30.dp)
                )
            }
        }
    }
}

@Composable
private fun RankBadge(rank: String) {
    val (bg, fg) = when (rank) {
        "S" -> Color(0xFFFFD700) to Color(0xFF5C4300)
        "A" -> Color(0xFFB388FF) to Color(0xFF3B1D70)
        "B" -> Color(0xFF64B5F6) to Color(0xFF0D3C61)
        else -> Color(0xFFB0BEC5) to Color(0xFF2A373D)
    }
    Surface(shape = CircleShape, color = bg) {
        Text(
            text = rank,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = fg
        )
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 面板内元素错峰淡入。 */
@Composable
private fun StaggerIn(index: Int, staggerMs: Long, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * staggerMs)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 3 }
    ) { content() }
}

// ------------------------------------------------------------------ 角色

/**
 * 庆祝吉祥物：金色小球冠军，戴星冠、举奖杯，持续弹跳 + 落地挤压 + 挥手。
 * 纯 Compose Canvas 绘制，无图片资源；动画走无限过渡，掉帧自动补偿。
 */
@Composable
private fun CelebratingMascot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition()

    // 弹跳（跳跃位移）
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                0f at 0 using FastOutSlowInEasing
                -26f at 280 using FastOutSlowInEasing
                0f at 560 using FastOutSlowInEasing
                0f at 900
            }
        ),
        label = "bounce"
    )
    // 落地挤压（体积近似守恒：sx = 2 - squash）
    val squash by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                1f at 0
                1f at 540
                0.86f at 600
                1.05f at 660
                1f at 900
            }
        ),
        label = "squash"
    )
    // 挥手（左右手臂反向摆动）
    val wave by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(620, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave"
    )

    Canvas(modifier = modifier) {
        val s = size.minDimension
        val cx = size.width / 2f
        val groundY = size.height * 0.94f

        // 阴影（随弹跳微微缩放）
        val shadowScale = 1f + bounce / 60f
        drawOval(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(cx - 34f * shadowScale, groundY - 8f),
            size = Size(68f * shadowScale, 16f)
        )

        // 角色整体：位移 + 以脚底为锚点的挤压
        translate(top = bounce.dp.toPx()) {
            // 注意：translate 内部坐标系已随位移平移，锚点仍为 groundY
            scale(
                scaleX = 2f - squash,
                scaleY = squash,
                pivot = Offset(cx, groundY)
            ) {
                drawMascot(cx, s, wave)
            }
        }
    }
}

/** 绘制吉祥物本体（坐标已由外层完成位移/挤压变换）。 */
private fun DrawScope.drawMascot(cx: Float, s: Float, wave: Float) {
    val bodyR = s * 0.30f
    val bodyCy = size.height * 0.58f

    // 手臂（挥手，先画位于身后侧）
    val armLen = s * 0.16f
    val armW = s * 0.075f
    val shoulderY = bodyCy + bodyR * 0.1f
    drawArm(cx - bodyR * 0.92f, shoulderY, armLen, armW, -28f + wave * 22f)
    drawArm(cx + bodyR * 0.92f, shoulderY, armLen, armW, 28f - wave * 22f)

    // 身体（金色径向渐变）
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFE98A), Color(0xFFFFB703), Color(0xFFF59E00)),
            center = Offset(cx - bodyR * 0.3f, bodyCy - bodyR * 0.35f),
            radius = bodyR * 1.5f
        ),
        radius = bodyR,
        center = Offset(cx, bodyCy)
    )
    // 肚皮高光
    drawCircle(
        color = Color.White.copy(alpha = 0.22f),
        radius = bodyR * 0.55f,
        center = Offset(cx, bodyCy + bodyR * 0.18f)
    )

    // 右手奖杯
    drawTrophy(cx + bodyR * 0.92f + armLen * 0.55f, shoulderY - armLen * 0.72f, s * 0.085f)

    // 眼睛（开心闭眼 ^^）
    val eyeR = s * 0.045f
    val eyeY = bodyCy - bodyR * 0.16f
    drawArc(
        color = Color(0xFF4A2C00),
        startAngle = 200f,
        sweepAngle = 220f,
        useCenter = false,
        topLeft = Offset(cx - bodyR * 0.52f - eyeR, eyeY - eyeR),
        size = Size(eyeR * 2, eyeR * 2),
        style = Stroke(width = s * 0.022f, cap = StrokeCap.Round)
    )
    drawArc(
        color = Color(0xFF4A2C00),
        startAngle = 200f,
        sweepAngle = 220f,
        useCenter = false,
        topLeft = Offset(cx + bodyR * 0.52f - eyeR, eyeY - eyeR),
        size = Size(eyeR * 2, eyeR * 2),
        style = Stroke(width = s * 0.022f, cap = StrokeCap.Round)
    )
    // 微笑
    drawArc(
        color = Color(0xFF4A2C00),
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(cx - bodyR * 0.26f, bodyCy + bodyR * 0.02f),
        size = Size(bodyR * 0.52f, bodyR * 0.42f),
        style = Stroke(width = s * 0.026f, cap = StrokeCap.Round)
    )
    // 腮红
    drawCircle(
        color = Color(0xFFFF8A80).copy(alpha = 0.55f),
        radius = s * 0.032f,
        center = Offset(cx - bodyR * 0.62f, bodyCy + bodyR * 0.22f)
    )
    drawCircle(
        color = Color(0xFFFF8A80).copy(alpha = 0.55f),
        radius = s * 0.032f,
        center = Offset(cx + bodyR * 0.62f, bodyCy + bodyR * 0.22f)
    )
    // 头顶星冠
    val crown = starPath(cx, bodyCy - bodyR - s * 0.055f, s * 0.075f)
    drawPath(crown, Color(0xFFFFD700))
    drawPath(crown, Color(0xFFB8860B), style = Stroke(width = s * 0.012f))
}

/** 画一条圆角手臂（angle 为与垂直方向夹角，度）。 */
private fun DrawScope.drawArm(
    x: Float,
    y: Float,
    length: Float,
    width: Float,
    angleDeg: Float
) {
    rotate(degrees = angleDeg, pivot = Offset(x, y)) {
        drawRoundRect(
            color = Color(0xFFFFB703),
            topLeft = Offset(x - width / 2f, y),
            size = Size(width, length),
            cornerRadius = CornerRadius(width / 2f)
        )
        // 手
        drawCircle(
            color = Color(0xFFFFE98A),
            radius = width * 0.62f,
            center = Offset(x, y + length)
        )
    }
}

/** 画一座小奖杯。 */
private fun DrawScope.drawTrophy(cx: Float, cy: Float, r: Float) {
    // 杯身
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFFFE98A), Color(0xFFFFC107))),
        topLeft = Offset(cx - r * 0.55f, cy - r),
        size = Size(r * 1.1f, r * 1.15f),
        cornerRadius = CornerRadius(r * 0.28f)
    )
    // 双耳
    drawArc(
        color = Color(0xFFFFC107),
        startAngle = 90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - r * 0.95f, cy - r * 0.92f),
        size = Size(r * 0.42f, r * 0.7f),
        style = Stroke(width = r * 0.13f, cap = StrokeCap.Round)
    )
    drawArc(
        color = Color(0xFFFFC107),
        startAngle = 270f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx + r * 0.53f, cy - r * 0.92f),
        size = Size(r * 0.42f, r * 0.7f),
        style = Stroke(width = r * 0.13f, cap = StrokeCap.Round)
    )
    // 杯柄与底座
    drawRect(
        color = Color(0xFFFFC107),
        topLeft = Offset(cx - r * 0.09f, cy + r * 0.15f),
        size = Size(r * 0.18f, r * 0.3f)
    )
    drawRoundRect(
        color = Color(0xFFB8860B),
        topLeft = Offset(cx - r * 0.4f, cy + r * 0.42f),
        size = Size(r * 0.8f, r * 0.16f),
        cornerRadius = CornerRadius(r * 0.06f)
    )
    // 杯中星
    drawPath(starPath(cx, cy - r * 0.4f, r * 0.22f), Color(0xFFFFF8E1))
}

/** 五角星路径。 */
private fun starPath(cx: Float, cy: Float, r: Float): Path {
    val path = Path()
    val inner = r * 0.42f
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else inner
        val a = (-PI / 2 + i * PI / 5).toFloat()
        val px = cx + rad * cos(a)
        val py = cy + rad * sin(a)
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    return path
}
