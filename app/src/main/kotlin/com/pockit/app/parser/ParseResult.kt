package com.pockit.app.parser

import java.time.LocalDate

sealed class ParseResult

data class ParsedEntry(
    val kind: EntryKind,
    val payerId: Long,
    val receiverId: Long?,            // transfers only
    val vendorId: Long?,
    val total: Int,
    val participants: List<ParticipantShare>, // empty for transfers
    val label: String,
    val purposeText: String?,         // null for transfers
    val extraInfo: String?,
    val expenseDate: LocalDate,
    val forDate: LocalDate?,
    val timeText: String?,
    val originalText: String,
    val groupTag: String?,            // group name or null for general
) : ParseResult()

data class ParticipantShare(val personId: Long, val amount: Int)

enum class EntryKind { EXPENSE, TRANSFER }

data class NeedsAnswer(
    val partial: PartialEntry,
    val question: Question,
) : ParseResult()

sealed class Question {
    object WhatWasThisFor : Question()
    object WhoWasThisFor : Question()
    data class WhichMonth(val day: Int) : Question()
}

data class PartialEntry(
    val kind: EntryKind,
    val payerId: Long,
    val receiverId: Long?,
    val vendorId: Long?,
    val total: Int,
    val participantIds: List<Long>?,  // null means not yet answered
    val splits: Map<Long, Int>?,      // explicit splits if provided
    val splitType: SplitType,
    val onlyFlag: Boolean,
    val label: String?,
    val purposeText: String?,
    val extraInfo: String?,
    val expenseDate: LocalDate,
    val forDate: LocalDate?,
    val timeText: String?,
    val originalText: String,
    val groupTag: String?,
)

enum class SplitType { EQUAL, EXPLICIT, PERCENTAGE, REST_EQUAL }

data class TypoError(val line: String, val reason: String) : ParseResult()
