package com.example.sudokumaster.data.repository

import com.example.sudokumaster.data.entity.SavedGameEntity
import com.example.sudokumaster.domain.session.GameSnapshot

/**
 * SavedGameEntity ↔ GameSnapshot 映射（data 层职责）。
 *
 * 领域层([com.example.sudokumaster.domain.session.GameSession])只产出 / 消费 [GameSnapshot]，
 * 不感知 Room 实体；槽位主键 slotId 在此由 sizeId:diffId 合成（F1 多槽）。
 */
fun GameSnapshot.toEntity(): SavedGameEntity = SavedGameEntity(
    slotId = "$sizeId:$diffId",
    sizeId = sizeId,
    sizeName = sizeName,
    diffId = diffId,
    diffName = diffName,
    values = values,
    given = given,
    notes = notes,
    solution = solution,
    selected = selected,
    hintsUsed = hintsUsed,
    mistakes = mistakes,
    elapsedMs = elapsedMs,
    isWon = isWon,
    savedAt = savedAt
)

fun SavedGameEntity.toSnapshot(): GameSnapshot = GameSnapshot(
    sizeId = sizeId,
    sizeName = sizeName,
    diffId = diffId,
    diffName = diffName,
    values = values,
    given = given,
    notes = notes,
    solution = solution,
    selected = selected,
    hintsUsed = hintsUsed,
    mistakes = mistakes,
    elapsedMs = elapsedMs,
    isWon = isWon,
    savedAt = savedAt
)
