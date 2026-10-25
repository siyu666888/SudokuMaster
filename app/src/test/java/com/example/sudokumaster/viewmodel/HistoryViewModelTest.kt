package com.example.sudokumaster.viewmodel

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sudokumaster.SudokuApplication
import androidx.room.Room
import com.example.sudokumaster.data.SudokuDatabase
import com.example.sudokumaster.data.entity.GameRecordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 历史记录页的「200 条截断」回归测试。
 *
 * 缺陷原貌：`HistoryViewModel` 用 `statsRepo.recentRecords(200)` 当数据源，
 * 又用 `allRecords.map { it.size }` 当总局数 —— 记录数一过 200：
 *   - 列表只剩最新 200 条；
 *   - 「总局数」恒显示 200；
 *   - 搜索只能在最近 200 条里命中；
 *   - 而界面拿 total 区分「从未完成」与「无匹配」，语义被一并带偏。
 *
 * 这里用超过 200 条的真实数据验证总数与列表都不再被限流。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HistoryViewModelTest {

    private lateinit var app: SudokuApplication
    private lateinit var db: SudokuDatabase

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // 用**内存库**，每个测试方法一份全新数据。
        // 不用默认文件库的原因：SudokuDatabase 是进程级单例，其原生 SQLite 连接只对
        // 当前 Robolectric 沙箱有效，跨方法复用会抛 "Illegal connection pointer"；
        // 而文件里的历史数据又会在方法间残留，导致「我插入的条数」对不上。
        db = Room.inMemoryDatabaseBuilder(app, SudokuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val puzzle = "530070000600195000098000060800060003400803001700020006060000280000419005000080079"
    private val solution =
        "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    private fun rec(i: Int) = GameRecordEntity(
        sizeId = "9", sizeName = "九宫格", diffId = 2, diffName = "普通",
        timeMs = 100_000L + i, date = 1_760_000_000_000L + i,
        puzzle = puzzle, solution = solution, score = 500 + i, stars = 3
    )

    /** 同 GameRecordDaoTest：把 Room 操作放到后台线程，本线程负责排空主 Looper。 */
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

    /**
     * 轮询等待 StateFlow 出现目标值。
     *
     * ⚠️ 必须先**订阅**再读 `.value`：ViewModel 里的 StateFlow 是
     * `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 初值)`，
     * 没有订阅者时上游根本不会启动，永远停在初值。真实界面里由
     * `collectAsStateWithLifecycle()` 负责订阅，测试里必须自己补上。
     *
     * 订阅协程用 `Dispatchers.Main`（非 immediate）投递到主 Looper 队列，
     * 再由循环里的 `idle()` 排空；Robolectric 默认 LooperMode=PAUSED，不会自动跑。
     */
    private fun <T> awaitState(flow: StateFlow<T>, predicate: (T) -> Boolean, what: String): T {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch { flow.collect { } }
        try {
            var v = flow.value
            var waited = 0L
            while (!predicate(v)) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(25)
                waited += 25
                check(waited < 10_000) { "等待「$what」超时，最后值 = $v" }
                v = flow.value
            }
            return v
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `totalCount and records are not capped at 200`() {
        runRoom { db.recordDao().insertAll((1..250).map { rec(it) }) }

        val vm = HistoryViewModel(AppContainer(app, db))

        // 1) 总局数必须走 SQL COUNT，而不是「列表有多大」
        assertEquals(250, awaitState(vm.totalCount, { it == 250 }, "总局数"))

        // 2) 展示列表本身也不能被截断
        val shown = awaitState(vm.records, { it.size == 250 }, "历史列表")
        assertEquals(250, shown.size)
        // 按 date 倒序，最新的是 i = 250，其 score = 500 + 250 = 750：
        // 既能证明第 250 条在列表里（没被 LIMIT 截掉），也顺带验证了排序方向
        assertEquals(750, shown.first().score)
    }

    @Test
    fun `search can still reach the oldest record`() {
        runRoom {
            db.recordDao().insertAll(
                listOf(
                    // 最旧的一条：唯一能被关键词「最老」命中的记录
                    GameRecordEntity(
                        sizeId = "4", sizeName = "四宫格", diffId = 1, diffName = "简单",
                        timeMs = 12_345, date = 1_000,
                        puzzle = puzzle, solution = solution, score = 100, stars = 1
                    )
                ) + (1..250).map { rec(it) }
            )
        }

        val vm = HistoryViewModel(AppContainer(app, db))
        awaitState(vm.totalCount, { it == 251 }, "总局数")

        // 旧实现只在前 200 条里搜索，这条永远搜不到
        vm.setQuery("四宫格")
        val hit = awaitState(vm.records, { it.size == 1 }, "搜索结果")
        assertEquals(12_345L, hit.first().timeMs)
    }

    @Test
    fun `empty state uses the real count`() {
        val vm = HistoryViewModel(AppContainer(app, db))
        // 空表：总数 0 → 界面走「还没有完成的对局」，而不是「没有匹配的记录」
        assertEquals(0, awaitState(vm.totalCount, { it == 0 }, "空表总数"))
        assertTrue(awaitState(vm.records, { it.isEmpty() }, "空表列表").isEmpty())
    }

    @Test
    fun `filters still narrow the list`() {
        runRoom { db.recordDao().insertAll((1..250).map { rec(it) }) }
        val vm = HistoryViewModel(AppContainer(app, db))
        awaitState(vm.totalCount, { it == 250 }, "总局数")

        vm.setSizeFilter("6")   // 数据全是 9 宫格，筛 6 宫格应为空
        assertTrue(awaitState(vm.records, { it.isEmpty() }, "尺寸筛选").isEmpty())

        vm.clearFilters()
        assertEquals(250, awaitState(vm.records, { it.size == 250 }, "清除筛选").size)
    }
}
