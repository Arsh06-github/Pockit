package com.pockit.app.parser

import com.pockit.app.ledger.parseAmount
import com.pockit.app.ledger.splitEqual
import java.time.LocalDate
import kotlin.math.ceil

// Structural words consumed during parsing (never reported as typos)
private val STOP_WORDS = setOf(
    "only", "back", "for", "on", "to", "by", "and", "paid", "the",
    "rest", "equal", "himself", "herself", "from", "at", "is", "a",
    "this", "that", "in", "of", "with", "also", "general"
)

private val JUNK_WORDS = setOf(
    "rupees", "rs", "inr", "₹"
)

fun parseEntry(line: String, ctx: ParserContext): ParseResult {
    val original = line.trim()
    val aliasIndex = buildAliasIndex(ctx.people)
    val owner = ctx.people.firstOrNull { it.isOwner }
        ?: return TypoError(line, "No owner in context")

    // ── 1. Detect group tag ──────────────────────────────────────────────────
    var groupTag: String? = null
    var workLine = original
    val groupTagRegex = Regex("""@(\S+)""")
    val tagMatches = groupTagRegex.findAll(workLine).toList()
    for (m in tagMatches) {
        val tag = m.groupValues[1]
        val tagLower = tag.lowercase()
        if (tagLower == "general") {
            if (groupTag != null) return TypoError(line, "@General with another group tag")
            groupTag = "__general__"
            workLine = workLine.replace(m.value, "").trim()
        } else if (tagLower != "search" && tagLower != "edit") {
            val group = ctx.groups.firstOrNull { it.name.lowercase() == tagLower && it.deletedAt == null }
                ?: return TypoError(line, "Unknown group @$tag")
            if (groupTag != null && groupTag != group.name)
                return TypoError(line, "Two different group tags")
            if (groupTag == "__general__") return TypoError(line, "@General with another group tag")
            groupTag = group.name
            workLine = workLine.replace(m.value, "").trim()
        }
    }
    val effectiveGroupTag = if (groupTag == "__general__") null else groupTag

    // ── 2. Tokenize ──────────────────────────────────────────────────────────
    val tokens = workLine.split(Regex("""[\s,]+""")).filter { it.isNotBlank() }.toMutableList()
    val consumed = BooleanArray(tokens.size)

    // ── 3. Match people (longest alias first) ───────────────────────────────
    data class PersonSpan(val person: PersonRecord, val start: Int, val end: Int)
    val personSpans = mutableListOf<PersonSpan>()

    var i = 0
    while (i < tokens.size) {
        var matched = false
        // Try longest span first (up to 4 tokens for multi-word names)
        for (len in minOf(4, tokens.size - i) downTo 1) {
            val phrase = tokens.subList(i, i + len).joinToString(" ")
            // Try exact main name match (case-sensitive)
            val byMain = ctx.people.firstOrNull { it.mainName == phrase && it.deletedAt == null }
            if (byMain != null) {
                personSpans.add(PersonSpan(byMain, i, i + len))
                for (k in i until i + len) consumed[k] = true
                i += len; matched = true; break
            }
            // Try lowercase alias match
            val byAlias = aliasIndex[phrase.lowercase()]
            if (byAlias != null) {
                personSpans.add(PersonSpan(byAlias, i, i + len))
                for (k in i until i + len) consumed[k] = true
                i += len; matched = true; break
            }
            // Deleted person main name match — typo error
            val deletedMatch = ctx.people.firstOrNull {
                it.mainName == phrase && it.deletedAt != null
            }
            if (deletedMatch != null) return TypoError(line, "Deleted user: ${deletedMatch.mainName}")
        }
        if (!matched) i++
    }

    // ── 4. Find verb ─────────────────────────────────────────────────────────
    val verbMatch = findVerb(tokens)
        ?: return TypoError(line, "No recognised verb")

    // Mark verb tokens consumed
    consumed[verbMatch.index] = true
    val verbLower = verbMatch.verb.lowercase()
    val nextIdx = verbMatch.index + 1
    if (tokens.getOrNull(nextIdx)?.lowercase() == "back" &&
        verbLower in setOf("paid", "gave", "sent")
    ) consumed[nextIdx] = true

    // ── 5. Determine payer ───────────────────────────────────────────────────
    var payerId: Long? = null

    // Check "paid by <person>" pattern
    val paidByIdx = tokens.indexOfFirst { it.lowercase() == "paid" }.let { pIdx ->
        if (pIdx >= 0 && tokens.getOrNull(pIdx + 1)?.lowercase() == "by") pIdx else -1
    }
    if (paidByIdx >= 0) {
        consumed[paidByIdx] = true
        consumed[paidByIdx + 1] = true
        // payer is the first person after "paid by"
        val payerSpan = personSpans.firstOrNull { it.start == paidByIdx + 2 }
        if (payerSpan != null) {
            payerId = payerSpan.person.id
        } else {
            val token = tokens.getOrNull(paidByIdx + 2)?.lowercase()
            if (token != null && token in OWNER_ALIASES) {
                payerId = owner.id
                consumed[paidByIdx + 2] = true
            }
        }
    } else {
        // Payer is the person(s) before the verb, or owner aliases
        val verbIdx = verbMatch.index
        // Check if the token before the verb is an owner alias
        val tokenBeforeVerb = tokens.getOrNull(verbIdx - 1)?.lowercase()
        if (tokenBeforeVerb != null && tokenBeforeVerb in OWNER_ALIASES) {
            payerId = owner.id
            consumed[verbIdx - 1] = true
        } else {
            // Find the person span ending at verbIdx
            val payerSpan = personSpans.firstOrNull { it.end == verbIdx }
            if (payerSpan != null) payerId = payerSpan.person.id
        }
    }

    if (payerId == null) return TypoError(line, "No payer found")
    val payerRecord = ctx.people.firstOrNull { it.id == payerId }
        ?: return TypoError(line, "Payer not found")

    // JMD Tiffin cannot be payer
    if (payerRecord.isVendor) return TypoError(line, "Vendor cannot be payer")

    // Mark payer's span as consumed
    personSpans.firstOrNull { it.person.id == payerId }?.let {
        for (k in it.start until it.end) consumed[k] = true
    }

    // ── 6. Handle transfers (LOAN / REPAYMENT) ──────────────────────────────
    if (verbMatch.kind == VerbClass.LOAN || verbMatch.kind == VerbClass.REPAYMENT) {
        // Find receiver: person after "to" or immediately after verb
        var receiverId: Long? = null
        val toIdx = tokens.indexOfFirst { it.lowercase() == "to" && !consumed[tokens.indexOf(it)] }
        if (toIdx >= 0) {
            consumed[toIdx] = true
            val span = personSpans.firstOrNull { it.start == toIdx + 1 }
            if (span != null) {
                receiverId = span.person.id
                for (k in span.start until span.end) consumed[k] = true
            } else {
                val tok = tokens.getOrNull(toIdx + 1)?.lowercase()
                if (tok != null && tok in OWNER_ALIASES) {
                    receiverId = owner.id
                    consumed[toIdx + 1] = true
                }
            }
        } else {
            // Person right after the verb
            val afterVerb = personSpans.firstOrNull { it.start == verbMatch.index + 1 }
            if (afterVerb != null) {
                receiverId = afterVerb.person.id
                for (k in afterVerb.start until afterVerb.end) consumed[k] = true
            }
        }

        if (receiverId == null) return TypoError(line, "No receiver for transfer")
        if (receiverId == payerId) return TypoError(line, "Cannot transfer to self")

        // Receiver is JMD Tiffin → reroute as vendor expense (payer must be owner)
        val receiverRecord = ctx.people.firstOrNull { it.id == receiverId }
        if (receiverRecord?.isVendor == true) {
            if (payerId != owner.id) return TypoError(line, "Only owner can pay JMD Tiffin")
            // Fall through to vendor expense handling below (break out of transfer block)
        } else {
            val amount = findAmount(tokens, consumed, verbMatch) ?: return TypoError(line, "No amount")
            markAmountConsumed(tokens, consumed, amount)

            val label = if (verbMatch.kind == VerbClass.REPAYMENT) "Repayment" else "Loan"
            return ParsedEntry(
                kind = EntryKind.TRANSFER,
                payerId = payerId,
                receiverId = receiverId,
                vendorId = null,
                total = amount,
                participants = emptyList(),
                label = label,
                purposeText = null,
                extraInfo = null,
                expenseDate = ctx.today,
                forDate = null,
                timeText = null,
                originalText = original,
                groupTag = effectiveGroupTag,
            )
        }
    }

    // ── 7. Find amount ───────────────────────────────────────────────────────
    val amount = findAmount(tokens, consumed, verbMatch)
        ?: return TypoError(line, "No amount found")
    // Mark amount token consumed
    markAmountConsumed(tokens, consumed, amount)

    // ── 8. Find date and time ────────────────────────────────────────────────
    var expenseDate = ctx.today
    var forDate: LocalDate? = null
    var timeText: String? = null
    var ambiguousDay: Int? = null

    var ti = 0
    while (ti < tokens.size) {
        if (consumed[ti]) { ti++; continue }
        val dateResult = tryParseDate(tokens, ti, ctx.today)
        if (dateResult != null) {
            val (dr, n) = dateResult
            when (dr) {
                is DateResult.Exact -> {
                    val d = dr.date
                    if (!d.isAfter(ctx.today)) {
                        expenseDate = d
                    } else {
                        forDate = d
                    }
                    for (k in ti until ti + n) consumed[k] = true
                    ti += n; continue
                }
                is DateResult.AmbiguousDay -> {
                    ambiguousDay = dr.day
                    for (k in ti until ti + n) consumed[k] = true
                    ti += n; continue
                }
            }
        }
        val timeResult = tryParseTime(tokens, ti)
        if (timeResult != null) {
            val (tt, n) = timeResult
            timeText = tt
            for (k in ti until ti + n) consumed[k] = true
            ti += n; continue
        }
        ti++
    }

    if (ambiguousDay != null) {
        // Build partial and ask which month
        return NeedsAnswer(
            partial = PartialEntry(
                kind = EntryKind.EXPENSE,
                payerId = payerId,
                receiverId = null,
                vendorId = null,
                total = amount,
                participantIds = null,
                splits = null,
                splitType = SplitType.EQUAL,
                onlyFlag = false,
                label = null,
                purposeText = null,
                extraInfo = null,
                expenseDate = ctx.today,
                forDate = null,
                timeText = timeText,
                originalText = original,
                groupTag = effectiveGroupTag,
            ),
            question = Question.WhichMonth(ambiguousDay!!)
        )
    }

    // ── 9. JMD Tiffin as receiver/vendor ─────────────────────────────────────
    val jmd = ctx.people.firstOrNull { it.isVendor }
    var vendorId: Long? = null
    val tiffinSpan = personSpans.firstOrNull { it.person.isVendor }
    if (tiffinSpan != null) {
        if (payerId != owner.id) return TypoError(line, "Only owner can pay JMD Tiffin")
        vendorId = jmd!!.id
        for (k in tiffinSpan.start until tiffinSpan.end) consumed[k] = true
    }

    // ── 10. Find participants ─────────────────────────────────────────────────
    val onlyFlag = tokens.any { it.lowercase() == "only" }
    if (onlyFlag) tokens.indexOfFirst { it.lowercase() == "only" }.let { consumed[it] = true }

    // Check "for himself" / "for me" / "for <payer>" alone → personal expense
    val forIdx = tokens.indexOfFirst { it.lowercase() == "for" && !consumed[tokens.indexOf(it)] }
    var personalExpense = false

    // Determine which person spans are participants (not the payer's span used for payer role)
    val participantSpans = personSpans.filter { span ->
        // Don't re-add JMD as participant
        !span.person.isVendor
    }

    // Find all persons after "for"
    val forPersons = mutableListOf<PersonRecord>()
    if (forIdx >= 0) {
        consumed[forIdx] = true
        val tok = tokens.getOrNull(forIdx + 1)?.lowercase()
        if (tok == "himself" || tok == "herself") {
            consumed[forIdx + 1] = true
            personalExpense = true
        } else if (tok in OWNER_ALIASES || tok == "me") {
            if (participantSpans.none { it.start == forIdx + 1 }) {
                consumed[forIdx + 1] = true
            }
            // Could still have more people
        }
    }

    // Collect named participants (all person spans not already marked as payer for payer-only logic)
    val namedParticipants = participantSpans.map { it.person }.distinctBy { it.id }

    // "for himself" or "for me" alone = personal
    if (personalExpense || (namedParticipants.size == 1 && namedParticipants[0].id == payerId)) {
        personalExpense = true
    }

    // Explicit splits detection: patterns like "400-arsh", "arsh 400", "arsh: 400", "arsh 40%"
    val explicitSplits = mutableMapOf<Long, Int>()
    var splitType = SplitType.EQUAL
    var restEqual = false

    // Check for "rest equal"
    if (tokens.any { it.lowercase() == "rest" } && tokens.any { it.lowercase() == "equal" }) {
        restEqual = true
        splitType = SplitType.REST_EQUAL
        tokens.indexOfFirst { it.lowercase() == "rest" }.let { consumed[it] = true }
        tokens.indexOfFirst { it.lowercase() == "equal" }.let { consumed[it] = true }
    }

    // Parse explicit amount or percentage splits
    for (span in participantSpans) {
        val afterEnd = tokens.getOrNull(span.end)
        val beforeStart = tokens.getOrNull(span.start - 1)

        // "arsh 400" or "arsh: 400" or "arsh 40%"
        if (afterEnd != null && !consumed[span.end]) {
            val pct = afterEnd.removeSuffix("%")
            if (pct != afterEnd) {
                val p = pct.toIntOrNull()
                if (p != null) {
                    explicitSplits[span.person.id] = p // store as percentage temporarily
                    consumed[span.end] = true
                    splitType = SplitType.PERCENTAGE
                }
            } else {
                val colonCleaned = afterEnd.trimEnd(':')
                val amt = parseAmount(colonCleaned)
                if (amt != null && amt != amount) {
                    explicitSplits[span.person.id] = amt
                    consumed[span.end] = true
                    if (splitType != SplitType.REST_EQUAL) splitType = SplitType.EXPLICIT
                }
            }
        }
        // "400-arsh" pattern
        if (beforeStart != null && !consumed[span.start - 1]) {
            val hyphenParts = beforeStart.split("-")
            if (hyphenParts.size == 2) {
                val amt = parseAmount(hyphenParts[0])
                if (amt != null) {
                    explicitSplits[span.person.id] = amt
                    consumed[span.start - 1] = true
                    if (splitType != SplitType.REST_EQUAL) splitType = SplitType.EXPLICIT
                }
            }
        }
    }

    // ── 11. Purpose and extra info ────────────────────────────────────────────
    // Consume stop words
    for (k in tokens.indices) {
        if (!consumed[k] && tokens[k].lowercase() in STOP_WORDS) consumed[k] = true
        if (!consumed[k] && tokens[k].lowercase() in JUNK_WORDS) consumed[k] = true
    }

    val remaining = tokens.indices.filter { !consumed[it] }.map { tokens[it] }

    // Match purpose dictionary
    var label: String? = null
    var purposeText: String? = null
    val extraWords = mutableListOf<String>()

    // Check for vendor auto-label
    if (vendorId != null) {
        label = "Tiffin"
        purposeText = "Tiffin"
    } else {
        for (word in remaining) {
            val w = word.lowercase()
            val dictLabel = ctx.purposeMap[w]
                ?: ctx.purposeMap.entries.firstOrNull { (key, _) ->
                    key.length >= 5 && w.length >= 5 && editDistance(w, key) == 1
                }?.value
            if (dictLabel != null && label == null) {
                label = dictLabel
                purposeText = word
            } else {
                // Check city expansion
                val cityFull = ctx.cityMap[w]
                if (cityFull != null) extraWords.add(cityFull)
                else extraWords.add(word)
            }
        }
    }

    // Check typo: leftover word 5+ letters near a registered alias
    for (word in remaining) {
        val w = word.lowercase()
        if (w.length >= 5 && w !in ctx.purposeMap && !ctx.cityMap.containsKey(w)) {
            val allAliases = ctx.people.filter { it.deletedAt == null }
                .flatMap { it.nicknames + listOf(it.mainName.lowercase()) }
            val nearAlias = allAliases.any { alias ->
                alias.length >= 5 && editDistance(w, alias.lowercase()) == 1
            }
            if (nearAlias) return TypoError(line, "Near-alias typo: $word")
        }
    }

    // Handle city route expansion in extra info
    val extraInfo = if (extraWords.isNotEmpty()) {
        // Try to build route string with "to" connector
        expandCityRoute(extraWords, ctx.cityMap)
    } else null

    // ── 12. Build participants and shares ─────────────────────────────────────
    if (personalExpense || namedParticipants.isEmpty() && vendorId != null) {
        // Personal expense (vendor tiffin with no friends, or "for me" etc.)
        if (vendorId != null) {
            return ParsedEntry(
                kind = EntryKind.EXPENSE,
                payerId = payerId,
                receiverId = null,
                vendorId = vendorId,
                total = amount,
                participants = listOf(ParticipantShare(payerId, amount)),
                label = "Tiffin",
                purposeText = "Tiffin",
                extraInfo = extraInfo,
                expenseDate = expenseDate,
                forDate = forDate,
                timeText = timeText,
                originalText = original,
                groupTag = effectiveGroupTag,
            )
        }
        return ParsedEntry(
            kind = EntryKind.EXPENSE,
            payerId = payerId,
            receiverId = null,
            vendorId = null,
            total = amount,
            participants = listOf(ParticipantShare(payerId, amount)),
            label = label ?: remaining.joinToString(" ").capitalize(),
            purposeText = purposeText ?: remaining.joinToString(" ").ifEmpty { null },
            extraInfo = extraInfo,
            expenseDate = expenseDate,
            forDate = forDate,
            timeText = timeText,
            originalText = original,
            groupTag = effectiveGroupTag,
        )
    }

    if (namedParticipants.isEmpty() && vendorId == null) {
        // Need to ask "Who was this for?"
        return NeedsAnswer(
            partial = PartialEntry(
                kind = EntryKind.EXPENSE,
                payerId = payerId,
                receiverId = null,
                vendorId = null,
                total = amount,
                participantIds = null,
                splits = explicitSplits.ifEmpty { null },
                splitType = splitType,
                onlyFlag = onlyFlag,
                label = label,
                purposeText = purposeText,
                extraInfo = extraInfo,
                expenseDate = expenseDate,
                forDate = forDate,
                timeText = timeText,
                originalText = original,
                groupTag = effectiveGroupTag,
            ),
            question = Question.WhoWasThisFor
        )
    }

    // Build the participant set
    val participantSet = namedParticipants.distinctBy { it.id }.toMutableList()
    // Payer takes part by default unless "only" is used and payer not explicitly listed
    val payerListed = participantSet.any { it.id == payerId }
    if (!onlyFlag && !payerListed) {
        ctx.people.firstOrNull { it.id == payerId }?.let { participantSet.add(it) }
    }

    if (label == null) {
        // No purpose found — ask
        return NeedsAnswer(
            partial = PartialEntry(
                kind = EntryKind.EXPENSE,
                payerId = payerId,
                receiverId = null,
                vendorId = vendorId,
                total = amount,
                participantIds = participantSet.map { it.id },
                splits = explicitSplits.ifEmpty { null },
                splitType = splitType,
                onlyFlag = onlyFlag,
                label = null,
                purposeText = null,
                extraInfo = extraInfo,
                expenseDate = expenseDate,
                forDate = forDate,
                timeText = timeText,
                originalText = original,
                groupTag = effectiveGroupTag,
            ),
            question = Question.WhatWasThisFor
        )
    }

    val shares = computeShares(amount, participantSet.map { it.id }, explicitSplits, splitType, payerId)
        ?: return TypoError(line, "Explicit split amounts exceed the bill")

    return ParsedEntry(
        kind = EntryKind.EXPENSE,
        payerId = payerId,
        receiverId = null,
        vendorId = vendorId,
        total = amount,
        participants = shares,
        label = label,
        purposeText = purposeText,
        extraInfo = extraInfo,
        expenseDate = expenseDate,
        forDate = forDate,
        timeText = timeText,
        originalText = original,
        groupTag = effectiveGroupTag,
    )
}

