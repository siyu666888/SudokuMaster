package com.example.sudokumaster.ui.fx

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** 粒子形态。 */
enum class ConfettiShape { RECT, CIRCLE, RIBBON, STAR }

/** 默认庆祝配色（明亮节庆色，深色遮罩上均可读）。 */
val CelebrationPalette: List<Color> = listOf(
    Color(0xFFFFD700), // 金
    Color(0xFFFF6B6B), // 珊瑚红
    Color(0xFF4ECDC4), // 青
    Color(0xFFFF9F43), // 橙
    Color(0xFFA66CFF), // 紫
    Color(0xFF54A0FF), // 蓝
    Color(0xFFFF5E8A), // 粉
    Color(0xFF95E06C)  // 绿
)

/** 单个彩带粒子（位置/速度均为归一化值，与画布尺寸解耦）。 */
internal class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val gravity: Float,
    val drag: Float,
    val sizeDp: Float,
    var rot: Float,
    val vr: Float,
    val color: Color,
    val shape: ConfettiShape,
    val maxLife: Float,
    val swayAmp: Float,
    val swayFreq: Float,
    val swayPhase: Float
) {
    var life: Float = maxLife
}

/**
 * 通关礼花粒子场（非阻塞）。
 *
 * - 轨迹在 [Dispatchers.Default] 上批量生成，主线程零计算开销；
 * - 帧循环由帧时钟（[withInfiniteAnimationFrameMillis]）驱动，掉帧自动补偿 dt；
 * - 粒子全部消亡后循环自动退出，不产生空转帧；
 * - 通过 [playKey] 变化触发一次发射（传入胜利信息的唯一标识即可）。
 *
 * @param playKey 变化即发射一轮；<=0 不发射
 * @param config 庆祝配置（粒子数量/寿命/礼花炮占比等）
 * @param colors 粒子配色
 */
@Composable
fun CelebrationParticles(
    playKey: Int,
    config: CelebrationConfig,
    modifier: Modifier = Modifier,
    colors: List<Color> = CelebrationPalette
) {
    val particles = remember { ArrayList<ConfettiParticle>(config.particleCount + 16) }
    val frameTick = remember { mutableIntStateOf(0) }

    LaunchedEffect(playKey) {
        if (playKey <= 0 || config.particleCount <= 0) return@LaunchedEffect
        val batch = withContext(Dispatchers.Default) {
            buildBatch(config, colors)
        }
        particles.addAll(batch)
        var lastFrame = 0L
        while (particles.isNotEmpty()) {
            withInfiniteAnimationFrameMillis { frame ->
                if (lastFrame != 0L) {
                    val dt = ((frame - lastFrame) / 1000.0).toFloat().coerceIn(0f, 0.05f)
                    step(particles, dt)
                }
                lastFrame = frame
                frameTick.intValue++   // 触发 Canvas 重绘
            }
        }
    }

    Canvas(modifier = modifier) {
        // 读取帧计数建立重绘依赖
        @Suppress("UNUSED_EXPRESSION") frameTick.intValue
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        for (p in particles) {
            val fadeIn = 0.08f
            val t = p.life / p.maxLife
            val alpha = when {
                t > 1f - fadeIn -> 1f
                else -> (t / 0.75f).coerceIn(0f, 1f)
            }
            val cx = p.x * w
            val cy = p.y * h
            val s = p.sizeDp.dp.toPx()
            val c = p.color.copy(alpha = alpha)
            when (p.shape) {
                ConfettiShape.RECT -> rotate(p.rot, Offset(cx, cy)) {
                    drawRect(c, Offset(cx - s / 2f, cy - s * 0.32f), Size(s, s * 0.64f))
                }
                ConfettiShape.CIRCLE -> drawCircle(c, radius = s / 2f, center = Offset(cx, cy))
                ConfettiShape.RIBBON -> rotate(p.rot, Offset(cx, cy)) {
                    drawRoundRect(
                        color = c,
                        topLeft = Offset(cx - s * 0.14f, cy - s * 0.8f),
                        size = Size(s * 0.28f, s * 1.6f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.14f)
                    )
                }
                ConfettiShape.STAR -> {
                    val path = starPath(cx, cy, s / 2f)
                    drawPath(path, c)
                }
            }
        }
    }
}

/** 推进一帧（主线程帧时钟内调用，仅简单算术）。 */
private fun step(list: MutableList<ConfettiParticle>, dt: Float) {
    for (i in list.indices.reversed()) {
        val p = list[i]
        p.life -= dt
        if (p.life <= 0f) {
            list.removeAt(i)
            continue
        }
        p.vy += p.gravity * dt
        if (p.drag > 0f) {
            val k = (1f - p.drag * dt).coerceAtLeast(0f)
            p.vx *= k
            p.vy *= k
        }
        val age = p.maxLife - p.life
        p.x += (p.vx + sin(age * p.swayFreq + p.swayPhase) * p.swayAmp) * dt
        p.y += p.vy * dt
        p.rot += p.vr * dt
    }
}

