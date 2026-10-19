package com.example.sudokumaster.ui.fx

/**
 * 通关庆祝效果的配置。
 *
 * 用法：修改字段后通过 `CelebrationController.updateConfig(copy(...))` 热生效（音频音量在下次播放时读取）。
 *
 * @param enabled 总开关；关闭后仅不播放音频与粒子，面板仍会显示
 * @param respectSoundSetting 是否尊重设置页的「提示音」开关（true 时用户关提示音则静音）
 * @param particleCount 单次庆祝发射的粒子总数（彩带 + 圆形 + 丝带）
 * @param particleDurationMs 粒子生命周期（毫秒），到时自动回收
 * @param cornerCannonRatio 左右上角礼花炮的粒子占比（其余自顶部中心飘落）
 * @param centerBurst 是否在面板中央触发一次爆发
 * @param scoreCountUpMs 得分数字滚动动画时长（毫秒）
 * @param panelEntranceMs 面板入场动画时长（毫秒）
 * @param elementStaggerMs 面板内元素错峰淡入的间隔（毫秒）
 * @param stingerVolume 胜利提示音（琶音）音量 0..1
 * @param bgmVolume 庆祝背景音乐音量 0..1（建议 ≤0.4，避免盖过提示音）
 * @param bgmFadeInMs BGM 淡入时长（毫秒）
 * @param bgmFadeOutMs BGM 淡出时长（毫秒）
 * @param bgmLoopSeconds BGM 循环体长度（秒，程序化生成；越长越吃内存，4~12 为宜）
 */
data class CelebrationConfig(
    val enabled: Boolean = true,
    val respectSoundSetting: Boolean = true,
    val particleCount: Int = 120,
    val particleDurationMs: Long = 3_400L,
    val cornerCannonRatio: Float = 0.45f,
    val centerBurst: Boolean = true,
    val scoreCountUpMs: Long = 900L,
    val panelEntranceMs: Long = 420,
    val elementStaggerMs: Long = 130L,
    val stingerVolume: Float = 0.85f,
    val bgmVolume: Float = 0.32f,
    val bgmFadeInMs: Long = 600L,
    val bgmFadeOutMs: Long = 400L,
    val bgmLoopSeconds: Int = 8
) {
    init {
        require(particleCount in 0..600) { "particleCount 超出合理范围" }
        require(bgmLoopSeconds in 2..16) { "bgmLoopSeconds 超出合理范围" }
    }
}
