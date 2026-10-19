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
    version = 2,
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
                    // 当前仅 1→2 且有 MIGRATION_1_2 覆盖，安全。
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
