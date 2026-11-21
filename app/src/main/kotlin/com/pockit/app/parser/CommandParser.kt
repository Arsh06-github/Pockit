package com.pockit.app.parser

import com.pockit.app.ledger.parseAmount
import java.time.LocalDate

sealed class Command

data class SearchCommand(
    val description: String,
    val groupTag: String?,
) : Command()

data class EditCommand(
    val description: String,
    val changes: String,
    val groupTag: String?,
) : Command()

data class DeleteCommand(
    val description: String,
    val groupTag: String?,
) : Command()

data class ClearCommand(
    val scope: ClearScope,
    val from: LocalDate,
    val to: LocalDate,
    val groupTag: String?,
) : Command()

sealed class ClearScope {
    data class Person(val personId: Long) : ClearScope()
    object MyExpenses : ClearScope()
}

data class BalanceTwoCommand(val personAId: Long, val personBId: Long, val groupTag: String?) : Command()
data class BalanceOneCommand(val personId: Long, val groupTag: String?) : Command()
data class SettleUpCommand(val groupTag: String?) : Command()
data class LastNCommand(val n: Int, val personAId: Long?, val personBId: Long?, val groupTag: String?) : Command()
data class DateRangeCommand(val personAId: Long?, val personBId: Long?, val from: LocalDate, val to: LocalDate, val groupTag: String?) : Command()

fun parseCommand(line: String, ctx: ParserContext): Command? {
    val trimmed = line.trim()
    val lower = trimmed.lowercase()

    // Extract group tag
    var groupTag: String? = null
    var work = trimmed
    val tagMatch = Regex("""@(\S+)""").find(work)
    if (tagMatch != null) {
        val tag = tagMatch.groupValues[1]
        val tagLower = tag.lowercase()
        if (tagLower != "search" && tagLower != "edit" && tagLower != "general") {
            val group = ctx.groups.firstOrNull { it.name.lowercase() == tagLower && it.deletedAt == null }
            if (group != null) {
                groupTag = group.name
                work = work.replace(tagMatch.value, "").trim()
            }
        }
    }

    return when {
        work.startsWith("@Search", ignoreCase = true) -> {
            val desc = work.removePrefix("@Search").removePrefix("@search").trim()
            SearchCommand(desc, groupTag)
        }
        work.startsWith("@Edit", ignoreCase = true) -> {
            val rest = work.removePrefix("@Edit").removePrefix("@edit").trim()
            // Split description from change. Change signals: add/remove at start,
            // or a bill/total/whole/share/owes/owed/due keyword, or the final "to <value>".
            // Strategy: find the first change keyword that is NOT part of a person/date description.
            val changeSignals = listOf("add ", "remove ", " bill ", " total ", " whole ",
                " share ", " owes ", " owed ", " due ", " pending ")
            val splitIdx = changeSignals.mapNotNull { signal ->
                val idx = rest.lowercase().indexOf(signal)
                if (idx > 0) idx else null  // must have a description before the signal
            }.minOrNull()
            val (description, changes) = if (splitIdx != null) {
                rest.substring(0, splitIdx).trim() to rest.substring(splitIdx).trim()
            } else {
                rest to rest  // cannot split; pass full text to both; parser will handle
            }
            EditCommand(description, changes, groupTag)
        }
        lower.startsWith("delete ") -> {
            val desc = work.removePrefix("delete ").removePrefix("Delete ").trim()
            DeleteCommand(desc, groupTag)
        }
        lower.startsWith("clear ") -> {
            parseClear(work, ctx, groupTag)
        }
        lower == "settle up" || lower.startsWith("settle up") -> {
            SettleUpCommand(groupTag)
        }
        lower.startsWith("last ") && lower.contains("expenses") -> {
            parseLastN(work, ctx, groupTag)
        }
        lower.startsWith("expenses between ") -> {
            parseDateRange(work, ctx, groupTag)
        }
        isBalanceQuery(lower) -> {
            parseBalance(work, ctx, groupTag)
        }
        else -> null
    }
}

private fun isBalanceQuery(lower: String): Boolean {
    return lower.contains("owes") || lower.contains("owed") ||
        lower.startsWith("how much") || lower.endsWith("balance") ||
        lower.endsWith("owe")
}

private fun parseClear(line: String, ctx: ParserContext, groupTag: String?): Command? {
    val lower = line.lowercase()
    // "clear my expenses from <date> to <date>"
    if (lower.startsWith("clear my expenses")) {
        val rest = line.removePrefix("clear my expenses").removePrefix("Clear my expenses").trim()
        val (from, to) = extractDateRange(rest, ctx) ?: return null
        return ClearCommand(ClearScope.MyExpenses, from, to, groupTag)
    }
    // "clear <person> chat from <date> to <date>"
    val tokens = line.split(Regex("""\s+"""))
    if (tokens.size < 2) return null
    // Find person after "clear"
    val aliasIndex = buildAliasIndex(ctx.people)
    val owner = ctx.people.firstOrNull { it.isOwner } ?: return null
    // Try to match person by looking after "clear"
    for (len in minOf(4, tokens.size - 1) downTo 1) {
        val phrase = tokens.subList(1, 1 + len).joinToString(" ")
        val person = ctx.people.firstOrNull { it.mainName == phrase && it.deletedAt == null }
            ?: aliasIndex[phrase.lowercase()]
        if (person != null) {
            val rest = tokens.drop(1 + len).dropWhile { it.lowercase() in setOf("chat", "from") }
                .joinToString(" ")
            val (from, to) = extractDateRange(rest, ctx) ?: return null
            return ClearCommand(ClearScope.Person(person.id), from, to, groupTag)
        }
    }
    return null
}

