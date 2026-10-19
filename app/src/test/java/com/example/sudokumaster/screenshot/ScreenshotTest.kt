package com.example.sudokumaster.screenshot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sudokumaster.SudokuApplication
import com.example.sudokumaster.data.SudokuDatabase
import com.example.sudokumaster.data.entity.GameRecordEntity
import com.example.sudokumaster.data.entity.SavedGameEntity
import com.example.sudokumaster.domain.model.AppSettings
import com.example.sudokumaster.domain.model.SizeId
import com.example.sudokumaster.ui.achievements.AchievementsScreen
import com.example.sudokumaster.ui.game.GameScreen
import com.example.sudokumaster.ui.history.HistoryScreen
import com.example.sudokumaster.ui.home.HomeScreen
import com.example.sudokumaster.ui.settings.SettingsScreen
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
 * 在 JVM 上用 Robolectric 渲染**真实页面 Composable**并导出 PNG，供 README 使用。
 *
 * 与「P 图」不同：这里跑的是 app 的线上代码路径——真 Compose 布局、真主题、
 * 真 Room 数据（Robolectric 提供真实 SQLite），只是把窗口画到软件 Canvas 上再落盘。
 *
 * 为什么不用 `captureToImage()`：它内部靠重绘回调 + 屏障等待，在 Robolectric 的
 * 单线程主 Looper 下会永远等不到（ComposeTimeoutException），因此改为直接
 * `decorView.draw(canvas)`。前提是 `@GraphicsMode(NATIVE)`（真实 Skia + 系统字体，
 * 中文能正常出字形）。
 *
 * 产物：`app/build/screenshots/` 下的 PNG，由 `tools/export_screenshots.sh` 拷到 `docs/screenshots/`。
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: SudokuApplication
    private lateinit var db: SudokuDatabase
    private val outDir = File("build/screenshots").apply { mkdirs() }

    /** 经典 9×9 题目与解，仅用于填充历史记录 / 续玩存档的展示数据。 */
    private val puzzle =
        "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
    private val solution =
        "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    @Before
    fun seedData() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        // Robolectric 的沙箱会在用例间复用静态状态，重置单例避免拿到上一个用例的旧库
        runCatching {
            SudokuDatabase::class.java.getDeclaredField("INSTANCE")
                .apply { isAccessible = true }
                .set(null, null)
        }
        db = SudokuDatabase.get(app)
        db.recordDao().clear()
        db.savedGameDao().clear()

        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        db.recordDao().insertAll(
            listOf(
                rec("9", "九宫格", 5, "地狱", 812_000, now - 2 * 3600_000, 980, 3),
                rec("9", "九宫格", 3, "困难", 455_000, now - 26 * 3600_000, 860, 3),
                rec("9", "九宫格", 2, "普通", 388_000, now - 2 * day, 760, 2),
                rec("9", "九宫格", 1, "简单", 236_000, now - 2 * day - 7 * 3600_000, 690, 2),
                rec("6", "六宫格", 3, "困难", 188_000, now - 3 * day, 640, 3),
                rec("6", "六宫格", 2, "普通", 152_000, now - 4 * day, 580, 2),
                rec("6", "六宫格", 1, "简单", 96_000, now - 5 * day, 520, 3),
                rec("4", "四宫格", 2, "普通", 41_000, now - 6 * day, 400, 3),
                rec("4", "四宫格", 1, "简单", 33_000, now - 7 * day, 350, 2)
            )
        )
        // 一盘进行中的九宫格·普通，供首页「继续游戏」卡片展示
        db.savedGameDao().upsert(
            SavedGameEntity(
                slotId = "9:2",
                sizeId = "9",
                sizeName = "九宫格",
                diffId = 2,
                diffName = "普通",
                values = "530070000600195000098000060800060003400803001700020006060000280000419005000080079",
                given = "110010000100111000011000010100010001100101001100010001010000110000111001000010011",
                notes = "",
                solution = solution,
                selected = 40,
                hintsUsed = 1,
                mistakes = 0,
                elapsedMs = 412_000,
                isWon = false,
                savedAt = now - 45 * 60_000
            )
        )
    }

    private fun rec(
        sizeId: String, sizeName: String, diffId: Int, diffName: String,
        timeMs: Long, date: Long, score: Int, stars: Int
    ) = GameRecordEntity(
        sizeId = sizeId, sizeName = sizeName, diffId = diffId, diffName = diffName,
        timeMs = timeMs, date = date, puzzle = puzzle, solution = solution,
        score = score, stars = stars
    )

    /** 让主 Looper 把挂起的重组 / 协程回调跑完，再给异步数据加载留出余量。 */
    private fun settle(rounds: Int = 40) {
        repeat(rounds) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
    }

    private fun capture(name: String) {
        val view = composeRule.activity.window.decorView
        check(view.width > 0 && view.height > 0) { "窗口尚未布局：${view.width}x${view.height}" }
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SHOT $name ${bmp.width}x${bmp.height}")
    }

    private fun content(block: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent {
            SudokuMasterTheme(settings = AppSettings()) {
                Surface { block() }
            }
        }
    }

    @Test
    fun home() {
        content { HomeScreen(onNewGame = { _, _ -> }, onResume = { }) }
        settle()
        capture("01-home")
    }

    @Test
    fun game() {
        content {
            GameScreen(
                sizeId = SizeId.NINE,
                diffId = 2,
                mode = "resume",
                replayRecordId = -1L,
                slotId = "9:2",
                onBack = {},
                onNavigateHome = {}
            )
        }
        settle(rounds = 60)
        capture("02-game")
    }

    @Test
    fun achievements() {
        content { AchievementsScreen(onBack = {}, onStartLevel = { _, _ -> }) }
        settle()
        capture("03-achievements")
    }

    @Test
    fun history() {
        content { HistoryScreen(onBack = {}, onReplay = { }, onResume = { }) }
        settle()
        capture("04-history")
    }

    @Test
    fun settings() {
        content { SettingsScreen(onBack = {}, onPlayImported = { _, _, _, _ -> }, onResume = { }) }
        settle()
        capture("05-settings")
    }
}