// Finds the bill amount from unconsumed tokens following the verb rules.
private fun findAmount(tokens: List<String>, consumed: BooleanArray, verb: VerbMatch): Int? {
    val verbIdx = verb.index
    val nextAfterBack = if (tokens.getOrNull(verbIdx + 1)?.lowercase() == "back") verbIdx + 2 else verbIdx + 1

    // Rule a: number directly after the verb (or after "back")
    val candA = tokens.getOrNull(nextAfterBack)
    if (candA != null && !consumed[nextAfterBack]) {
        val amt = parseAmount(candA)
        if (amt != null) {
            // Check what follows
            val afterAmt = tokens.getOrNull(nextAfterBack + 1)?.lowercase()
            if (afterAmt == null || afterAmt in setOf("for", "on", "to", "by") ||
                isPersonOrEnd(afterAmt, tokens, nextAfterBack + 1)
            ) return amt
        }
    }

    // Rule b: number after first "for" or "on"
    val forOnIdx = tokens.indexOfFirst { it.lowercase() in setOf("for", "on") && !consumed[tokens.indexOf(it)] }
    if (forOnIdx >= 0) {
        val candB = tokens.getOrNull(forOnIdx + 1)
        if (candB != null) {
            val amt = parseAmount(candB)
            if (amt != null && !consumed[forOnIdx + 1]) return amt
        }
    }

    // Rule c: number before "paid by"
    val paidByIdx = tokens.indexOfFirst { it.lowercase() == "paid" }.let {
        if (it >= 0 && tokens.getOrNull(it + 1)?.lowercase() == "by") it else -1
    }
    if (paidByIdx > 0) {
        val candC = tokens.getOrNull(paidByIdx - 1)
        if (candC != null && !consumed[paidByIdx - 1]) {
            val amt = parseAmount(candC)
            if (amt != null) return amt
        }
    }

    // Any remaining number token
    for (k in tokens.indices) {
        if (!consumed[k]) {
            val amt = parseAmount(tokens[k])
            if (amt != null) return amt
        }
    }
    return null
}

