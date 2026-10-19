package com.example.sudokumaster.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sudokumaster.data.dao.GameRecordDao
import com.example.sudokumaster.data.dao.SavedGameDao
import com.example.sudokumaster.data.entity.GameRecordEntity
import com.example.sudokumaster.data.entity.SavedGameEntity

@Database(
    entities = [GameRecordEntity::class, SavedGameEntity::class],
    version = 3,
    exportSchema = false
)
abstract class SudokuDatabase : RoomDatabase() {
    abstract fun recordDao(): GameRecordDao
    abstract fun savedGameDao(): SavedGameDao

    companion object {
        /** v1 -> v2：历史记录新增 score / stars 两列（旧记录默认 0，界面按「无评分」处理）。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE game_records ADD COLUMN score INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE game_records ADD COLUMN stars INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v2 -> v3：saved_game 由单槽(id=1)改为多槽(slotId = sizeId:diffId 主键)，并移除 id 列。
         * 旧单条记录(id=1)按自身 sizeId:diffId 映射为新槽位主键。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE saved_game_new (
                        slotId TEXT NOT NULL PRIMARY KEY,
                        sizeId TEXT NOT NULL,
                        sizeName TEXT NOT NULL,
                        diffId INTEGER NOT NULL,
                        diffName TEXT NOT NULL,
                        values TEXT NOT NULL,
                        given TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        solution TEXT NOT NULL,
                        selected INTEGER NOT NULL,
                        hintsUsed INTEGER NOT NULL,
                        mistakes INTEGER NOT NULL,
                        elapsedMs INTEGER NOT NULL,
                        isWon INTEGER NOT NULL,
                        savedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO saved_game_new
                        (slotId, sizeId, sizeName, diffId, diffName, values, given, notes, solution, selected, hintsUsed, mistakes, elapsedMs, isWon, savedAt)
                    SELECT sizeId || ':' || diffId, sizeId, sizeName, diffId, diffName, values, given, notes, solution, selected, hintsUsed, mistakes, elapsedMs, isWon, savedAt
                    FROM saved_game
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE saved_game")
                db.execSQL("ALTER TABLE saved_game_new RENAME TO saved_game")
            }
        }

        @Volatile
        private var INSTANCE: SudokuDatabase? = null

        fun get(context: Context): SudokuDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SudokuDatabase::class.java,
                    "sudoku.db"
                )
                    // 注意：不启用 fallbackToDestructiveMigration —— 未来若漏写迁移会直接崩溃而非静默清空用户数据（D1）。
                    // 当前 1→2、2→3 均有对应迁移覆盖，安全。
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