/** 批量生成一轮礼花：双上角礼花炮 + 顶部飘落 + 中央爆发。 */
internal fun buildBatch(config: CelebrationConfig, colors: List<Color>): List<ConfettiParticle> {
    val rng = Random(System.nanoTime())
    val total = config.particleCount
    val list = ArrayList<ConfettiParticle>(total)
    val lifeScale = config.particleDurationMs / 1000f

    fun add(
        x: Float, y: Float, vx: Float, vy: Float,
        gravity: Float, drag: Float, sizeDp: Float,
        color: Color, shape: ConfettiShape, life: Float,
        swayAmp: Float = 0f, swayFreq: Float = 6f
    ) {
        list.add(
            ConfettiParticle(
                x = x, y = y, vx = vx, vy = vy,
                gravity = gravity, drag = drag,
                sizeDp = sizeDp,
                rot = rng.nextFloat() * 360f,
                vr = (rng.nextFloat() - 0.5f) * 720f,
                color = color, shape = shape,
                maxLife = life,
                swayAmp = swayAmp,
                swayFreq = swayFreq,
                swayPhase = rng.nextFloat() * 2f * PI.toFloat()
            )
        )
    }

    fun shapeFor(): ConfettiShape = when (rng.nextInt(10)) {
        in 0..4 -> ConfettiShape.RECT
        in 5..6 -> ConfettiShape.CIRCLE
        in 7..8 -> ConfettiShape.RIBBON
        else -> ConfettiShape.STAR
    }

    // 1) 双上角礼花炮（向内下方喷射）
    val cannonCount = (total * config.cornerCannonRatio).toInt()
    for (i in 0 until cannonCount) {
        val fromLeft = i % 2 == 0
        val speed = 0.45f + rng.nextFloat() * 0.30f
        val angle = (72f + rng.nextFloat() * 36f) * PI.toFloat() / 180f // 与水平夹角
        val dirX = if (fromLeft) 1f else -1f
        add(
            x = if (fromLeft) 0.02f else 0.98f,
            y = -0.02f,
            vx = dirX * speed * kotlin.math.cos(angle),
            vy = speed * sin(angle) * 0.9f,
            gravity = 0.62f, drag = 0f,
            sizeDp = 6f + rng.nextFloat() * 5f,
            color = colors[rng.nextInt(colors.size)],
            shape = shapeFor(),
            life = (1.6f + rng.nextFloat() * 1.2f) * lifeScale
        )
    }

    // 2) 顶部飘落
    val rainCount = (total - cannonCount) / 2
    for (i in 0 until rainCount) {
        add(
            x = rng.nextFloat() * 0.96f + 0.02f,
            y = -0.02f - rng.nextFloat() * 0.05f,
            vx = (rng.nextFloat() - 0.5f) * 0.06f,
            vy = 0.10f + rng.nextFloat() * 0.08f,
            gravity = 0.05f, drag = 0f,
            sizeDp = 5f + rng.nextFloat() * 4f,
            color = colors[rng.nextInt(colors.size)],
            shape = shapeFor(),
            life = (2.2f + rng.nextFloat() * 1.2f) * lifeScale,
            swayAmp = 0.05f + rng.nextFloat() * 0.06f,
            swayFreq = 3f + rng.nextFloat() * 3f
        )
    }

    // 3) 中央爆发（从面板位置径向喷射）
    if (config.centerBurst) {
        val burstCount = total - cannonCount - rainCount
        for (i in 0 until burstCount) {
            val angle = rng.nextFloat() * 2f * PI.toFloat()
            val speed = 0.30f + rng.nextFloat() * 0.42f
            add(
                x = 0.5f + (rng.nextFloat() - 0.5f) * 0.1f,
                y = 0.40f + (rng.nextFloat() - 0.5f) * 0.08f,
                vx = speed * kotlin.math.cos(angle),
                vy = speed * sin(angle) * 0.8f,
                gravity = 0.55f, drag = 0.9f,
                sizeDp = 5f + rng.nextFloat() * 5f,
                color = colors[rng.nextInt(colors.size)],
                shape = shapeFor(),
                life = (1.4f + rng.nextFloat() * 1.0f) * lifeScale
            )
        }
    }
    return list
}

/** 五角星路径（外径 r）。 */
private fun starPath(cx: Float, cy: Float, r: Float): Path {
    val path = Path()
    val inner = r * 0.42f
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else inner
        val a = (-PI / 2 + i * PI / 5).toFloat()
        val px = cx + rad * kotlin.math.cos(a)
        val py = cy + rad * sin(a)
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    return path
}
