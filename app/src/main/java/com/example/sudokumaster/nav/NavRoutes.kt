package com.example.sudokumaster.nav

/** 路由定义。 */
object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ACHIEVEMENTS = "achievements"

    // game?mode={mode}&sizeId={sizeId}&diffId={diffId}&recordId={recordId}&puzzle={puzzle}&solution={solution}&slotId={slotId}
    // puzzle / solution 仅 mode=import 使用（纯数字串，无需转义）
    // slotId 仅 mode=resume 使用：为空表示恢复「最近保存」的一盘；指定则恢复对应 (尺寸:难度) 槽（F1 多槽）
    const val GAME_PATTERN =
        "game?mode={mode}&sizeId={sizeId}&diffId={diffId}&recordId={recordId}&puzzle={puzzle}&solution={solution}&slotId={slotId}"

    fun newGame(sizeId: String, diffId: Int) =
        "game?mode=new&sizeId=$sizeId&diffId=$diffId&recordId=-1&puzzle=&solution=&slotId="

    /** 恢复进度。slotId 为空 = 最近保存的一盘；否则恢复指定槽。 */
    fun resume(slotId: String = "") =
        "game?mode=resume&sizeId=9&diffId=1&recordId=-1&puzzle=&solution=&slotId=$slotId"

    fun replay(recordId: Long) =
        "game?mode=replay&sizeId=9&diffId=1&recordId=$recordId&puzzle=&solution=&slotId="

    /** 导入题目开局（solution 为空时由 GameViewModel 自动求解）。 */
    fun importGame(sizeId: String, diffId: Int, puzzle: String, solution: String?) =
        "game?mode=import&sizeId=$sizeId&diffId=$diffId&recordId=-1&puzzle=$puzzle&solution=${solution ?: ""}&slotId="
}