private fun extractDateRange(text: String, ctx: ParserContext): Pair<LocalDate, LocalDate>? {
    val tokens = text.split(Regex("""\s+""")).filter { it.isNotBlank() }
    var fromDate: LocalDate? = null
    var toDate: LocalDate? = null
    var i = 0
    while (i < tokens.size) {
        if (tokens[i].lowercase() == "from") {
            i++
            val r = tryParseDate(tokens, i, ctx.today)
            if (r != null) { fromDate = (r.first as? DateResult.Exact)?.date; i += r.second; continue }
        }
        if (tokens[i].lowercase() == "to") {
            i++
            val r = tryParseDate(tokens, i, ctx.today)
            if (r != null) { toDate = (r.first as? DateResult.Exact)?.date; i += r.second; continue }
        }
        i++
    }
    return if (fromDate != null && toDate != null) fromDate to toDate else null
}

private fun parseLastN(line: String, ctx: ParserContext, groupTag: String?): Command? {
    val tokens = line.split(Regex("""\s+"""))
    val n = tokens.getOrNull(1)?.toIntOrNull() ?: return null
    // find people "between X and Y"
    val btIdx = tokens.indexOfFirst { it.lowercase() == "between" }
    if (btIdx < 0) return LastNCommand(n, null, null, groupTag)
    val aliasIndex = buildAliasIndex(ctx.people)
    val owner = ctx.people.firstOrNull { it.isOwner } ?: return null
    val andIdx = tokens.indexOfFirst { it.lowercase() == "and" && tokens.indexOf(it) > btIdx }
    if (andIdx < 0) return null
    val personA = resolvePersonTokens(tokens, btIdx + 1, andIdx, ctx, aliasIndex, owner)
    val personB = resolvePersonTokens(tokens, andIdx + 1, tokens.size, ctx, aliasIndex, owner)
    return LastNCommand(n, personA, personB, groupTag)
}

private fun parseDateRange(line: String, ctx: ParserContext, groupTag: String?): Command? {
    val aliasIndex = buildAliasIndex(ctx.people)
    val owner = ctx.people.firstOrNull { it.isOwner } ?: return null
    val tokens = line.split(Regex("""\s+"""))
    val btIdx = tokens.indexOfFirst { it.lowercase() == "between" }
    val fromIdx = tokens.indexOfFirst { it.lowercase() == "from" }
    var personA: Long? = null
    var personB: Long? = null
    if (btIdx >= 0) {
        val andIdx = tokens.indexOfFirst { it.lowercase() == "and" && tokens.indexOf(it) > btIdx }
        if (andIdx >= 0) {
            val endOfPeople = fromIdx.takeIf { it > 0 } ?: andIdx
            personA = resolvePersonTokens(tokens, btIdx + 1, andIdx, ctx, aliasIndex, owner)
            personB = resolvePersonTokens(tokens, andIdx + 1, endOfPeople, ctx, aliasIndex, owner)
        }
    }
    val rest = tokens.drop(fromIdx.takeIf { it >= 0 } ?: 0).joinToString(" ")
    val (from, to) = extractDateRange(rest, ctx) ?: return null
    return DateRangeCommand(personA, personB, from, to, groupTag)
}

private fun parseBalance(line: String, ctx: ParserContext, groupTag: String?): Command? {
    val aliasIndex = buildAliasIndex(ctx.people)
    val owner = ctx.people.firstOrNull { it.isOwner } ?: return null
    val tokens = line.split(Regex("""\s+""")).filter { it.isNotBlank() }
    val owesIdx = tokens.indexOfFirst { it.lowercase() == "owes" }
    if (owesIdx >= 0) {
        val personA = resolvePersonTokens(tokens, 0, owesIdx, ctx, aliasIndex, owner)
        val personB = resolvePersonTokens(tokens, owesIdx + 1, tokens.size, ctx, aliasIndex, owner)
        if (personA != null && personB != null) return BalanceTwoCommand(personA, personB, groupTag)
    }
    // "how much does X owe Y"
    val doesIdx = tokens.indexOfFirst { it.lowercase() == "does" }
    val oweIdx = tokens.indexOfFirst { it.lowercase() in setOf("owe", "owed") }
    if (doesIdx >= 0 && oweIdx >= 0) {
        val personA = resolvePersonTokens(tokens, doesIdx + 1, oweIdx, ctx, aliasIndex, owner)
        val personB = resolvePersonTokens(tokens, oweIdx + 1, tokens.size, ctx, aliasIndex, owner)
        if (personA != null && personB != null) return BalanceTwoCommand(personA, personB, groupTag)
        if (personA != null) return BalanceOneCommand(personA, groupTag)
    }
    // "X balance"
    val balIdx = tokens.indexOfFirst { it.lowercase() == "balance" }
    if (balIdx > 0) {
        val personId = resolvePersonTokens(tokens, 0, balIdx, ctx, aliasIndex, owner)
        if (personId != null) return BalanceOneCommand(personId, groupTag)
    }
    return null
}

private fun resolvePersonTokens(
    tokens: List<String>,
    from: Int,
    to: Int,
    ctx: ParserContext,
    aliasIndex: Map<String, PersonRecord>,
    owner: PersonRecord,
): Long? {
    if (from >= to) return null
    val phrase = tokens.subList(from, to).joinToString(" ").trim()
    if (phrase.lowercase() in OWNER_ALIASES || phrase.lowercase() == "me" || phrase.lowercase() == "i") return owner.id
    val byMain = ctx.people.firstOrNull { it.mainName == phrase && it.deletedAt == null }
    if (byMain != null) return byMain.id
    return aliasIndex[phrase.lowercase()]?.id
}
