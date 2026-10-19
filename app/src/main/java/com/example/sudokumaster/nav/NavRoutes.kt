package com.example.sudokumaster.nav

/** 路由定义。 */
object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ACHIEVEMENTS = "achievements"

    // game?mode={mode}&sizeId={sizeId}&diffId={diffId}&recordId={recordId}&puzzle={puzzle}&solution={solution}
    // puzzle / solution 仅 mode=import 使用（纯数字串，无需转义）
    const val GAME_PATTERN =
        "game?mode={mode}&sizeId={sizeId}&diffId={diffId}&recordId={recordId}&puzzle={puzzle}&solution={solution}"

    fun newGame(sizeId: String, diffId: Int) =
        "game?mode=new&sizeId=$sizeId&diffId=$diffId&recordId=-1&puzzle=&solution="

    fun resume() = "game?mode=resume&sizeId=9&diffId=1&recordId=-1&puzzle=&solution="

    fun replay(recordId: Long) =
        "game?mode=replay&sizeId=9&diffId=1&recordId=$recordId&puzzle=&solution="

    /** 导入题目开局（solution 为空时由 GameViewModel 自动求解）。 */
    fun importGame(sizeId: String, diffId: Int, puzzle: String, solution: String?) =
        "game?mode=import&sizeId=$sizeId&diffId=$diffId&recordId=-1&puzzle=$puzzle&solution=${solution ?: ""}"
}
