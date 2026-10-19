package com.example.sudokumaster.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

/**
 * Room 迁移回归测试：在真实 SQLite（sqlite-jdbc，JVM 直跑）上按顺序执行
 * [SavedGameSchema.MIGRATION_2_3_STATEMENTS]，并校验迁移后的表结构与数据。
 *
 * **为什么要有这个测试**：v2→v3 迁移早期漏写引号，而 `values` 是 SQLite 保留字，
 * 执行时抛 `near "values": syntax error`。症状是从 v1.8.x（DB v2）升级到 v1.9.x（DB v3）
 * 的用户**一进应用就闪退**（Room 在建库时跑迁移，异常直接冒泡到启动流程）。
 * 由于纯 JVM 单测平时根本不会碰 Room，这个 bug 在测试全绿的情况下上了线。
 *
 * 这里直接执行与线上同一份迁移语句，任何新增保留字段名、拼错的列名或顺序错误
 * 都会让测试立刻失败。
 */
class RoomMigrationTest {

    private lateinit var conn: Connection
    private lateinit var st: Statement

    /** 还原 Room 在 DB v2 时生成的结构：单槽，`id` 固定为 1（升级前的历史形态）。 */
    private val createV2SavedGame = """
        CREATE TABLE `saved_game` (
            `id` INTEGER NOT NULL,
            `sizeId` TEXT NOT NULL,
            `sizeName` TEXT NOT NULL,
            `diffId` INTEGER NOT NULL,
            `diffName` TEXT NOT NULL,
            `values` TEXT NOT NULL,
            `given` TEXT NOT NULL,
            `notes` TEXT NOT NULL,
            `solution` TEXT NOT NULL,
            `selected` INTEGER NOT NULL,
            `hintsUsed` INTEGER NOT NULL,
            `mistakes` INTEGER NOT NULL,
            `elapsedMs` INTEGER NOT NULL,
            `isWon` INTEGER NOT NULL,
            `savedAt` INTEGER NOT NULL,
            PRIMARY KEY(`id`)
        )
    """.trimIndent()

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        st = conn.createStatement()
        // game_records 表在 v2 已存在且本次迁移不涉及，建出来只为还原真实环境
        st.executeUpdate(
            """
            CREATE TABLE `game_records` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sizeId` TEXT NOT NULL,
                `sizeName` TEXT NOT NULL,
                `diffId` INTEGER NOT NULL,
                `diffName` TEXT NOT NULL,
                `timeMs` INTEGER NOT NULL,
                `date` INTEGER NOT NULL,
                `puzzle` TEXT NOT NULL,
                `solution` TEXT NOT NULL,
                `score` INTEGER NOT NULL DEFAULT 0,
                `stars` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    @After
    fun tearDown() {
        st.close()
        conn.close()
    }

    private fun migrate() {
        SavedGameSchema.MIGRATION_2_3_STATEMENTS.forEach { st.executeUpdate(it) }
    }

    /** 返回 (列名, 类型, 是否NOT NULL, 主键序号)。 */
    private fun tableInfo(table: String): List<Quad> =
        st.executeQuery("PRAGMA table_info(`$table`)").use { rs ->
            buildList {
                while (rs.next()) add(
                    Quad(
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getInt("notnull") == 1,
                        rs.getInt("pk")
                    )
                )
            }
        }

    private data class Quad(val name: String, val type: String, val notNull: Boolean, val pk: Int)

    @Test
    fun `migration moves single-slot row into slotId keyed table`() {
        st.executeUpdate(createV2SavedGame)
        st.executeUpdate(
            """
            INSERT INTO `saved_game`
                (`id`,`sizeId`,`sizeName`,`diffId`,`diffName`,`values`,`given`,`notes`,`solution`,`selected`,`hintsUsed`,`mistakes`,`elapsedMs`,`isWon`,`savedAt`)
            VALUES (1,'9','九宫格',2,'普通','530070000','100000000','','530070000',12,3,1,42000,0,1760000000000)
            """.trimIndent()
        )

        migrate()

        st.executeQuery(
            "SELECT `slotId`,`sizeId`,`diffId`,`values`,`notes`,`selected`,`hintsUsed`,`mistakes`,`elapsedMs`,`isWon`,`savedAt` FROM `saved_game`"
        ).use { rs ->
            assertTrue("迁移后应有一行数据", rs.next())
            assertEquals("9:2", rs.getString("slotId"))
            assertEquals("9", rs.getString("sizeId"))
            assertEquals(2, rs.getInt("diffId"))
            assertEquals("530070000", rs.getString("values"))
            assertEquals("", rs.getString("notes"))
            assertEquals(12, rs.getInt("selected"))
            assertEquals(3, rs.getInt("hintsUsed"))
            assertEquals(1, rs.getInt("mistakes"))
            assertEquals(42000L, rs.getLong("elapsedMs"))
            assertEquals(0, rs.getInt("isWon"))
            assertEquals(1760000000000L, rs.getLong("savedAt"))
            assertTrue("应只有一行", !rs.next())
        }
    }

    @Test
    fun `migration produces schema matching SavedGameEntity`() {
        st.executeUpdate(createV2SavedGame)
        migrate()

        val cols = tableInfo(SavedGameSchema.TABLE)
        // 期望结构与 SavedGameEntity 一一对应（含类型 / NOT NULL / 主键）
        val expected = listOf(
            Quad("slotId", "TEXT", true, 1),
            Quad("sizeId", "TEXT", true, 0),
            Quad("sizeName", "TEXT", true, 0),
            Quad("diffId", "INTEGER", true, 0),
            Quad("diffName", "TEXT", true, 0),
            Quad("values", "TEXT", true, 0),
            Quad("given", "TEXT", true, 0),
            Quad("notes", "TEXT", true, 0),
            Quad("solution", "TEXT", true, 0),
            Quad("selected", "INTEGER", true, 0),
            Quad("hintsUsed", "INTEGER", true, 0),
            Quad("mistakes", "INTEGER", true, 0),
            Quad("elapsedMs", "INTEGER", true, 0),
            Quad("isWon", "INTEGER", true, 0),
            Quad("savedAt", "INTEGER", true, 0)
        )
        assertEquals(expected, cols)
        assertEquals("旧 id 列必须被移除", false, cols.any { it.name == "id" })
        assertEquals(listOf("slotId"), cols.filter { it.pk > 0 }.map { it.name })
    }

    @Test
    fun `migration succeeds when there is no saved game`() {
        st.executeUpdate(createV2SavedGame)   // 空表：从未有过进行中进度的用户

        migrate()

        assertEquals(15, tableInfo(SavedGameSchema.TABLE).size)
        st.executeQuery("SELECT COUNT(*) FROM `saved_game`").use { rs ->
            assertTrue(rs.next())
            assertEquals(0, rs.getInt(1))
        }
    }

    @Test
    fun `migrated table supports the dao queries`() {
        st.executeUpdate(createV2SavedGame)
        st.executeUpdate(
            """
            INSERT INTO `saved_game`
                (`id`,`sizeId`,`sizeName`,`diffId`,`diffName`,`values`,`given`,`notes`,`solution`,`selected`,`hintsUsed`,`mistakes`,`elapsedMs`,`isWon`,`savedAt`)
            VALUES (1,'4','四宫格',1,'简单','1234','1000','','1234',0,0,0,9000,0,1760000000000)
            """.trimIndent()
        )
        migrate()

        // SavedGameDao 的三类查询在迁移后的表上必须可用
        st.executeQuery("SELECT * FROM saved_game WHERE slotId = '4:1'").use { rs ->
            assertTrue("按 slotId 查询应命中", rs.next())
            assertEquals("1234", rs.getString("values"))
        }
        st.executeQuery("SELECT * FROM saved_game ORDER BY savedAt DESC LIMIT 1").use { rs ->
            assertTrue("最近一条查询应可用", rs.next())
        }
        assertEquals(1, st.executeUpdate("DELETE FROM saved_game WHERE slotId = '4:1'"))
    }
}
