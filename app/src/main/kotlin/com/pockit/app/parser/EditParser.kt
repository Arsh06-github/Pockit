package com.pockit.app.parser

import com.pockit.app.ledger.parseAmount

sealed class EditChange {
    data class BillTotal(val newTotal: Int) : EditChange()
    // absorberId: if non-null, that person absorbs the diff; if null, spread equally among others
    data class PersonShare(val personId: Long, val newAmount: Int, val absorberId: Long?) : EditChange()
    data class AddParticipant(val personId: Long) : EditChange()
    data class RemoveParticipant(val personId: Long) : EditChange()
    data class ChangePayer(val newPayerId: Long) : EditChange()
    data class ChangePurpose(val newLabel: String, val newPurposeText: String) : EditChange()
    data class ChangeDate(val newExpenseDate: Long) : EditChange()
}

/**
 * Parses the change portion of an @Edit command.
 *
 * Steering section 9.4 rule: "The final `to <value>` is the new value; earlier `to` words
 * are part of the description." So the LAST " to <amount>" in the string is the target value.
 *
 * For share-change sentences like "change the amount pending by vaibhav to dhruv ... to 1000":
 *   - The person whose share changes is identified by the keyword before their name:
 *     "pending by X", "owed by X", "owes X", "due from X", "X's share".
 *   - The name after an earlier "to" (e.g. "to dhruv") belongs to the entry description
 *     (it names the payer) and is NOT the share-change target.
 *   - Do NOT scan from the tail of the sentence; look for explicit ownership keywords.
 *
 * Returns null on unrecognised input (typo error shown to user).
 */
fun parseEditChange(changeText: String, ctx: ParserContext): EditChange? {
    val aliasIndex = buildAliasIndex(ctx.people)
    val owner = ctx.people.firstOrNull { it.isOwner } ?: return null
    val lower = changeText.lowercase().trim()

    // "add <person> to ..."
    if (lower.startsWith("add ")) {
        val after = lower.removePrefix("add ").trim()
        val personName = after.substringBefore(" to ").trim()
        val person = resolvePersonByText(personName, ctx, aliasIndex, owner) ?: return null
        return EditChange.AddParticipant(person.id)
    }

    // "remove <person> from ..."
    if (lower.startsWith("remove ")) {
        val after = lower.removePrefix("remove ").trim()
        val personName = after.substringBefore(" from ").trim()
        val person = resolvePersonByText(personName, ctx, aliasIndex, owner) ?: return null
        return EditChange.RemoveParticipant(person.id)
    }

    // The LAST " to <amount>" is the new value.
    val toLastIdx = lower.lastIndexOf(" to ")
    val newAmount = if (toLastIdx >= 0) parseAmount(lower.substring(toLastIdx + 4).trim()) else null

    // "bill" / "total" / "whole" → whole bill changes
    if (lower.contains("bill") || lower.contains("total") || lower.contains("whole")) {
        newAmount ?: return null
        return EditChange.BillTotal(newAmount)
    }

    // Share-change patterns: identify the person by an ownership keyword, NOT by tail scan.
    // Supported phrases: "pending by X", "owed by X", "due from X", "owes X", "X's share", "X's pending"
    val shareKeywords = listOf("share", "pending", "owes", "owed", "due")
    if (shareKeywords.any { lower.contains(it) } && newAmount != null) {
        // Extract the person using ownership-keyword lookup (before the final "to <value>").
        val descPart = if (toLastIdx >= 0) lower.substring(0, toLastIdx) else lower
        val personId = findShareSubject(descPart, ctx, aliasIndex, owner)
        return EditChange.PersonShare(personId ?: owner.id, newAmount, null)
    }

    // Bare "to <amount>" at end — if text before names exactly one person treat as their share,
    // otherwise treat as bill total.
    if (newAmount != null && toLastIdx >= 0) {
        val before = lower.substring(0, toLastIdx).trim()
        val personId = findPersonInText(before, ctx, aliasIndex, owner)
        return if (personId != null) EditChange.PersonShare(personId, newAmount, null)
        else EditChange.BillTotal(newAmount)
    }

    return null
}

/**
 * Finds the person whose share is being changed from ownership-keyword patterns.
 *
 * Patterns searched (in order of priority):
 *   "pending by <name>"   → name is the debtor (share-holder who owes)
 *   "owed by <name>"      → same
 *   "due from <name>"     → same
 *   "owes <name>"         → name is the debtor
 *   "<name>'s share"      → name is the share-holder
 *   "<name>'s pending"    → name is the share-holder
 *
 * This deliberately does NOT look at "to <name>" — that names the payer/creditor
 * and belongs to the entry description.
 */
private fun findShareSubject(
    text: String,
    ctx: ParserContext,
    aliasIndex: Map<String, PersonRecord>,
    owner: PersonRecord,
): Long? {
    // "pending by <name>", "owed by <name>"
    for (prefix in listOf("pending by ", "owed by ")) {
        val idx = text.indexOf(prefix)
        if (idx >= 0) {
            val nameText = text.substring(idx + prefix.length)
                .substringBefore(" to ").substringBefore(" for ").substringBefore(" on ").trim()
            val id = findPersonInText(nameText, ctx, aliasIndex, owner)
            if (id != null) return id
        }
    }

    // "due from <name>"
    val dueFromIdx = text.indexOf("due from ")
    if (dueFromIdx >= 0) {
        val nameText = text.substring(dueFromIdx + 9)
            .substringBefore(" to ").substringBefore(" for ").trim()
        val id = findPersonInText(nameText, ctx, aliasIndex, owner)
        if (id != null) return id
    }

    // "owes <name>" (the named person is the creditor — but here "owes" means the entry
    // has a pending amount FROM someone TO someone; the person before "owes" is the debtor)
    val owesIdx = text.indexOf(" owes ")
    if (owesIdx >= 0) {
        val before = text.substring(0, owesIdx).trim()
        val id = findPersonInText(before, ctx, aliasIndex, owner)
        if (id != null) return id
    }

    // "<name>'s share" or "<name>'s pending"
    for (suffix in listOf("'s share", "'s pending")) {
        val idx = text.indexOf(suffix)
        if (idx >= 0) {
            val namePart = text.substring(0, idx).trim()
            val id = findPersonInText(namePart, ctx, aliasIndex, owner)
            if (id != null) return id
        }
    }

    return null
}

private fun resolvePersonByText(
    text: String,
    ctx: ParserContext,
    aliasIndex: Map<String, PersonRecord>,
    owner: PersonRecord,
): PersonRecord? {
    val lower = text.lowercase().trim()
    if (lower in OWNER_ALIASES) return owner
    return ctx.people.firstOrNull { it.mainName.lowercase() == lower && it.deletedAt == null }
        ?: aliasIndex[lower]
}

// Finds a person by trying every suffix of the word list (longest first).
private fun findPersonInText(
    text: String,
    ctx: ParserContext,
    aliasIndex: Map<String, PersonRecord>,
    owner: PersonRecord,
): Long? {
    val words = text.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    for (len in minOf(4, words.size) downTo 1) {
        val phrase = words.takeLast(len).joinToString(" ").lowercase()
        if (phrase in OWNER_ALIASES) return owner.id
        val p = ctx.people.firstOrNull { it.mainName.lowercase() == phrase && it.deletedAt == null }
            ?: aliasIndex[phrase]
        if (p != null) return p.id
    }
    return null
}
