package com.example.sudokumaster.domain.game

import com.example.sudokumaster.domain.model.BoardDefinitions
import com.example.sudokumaster.domain.model.SizeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreCalculatorTest {

    private val nineEasy = BoardDefinitions.difficulty(SizeId.NINE, 1)
    private val nineHell = BoardDefinitions.difficulty(SizeId.NINE, 5)

    @Test
    fun perfectFastGame_scoresSWithThreeStars() {
        // NINE Easy 标准用时 600000ms；恰好达标 → speed=1.0，零失误零提示
        val r = ScoreCalculator.calculate(SizeId.NINE, nineEasy, elapsedMs = 600_000L, mistakes = 0, hintsUsed = 0)
        assertEquals(3, r.stars)
        assertEquals("S", r.rank)
        // base = 1000 * 1.0 * 1.0 = 1000
        assertEquals(1000, r.score)
    }

    @Test
    fun slowGameWithManyMistakes_floorsScoreAndRanksC() {
        val r = ScoreCalculator.calculate(
            SizeId.NINE, nineEasy,
            elapsedMs = 10_000_000L, // speed 触底 0.25
            mistakes = 10,
            hintsUsed = 5
        )
        assertEquals(1, r.stars)
        assertEquals("C", r.rank)
        assertTrue("score must be floored at 50, was ${r.score}", r.score >= 50)
    }

    @Test
    fun twoStarBoundary_respected() {
        // mistakes<=2 且 hints<=3 且 speed>=0.4 → 2 星（零失误才 3 星）
        val r = ScoreCalculator.calculate(
            SizeId.NINE, nineEasy,
            elapsedMs = 600_000L, // speed=1.0
            mistakes = 2,
            hintsUsed = 3
        )
        assertEquals(2, r.stars)
    }

    @Test
    fun multipliers_matchDefinitions() {
        assertEquals(2.0f, ScoreCalculator.difficultyMultiplier(nineHell))
        assertEquals(1.6f, ScoreCalculator.difficultyMultiplier(BoardDefinitions.difficulty(SizeId.NINE, 4)))
        assertEquals(0.6f, ScoreCalculator.sizeMultiplier(SizeId.FOUR))
        assertEquals(0.8f, ScoreCalculator.sizeMultiplier(SizeId.SIX))
        assertEquals(1.0f, ScoreCalculator.sizeMultiplier(SizeId.NINE))
    }
}
