package com.example.sudokumaster.ui.fx

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.sin

/**
 * 通关庆祝音频体系（程序化合成，零音频资源依赖，完全离线）。
 *
 * 组成：
 * - **胜利提示音（stinger）**：C 大调上行琶音 + 结尾和弦 + 高频闪光音，与视觉礼花在同一时刻触发；
 * - **庆祝 BGM**：C–G–Am–F 四小节循环（贝斯 + 垫音 + 琶音），淡入播放，关闭时淡出。
 *
 * 线程模型（保证不阻塞主线程）：
 * - PCM 合成在 [Dispatchers.Default]；
 * - [AudioTrack] 流式写在 [Dispatchers.IO]；
 * - 首次合成后缓存，再次通关零延迟触发。
 *
 * 生命周期：`playVictory()` → （关闭时）`stop()` → `release()`。
 */
class CelebrationAudio(private var config: CelebrationConfig = CelebrationConfig()) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var stingerPcm: FloatArray? = null
    @Volatile private var bgmPcm: FloatArray? = null

    private var bgmJob: kotlinx.coroutines.Job? = null
    @Volatile private var fadeOutAtMs: Long = -1L

    /** 触发整套胜利音频：提示音立即响起，随后 BGM 淡入。 */
    fun playVictory() {
        scope.launch {
            runCatching { playStinger() }
            if (config.bgmVolume > 0f) runCatching { startBgm() }
        }
    }

    /** 停止 BGM（淡出）。提示音很短，让其自然播完。 */
    fun stop() {
        fadeOutAtMs = System.currentTimeMillis()
    }

    /** 释放全部音频资源（页面销毁时调用）。 */
    fun release() {
        scope.cancel()
        bgmJob = null
    }

    fun updateConfig(config: CelebrationConfig) {
        this.config = config
    }

    // ------------------------------------------------------------ 提示音

    private suspend fun playStinger() = withContext(Dispatchers.IO) {
        val pcm = stingerPcm ?: withContext(Dispatchers.Default) { buildStinger() }
            .also { stingerPcm = it }
        if (pcm.isEmpty()) return@withContext
        val track = createStreamTrack()
        try {
            track.play()
            writeAll(track, pcm, config.stingerVolume)
            // 等待播完再释放（本协程在 IO 线程，不阻塞主线程）
            val durationMs = pcm.size * 1000L / SAMPLE_RATE
            val deadline = System.currentTimeMillis() + durationMs + 150L
            while (System.currentTimeMillis() < deadline && isActive) {
                Thread.sleep(20)
            }
        } finally {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    /** C 大调上行琶音 + 结尾大三和弦 + 三连闪光音。 */
    private fun buildStinger(): FloatArray {
        val totalMs = 1_000
        val buf = FloatArray(SAMPLE_RATE * totalMs / 1000)
        // 主琶音 C5 E5 G5 C6
        addNote(buf, 0.0, 523.25, 110.0, 0.90, Wave.SINE)
        addNote(buf, 115.0, 659.25, 110.0, 0.90, Wave.SINE)
        addNote(buf, 230.0, 783.99, 110.0, 0.90, Wave.SINE)
        // 结尾和弦（C 大三 + 高八度 E + 中音 G 垫底）
        addNote(buf, 345.0, 1046.50, 480.0, 1.00, Wave.SINE)
        addNote(buf, 345.0, 1318.51, 480.0, 0.45, Wave.SINE)
        addNote(buf, 345.0, 783.99, 480.0, 0.32, Wave.SINE)
        addNote(buf, 345.0, 523.25, 480.0, 0.28, Wave.SINE)
        // 闪光音
        addNote(buf, 700.0, 2093.00, 30.0, 0.22, Wave.SINE)
        addNote(buf, 755.0, 2349.32, 30.0, 0.18, Wave.SINE)
        addNote(buf, 810.0, 2637.02, 30.0, 0.14, Wave.SINE)
        fadeEdges(buf, 0.02, 0.12)
        return buf
    }

    // ------------------------------------------------------------ BGM

    private suspend fun startBgm() {
        bgmJob?.cancel()
        fadeOutAtMs = -1L
        bgmJob = scope.launch {
            val pcm = bgmPcm ?: withContext(Dispatchers.Default) { buildBgm() }
                .also { bgmPcm = it }
            if (pcm.isEmpty()) return@launch
            val track = createStreamTrack()
            try {
                track.play()
                val chunk = 2048
                var pos = 0
                val startedAt = System.currentTimeMillis()
                while (isActive) {
                    val now = System.currentTimeMillis()
                    var v = (now - startedAt).toFloat() / config.bgmFadeInMs
                    if (fadeOutAtMs > 0) {
                        v = minOf(
                            v,
                            (fadeOutAtMs + config.bgmFadeOutMs - now).toFloat() / config.bgmFadeOutMs
                        )
                    }
                    v = v.coerceIn(0f, 1f) * config.bgmVolume
                    if (fadeOutAtMs > 0 && now >= fadeOutAtMs + config.bgmFadeOutMs) break

                    val out = FloatArray(chunk)
                    var written = 0
                    while (written < chunk) {
                        val n = minOf(chunk - written, pcm.size - pos)
                        for (i in 0 until n) out[written + i] = pcm[pos + i] * v
                        written += n
                        pos += n
                        if (pos >= pcm.size) pos = 0
                    }
                    writeAll(track, out, 1f)
                }
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
        bgmJob?.join()
    }

    private data class Chord(val root: Double, val third: Double, val fifth: Double, val octave: Double)

    /** C–G–Am–F 四小节循环：贝斯 + 持续垫音 + 八分音符琶音。 */
    private fun buildBgm(): FloatArray {
        val total = SAMPLE_RATE * config.bgmLoopSeconds
        val buf = FloatArray(total)
        val chords = listOf(
            Chord(261.63, 329.63, 392.00, 523.25), // C
            Chord(392.00, 493.88, 587.33, 783.99), // G
            Chord(440.00, 523.25, 659.25, 880.00), // Am
            Chord(349.23, 440.00, 523.25, 698.46)  // F
        )
        val barMs = 2000.0
        for (b in chords.indices) {
            val start = b * barMs
            val c = chords[b]
            // 垫音（根 + 五音，慢起慢落）
            addNote(buf, start, c.root, barMs, 0.085, Wave.SINE, tauFactor = 3.2)
            addNote(buf, start, c.fifth, barMs, 0.065, Wave.SINE, tauFactor = 3.2)
            // 贝斯（根音低八度，拨奏）
            addNote(buf, start, c.root / 2, 900.0, 0.20, Wave.TRIANGLE)
            // 琶音（八分音符，强弱交替）
            val tones = listOf(
                c.root, c.third, c.fifth, c.octave,
                c.fifth, c.third, c.octave, c.fifth
            )
            tones.forEachIndexed { i, f ->
                addNote(buf, start + i * 250.0, f, 240.0, if (i % 2 == 0) 0.15 else 0.10, Wave.TRIANGLE)
            }
        }
        // 循环接缝防爆音
        fadeEdges(buf, 0.05, 0.05)
        return buf
    }

    // ------------------------------------------------------------ 合成原语

    private enum class Wave { SINE, TRIANGLE }

    /**
     * 向缓冲区叠加一个音符。
     *
     * @param buf 目标缓冲（-1..1 浮点，可叠加）
     * @param offsetMs 起始时间（毫秒，可为小数）
     * @param freq 频率（Hz）
     * @param durMs 时长（毫秒）
     * @param amp 振幅 0..1
     * @param wave 波形
     * @param tauFactor 衰减时间常数系数：衰减 τ = 时长 / tauFactor，越大越绵长
     */
    private fun addNote(
        buf: FloatArray,
        offsetMs: Double,
        freq: Double,
        durMs: Double,
        amp: Double,
        wave: Wave,
        tauFactor: Double = 4.0
    ) {
        val start = (offsetMs * SAMPLE_RATE / 1000.0).toInt()
        val count = (durMs * SAMPLE_RATE / 1000.0).toInt()
        if (start < 0 || start >= buf.size) return
        val n = minOf(count, buf.size - start)
        val attackSec = 0.008
        val tau = durMs / 1000.0 / tauFactor
        val phase = freq * 0.6180339887 // 黄金比例初相，错开叠加的相位感
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = if (t < attackSec) t / attackSec
            else exp(-(t - attackSec) / tau)
            if (env < 0.0005) continue
            val x = 2.0 * PI * freq * t + phase
            val s = when (wave) {
                Wave.SINE -> sin(x)
                Wave.TRIANGLE -> 2.0 / PI * asin(sin(x))
            }
            buf[start + i] += (amp * env * s).toFloat()
        }
    }

    /** 头尾淡入淡出（秒），消除爆音与循环接缝 click。 */
    private fun fadeEdges(buf: FloatArray, inSec: Double, outSec: Double) {
        val inN = (inSec * SAMPLE_RATE).toInt().coerceAtMost(buf.size / 2)
        val outN = (outSec * SAMPLE_RATE).toInt().coerceAtMost(buf.size / 2)
        for (i in 0 until inN) buf[i] *= (i.toFloat() / inN)
        for (i in 0 until outN) {
            val idx = buf.size - 1 - i
            buf[idx] *= (i.toFloat() / outN)
        }
    }

    // ------------------------------------------------------------ 播放原语

    private fun createStreamTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    /** 流式写入整段 PCM（IO 线程上调用；会自动分块避免超过缓冲）。 */
    private fun writeAll(track: AudioTrack, pcm: FloatArray, volume: Float) {
        val chunk = 2048
        var i = 0
        while (i < pcm.size) {
            val n = minOf(chunk, pcm.size - i)
            val shorts = ShortArray(n) { k ->
                val v = (pcm[i + k] * volume).coerceIn(-1f, 1f)
                (v * 32767f).toInt().toShort()
            }
            val written = track.write(shorts, 0, n)
            if (written < 0) break
            i += written
        }
    }

    companion object {
        /** 采样率：22.05kHz 对合成音足够，减半内存与计算量。 */
        const val SAMPLE_RATE = 22050
    }
}
