package com.example.sudokumaster.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sudokumaster.SudokuApplication
import com.example.sudokumaster.data.SudokuDatabase
import com.example.sudokumaster.data.entity.SavedGameEntity
import com.example.sudokumaster.domain.model.AppSettings
import com.example.sudokumaster.domain.model.SizeId
import com.example.sudokumaster.ui.game.GameScreen
import com.example.sudokumaster.ui.theme.SudokuMasterTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * 生成 GIF 用的交互帧：在真实对局页上**执行真实点击**（数字盘 / 笔记开关 / 擦除），
 * 每步之后把窗口画到 Canvas 落盘，再用 ffmpeg 合成动图。
 *
 * 与静态截图的区别：每一帧都是「输入事件 → ViewModel 状态变更 → 重组」后的结果，
 * 不是把几张静态图拼接。
 *
 * 踩坑记录：Robolectric 下单次 `performClick` 之后，状态变更要到**下一轮**主 Looper
 * 空闲才落到帧里，所以 [frame] 内部先 settle 再取图，否则整段素材会滞后一拍。
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class GifFramesTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var db: SudokuDatabase
    private val frames = File("build/gifframes").apply { mkdirs() }
    private var seq = 0

    private val puzzle =
        "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
    private val solution =
        "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
    private val given =
        "110010000100111000011000010100010001100101001100010001010000110000111001000010011"

    @Before
    fun seed() = runBlocking {
        val app: SudokuApplication = ApplicationProvider.getApplicationContext()
        runCatching {
            SudokuDatabase::class.java.getDeclaredField("INSTANCE")
                .apply { isAccessible = true }.set(null, null)
        }
        db = SudokuDatabase.get(app)
        db.savedGameDao().clear()
        // 选中格取下标 40（空、正确答案为 5），便于演示填数 / 擦除 / 笔记
        db.savedGameDao().upsert(
            SavedGameEntity(
                slotId = "9:2", sizeId = "9", sizeName = "九宫格", diffId = 2, diffName = "普通",
                values = puzzle, given = given, notes = "", solution = solution,
                selected = 40, hintsUsed = 0, mistakes = 0, elapsedMs = 412_000,
                isWon = false, savedAt = System.currentTimeMillis() - 45 * 60_000
            )
        )
    }

    /**
     * 推进一帧并跑完主 Looper 的挂起任务。
     *
     * 关键：Compose 测试环境用**受控帧时钟**（TestMonotonicFrameClock），不显式推进
     * `mainClock` 时重组根本不会发生——只 `idle()` 主 Looper 的话，画面会稳定滞后一帧。
     */
    private fun settle(rounds: Int = 20) {
        repeat(rounds) {
            composeRule.mainClock.advanceTimeBy(16)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun drawTo(): Bitmap {
        val view = composeRule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return bmp
    }

    private fun frame() {
        settle(20)
        drawTo()          // 丢弃一次：冲刷掉上一轮合成的残留，否则画面稳定滞后一拍
        settle(20)
        val bmp = drawTo()
        val name = String.format("f%03d", seq++)
        FileOutputStream(File(frames, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("FRAME $name ${bmp.width}x${bmp.height}")
    }

    /** 数字盘按钮内部同时渲染「数字 + 剩余数」，文本节点会混入剩余数，故用 testTag 精确定位。 */
    private fun tapPad(digit: Int) {
        composeRule.onNodeWithTag("pad-$digit").performClick()
    }

    @Test
    fun playInteraction() {
        composeRule.setContent {
            SudokuMasterTheme(settings = AppSettings()) {
                Surface {
                    GameScreen(
                        sizeId = SizeId.NINE, diffId = 2, mode = "resume",
                        replayRecordId = -1L, slotId = "9:2",
                        onBack = {}, onNavigateHome = {}
                    )
                }
            }
        }
        settle(60)
        frame()                          // 0 初始盘面

        tapPad(5); frame()               // 1 填入正确答案 5
        composeRule.onNodeWithText("擦除", substring = true).performClick()
        frame()                          // 2 擦除
        composeRule.onNodeWithText("笔记：", substring = true).performClick()
        frame()                          // 3 笔记模式开（数字盘变主色底）
        tapPad(4); frame()               // 4 记录候选 4
        tapPad(7); frame()               // 5 再记录候选 7
        composeRule.onNodeWithText("笔记：", substring = true).performClick()
        frame()                          // 6 关闭笔记模式
        println("FRAMES_DONE $seq")
    }
}
