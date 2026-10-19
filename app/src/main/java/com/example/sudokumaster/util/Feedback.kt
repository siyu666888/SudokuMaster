package com.example.sudokumaster.util

import android.content.Context
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** 触控反馈：震动 + 轻提示音（无外部资源，离线可用）。 */
object Feedback {

    private var toneGen: ToneGenerator? = null

    fun vibrate(context: Context, enabled: Boolean) {
        if (!enabled) return
        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(25)
            }
        } catch (_: Exception) {
            // 忽略：无震动硬件或权限
        }
    }

    fun beep(enabled: Boolean) {
        if (!enabled) return
        try {
            if (toneGen == null) toneGen = ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        } catch (_: Exception) {
            // 忽略
        }
    }
}