private fun isPersonOrEnd(token: String, tokens: List<String>, idx: Int): Boolean = true

private fun markAmountConsumed(tokens: List<String>, consumed: BooleanArray, amount: Int) {
    for (k in tokens.indices) {
        if (!consumed[k]) {
            if (parseAmount(tokens[k]) == amount) {
                consumed[k] = true; return
            }
        }
    }
}

fun computeShares(
    total: Int,
    participantIds: List<Long>,
    explicitSplits: Map<Long, Int>,
    splitType: SplitType,
    payerId: Long,
): List<ParticipantShare>? {
    val distinct = participantIds.distinct()
    return when (splitType) {
        SplitType.EQUAL -> {
            val shares = splitEqual(total, distinct.size)
            distinct.mapIndexed { i, id -> ParticipantShare(id, shares[i]) }
        }
        SplitType.EXPLICIT -> {
            val explicitTotal = explicitSplits.values.sum()
            if (explicitTotal > total) return null // typo error
            val remainder = total - explicitTotal
            val shares = mutableListOf<ParticipantShare>()
            for (id in distinct) {
                val explicit = explicitSplits[id]
                if (explicit != null) shares.add(ParticipantShare(id, explicit))
                else if (id == payerId) shares.add(ParticipantShare(id, remainder))
                // else: not in explicit, not payer — no share (payer absorbs remainder)
            }
            shares
        }
        SplitType.PERCENTAGE -> {
            val pctTotal = explicitSplits.values.sum()
            val shares = mutableListOf<ParticipantShare>()
            for (id in distinct) {
                val pct = explicitSplits[id]
                if (pct != null) {
                    shares.add(ParticipantShare(id, ceil(total * pct / 100.0).toInt()))
                }
            }
            val coveredIds = shares.map { it.personId }.toSet()
            val uncoveredIds = distinct.filter { it !in coveredIds }
            val coveredTotal = shares.sumOf { it.amount }
            if (coveredTotal > total) return null
            if (uncoveredIds.isNotEmpty()) {
                val remainder = total - coveredTotal
                val equal = splitEqual(remainder, uncoveredIds.size)
                uncoveredIds.forEachIndexed { i, id -> shares.add(ParticipantShare(id, equal[i])) }
            }
            shares
        }
        SplitType.REST_EQUAL -> {
            val fixedTotal = explicitSplits.values.sum()
            if (fixedTotal > total) return null
            val remainder = total - fixedTotal
            val withoutFixed = distinct.filter { it !in explicitSplits }
            val equal = splitEqual(remainder, withoutFixed.size)
            val shares = mutableListOf<ParticipantShare>()
            for (id in distinct) {
                val explicit = explicitSplits[id]
                if (explicit != null) shares.add(ParticipantShare(id, explicit))
            }
            withoutFixed.forEachIndexed { i, id -> shares.add(ParticipantShare(id, equal[i])) }
            shares
        }
    }
}

private fun expandCityRoute(words: List<String>, cityMap: Map<String, String>): String {
    return words.joinToString(" ") { w ->
        cityMap[w.lowercase()] ?: w
    }
}

@Suppress("DEPRECATION")
private fun String.capitalize() = replaceFirstChar { it.uppercase() }
