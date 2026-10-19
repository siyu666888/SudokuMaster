package com.example.sudokumaster.data

/**
 * `saved_game` 表的 v3 schema 与 v2→v3 迁移 SQL。
 *
 * 集中定义，供 [SudokuDatabase] 的 MIGRATION_2_3 与回归测试
 * （`RoomMigrationTest`）共用同一份语句，避免「迁移里一份、测试里另一份」的漂移。
 *
 * ⚠️ **所有标识符必须用反引号包裹**。`values` 是 SQLite 保留字：
 * 早期迁移漏写引号，执行时抛 `near "values": syntax error`，
 * 导致从 v1.8.x（DB v2）升级到 v1.9.x（DB v3）的用户一进应用就闪退。
 * `RoomMigrationTest` 会在真实 SQLite 上按顺序执行 [MIGRATION_2_3_STATEMENTS]，
 * 任何新增保留字段名都会立刻让测试失败。
 */
internal object SavedGameSchema {
    const val TABLE = "saved_game"
    const val TABLE_NEW = "saved_game_new"

    /** v3 目标结构：多槽，slotId = "sizeId:diffId" 为主键。 */
    const val CREATE_TABLE_V3 = """
        CREATE TABLE `saved_game_new` (
            `slotId` TEXT NOT NULL,
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
            PRIMARY KEY(`slotId`)
        )
    """

    /** 把旧单行按自身 sizeId:diffId 复制进新表，slotId 由 SQL 拼接生成。 */
    const val COPY_ROWS = """
        INSERT INTO `saved_game_new`
            (`slotId`, `sizeId`, `sizeName`, `diffId`, `diffName`, `values`, `given`, `notes`, `solution`, `selected`, `hintsUsed`, `mistakes`, `elapsedMs`, `isWon`, `savedAt`)
        SELECT `sizeId` || ':' || `diffId`, `sizeId`, `sizeName`, `diffId`, `diffName`, `values`, `given`, `notes`, `solution`, `selected`, `hintsUsed`, `mistakes`, `elapsedMs`, `isWon`, `savedAt`
        FROM `saved_game`
    """

    const val DROP_OLD_TABLE = "DROP TABLE `saved_game`"

    const val RENAME_NEW_TABLE = "ALTER TABLE `saved_game_new` RENAME TO `saved_game`"

    /** v2→v3 迁移的有序语句序列（迁移与测试共用，顺序即执行顺序）。 */
    val MIGRATION_2_3_STATEMENTS: List<String> = listOf(
        CREATE_TABLE_V3,
        COPY_ROWS,
        DROP_OLD_TABLE,
        RENAME_NEW_TABLE
    )
}
