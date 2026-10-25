package com.example.sudokumaster.data

import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sudokumaster.data.dao.GameRecordDao
import com.example.sudokumaster.data.entity.GameRecordEntity
import com.example.sudokumaster.domain.game.StatsAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * GameRecordDao 聚合查询回归测试（Robolectric + 内存版 Room，跑真实 SQLite）。
 *
 * 覆盖两条容易悄悄坏掉的路径：
 *  1. **`COUNT(*)` 不能被列表的 LIMIT 顺手限掉** —— 早期 `HistoryViewModel` 用
 *     `recent(200).map { it.size }` 当总局数，记录数过 200 后总数恒为 200。
 *  2. **SQL 聚合结果必须与纯 Kotlin 实现（[StatsAnalyzer]）逐值一致**，
 *     尤其是「本地自然日」的口径（`strftime('localtime')` vs `Calendar` 零点）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GameRecordDaoTest {

    private lateinit var db: SudokuDatabase
    private lateinit var dao: GameRecordDao

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(app, SudokuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.recordDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------------------------------------------------------- 工具

    private val puzzle = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
    private val solution =
        "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    private fun rec(
        sizeId: String, diffId: Int, timeMs: Long, date: Long, score: Int = 0
    ) = GameRecordEntity(
        sizeId = sizeId, sizeName = sizeId, diffId = diffId, diffName = "d$diffId",
        timeMs = timeMs, date = date, puzzle = puzzle, solution = solution,
        score = score, stars = if (score > 0) 3 else 0
    )

    /**
     * 在 Robolectric 里执行 Room 操作（含 Flow 收集）。
     *
     * Robolectric 默认 LooperMode=PAUSED：主 Looper 队列不会自动排空，而测试本身就跑在主线程，
     * 所以不能直接 `runBlocking { dao.insert(...) }` —— 一旦 Room 把活儿派到主 Looper 就死锁。
     * 这里放到后台线程执行，本线程循环排空主 Looper 直到完成。
     */
    private fun <T> runRoom(block: suspend () -> T): T {
        val latch = CountDownLatch(1)
        val result = AtomicReference<T>()
        val failure = AtomicReference<Throwable>()
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            try {
                result.set(block())
            } catch (t: Throwable) {
                failure.set(t)
            } finally {
                latch.countDown()
            }
        }
        var waited = 0L
        while (!latch.await(50, TimeUnit.MILLISECONDS)) {
            shadowOf(Looper.getMainLooper()).idle()
            waited += 50
            check(waited < 10_000) { "Room 操作 10 秒未完成" }
        }
        scope.cancel()
        failure.get()?.let { throw it }
        return result.get()
    }

    private fun countAll() = runRoom { dao.countAll().first() }
    private fun all() = runRoom { dao.all().first() }
    private fun recent(n: Int) = runRoom { dao.recent(n).first() }
    private fun overview() = runRoom { dao.overview().first() }
    private fun groupStats() = runRoom { dao.groupStats().first() }
    private fun distinctDays() = runRoom { dao.distinctDays().first() }
    private fun lastPlayed() = runRoom { dao.lastPlayed().first() }

    // ---------------------------------------------------------- 用例

    /**
     * **本项目的核心回归**：条数查询不能被列表限流。
     * 插入 250 条（超过旧代码写死的 200），COUNT 与全表都必须给出 250。
     */
    @Test
    fun `countAll and all are not capped at 200`() {
        runRoom {
            dao.insertAll((1..250).map { rec("9", 2, 100_000L + it, 1_760_000_000_000L + it) })
        }

        assertEquals(250, countAll())
        assertEquals(250, all().size)
        // 旧的错误实现正是 recent(200).map { it.size }：这里让它原形毕露
        assertEquals(200, recent(200).size)
    }

    @Test
    fun `overview aggregates the whole table`() {
        // 空表：COUNT 为 0，其余为 null
        val empty = overview()
        assertEquals(0, empty.cnt)
        assertNull(empty.avgMs)
        assertNull(empty.bestMs)
        assertNull(empty.maxScore)

        runRoom {
            dao.insertAll(
                listOf(
                    rec("9", 1, 30_000, 1_000, score = 500),
                    rec("9", 1, 60_000, 2_000, score = 300),
                    rec("9", 1, 90_000, 3_000, score = 0)   // 旧版本记录，无评分
                )
            )
        }
        val row = overview()
        assertEquals(3, row.cnt)
        assertEquals(30_000L, row.bestMs)
        assertEquals(60_000L, row.avgMs)          // (30+60+90)/3
        assertEquals(500, row.maxScore)            // 是否忽略 0 分由调用方决定
    }

    @Test
    fun `groupStats groups by size and difficulty`() {
        runRoom {
            dao.insertAll(
                listOf(
                    rec("9", 2, 100, 10, score = 700),
                    rec("9", 2, 200, 20, score = 900),
                    rec("9", 2, 300, 30),
                    rec("6", 1, 50, 40, score = 100),
                    rec("4", 1, 20, 50)
                )
            )
        }
        val rows = groupStats()
        assertEquals(3, rows.size)

        val nine = rows.first { it.sizeId == "9" && it.diffId == 2 }
        assertEquals(3, nine.cnt)
        assertEquals(100L, nine.bestMs)
        assertEquals(200.0, nine.avgMs!!, 1e-9)
        assertEquals(900, nine.maxScore)

        val six = rows.first { it.sizeId == "6" && it.diffId == 1 }
        assertEquals(1, six.cnt)
        assertEquals(50L, six.bestMs)

        val four = rows.first { it.sizeId == "4" && it.diffId == 1 }
        assertEquals(1, four.cnt)
        // 全为 0 分旧记录时 MAX(score) 取到 0；「无评分」的过滤在 UI 侧完成
        assertEquals(0, four.maxScore)
    }

    /**
     * SQL 的「本地自然日」必须与 [StatsAnalyzer.dayNumber] 同口径。
     * 这条一旦对不上，连续天数 / 活跃天数就会静默算错。
     */
    @Test
    fun `distinctDays matches StatsAnalyzer dayNumber`() {
        // 同一天两局 + 隔天 + 再隔天：共 3 个自然日（时刻取当地正午附近，避开零点边界）
        val day = 24L * 60 * 60 * 1000
        val base = 1_760_000_000_000L
        val dates = listOf(
            base,
            base + 3 * 3600_000,        // 同一天
            base + day,                 // 次日
            base + day + 5 * 3600_000,  // 仍是次日
            base + 5 * day              // 第 5 天
        )
        runRoom { dao.insertAll(dates.mapIndexed { i, d -> rec("9", 2, 100L + i, d) }) }

        val expected = dates.map { StatsAnalyzer.dayNumber(it) }.distinct().sorted()
        val actual = distinctDays()

        assertEquals(expected, actual)
        assertEquals(3, actual.size)
    }

    @Test
    fun `lastPlayed returns the newest date`() {
        assertNull(lastPlayed())

        runRoom {
            dao.insertAll(
                listOf(
                    rec("9", 2, 100, 5_000),
                    rec("9", 2, 100, 9_000),   // 最新
                    rec("9", 2, 100, 7_000)
                )
            )
        }
        assertEquals(9_000L, lastPlayed())
    }

    /** 聚合结果在插入新记录后要能重新发射（Flow 活性，不是一次性快照）。 */
    @Test
    fun `aggregates re-emit after insert`() {
        assertEquals(0, countAll())
        runRoom { dao.insertAll(listOf(rec("4", 1, 10, 1))) }
        assertEquals(1, countAll())
        // 同一 (尺寸:难度) 再进一条：仍是一组，但局数要从 1 变 2 —— 证明是重新发射而非旧值
        runRoom { dao.insertAll(listOf(rec("4", 1, 20, 2))) }
        assertEquals(2, countAll())
        val groups = groupStats()
        assertEquals(1, groups.size)
        assertEquals(2, groups.first().cnt)
        assertEquals(10L, overview().bestMs)
    }
}
