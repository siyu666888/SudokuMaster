package com.example.sudokumaster.ui.fx

import com.example.sudokumaster.domain.model.WinInfo

/**
 * 通关庆祝控制器——庆祝效果的统一入口，负责「初始化 → 触发 → 播放控制 → 释放」全生命周期。
 *
 * 集成方式（3 行接线）：
 * ```kotlin
 * val celebration = remember { CelebrationController() }
 * DisposableEffect(Unit) { onDispose { celebration.release() } }   // 页面销毁时释放
 * // 胜利时（winInfo 变为非空）：
 * LaunchedEffect(winInfo) { winInfo?.let { celebration.play(it) } }
 * ```
 *
 * 职责划分：
 * - 音频（提示音 + BGM 切换）由内部 [CelebrationAudio] 全异步处理，不阻塞主线程；
 * - 粒子 / 面板动画由 [WinCelebrationOverlay] 自行驱动，控制器只负责触发时机与配置；
 * - [soundEnabled] 供设置页「提示音」开关联动（见 [CelebrationConfig.respectSoundSetting]）。
 */
class CelebrationController(config: CelebrationConfig = CelebrationConfig()) {

    private var _config: CelebrationConfig = config
    private val audio = CelebrationAudio(config)

    /** 当前配置（可读；修改请用 [updateConfig]）。 */
    val config: CelebrationConfig get() = _config

    /** 最近一次庆祝的胜利信息（供调试 / 扩展用，如按星级调整特效强度）。 */
    var lastWinInfo: com.example.sudokumaster.domain.model.WinInfo? = null
        private set

    /** 外部声音开关（设置页「提示音」）。 */
    var soundEnabled: Boolean = true

    /** 初始化 / 预热（音频对象惰性就绪，此处预留预热点）。 */
    fun initialize() {
        // 预留：如需预热 PCM 缓存，可在此调用 audio.preheat()
    }

    /**
     * 触发一整套庆祝效果（与视觉礼花同一时刻）。
     * 尊重 [CelebrationConfig.enabled] 与声音开关；音频失败静默降级，绝不影响游戏。
     */
    fun play(info: WinInfo) {
        lastWinInfo = info
        if (!_config.enabled) return
        val muted = _config.respectSoundSetting && !soundEnabled
        if (!muted) audio.playVictory()
    }

    /** 播放控制：停止 BGM（淡出）。 */
    fun stop() {
        audio.stop()
    }

    /** 资源释放：取消所有音频协程并释放 AudioTrack。 */
    fun release() {
        audio.release()
    }

    /** 热更新配置（音频参数在下次播放时生效）。 */
    fun updateConfig(config: CelebrationConfig) {
        _config = config
        audio.updateConfig(config)
    }
}
