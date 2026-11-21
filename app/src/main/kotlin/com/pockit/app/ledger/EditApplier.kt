
package com.pockit.app.ledger

import com.pockit.app.parser.EditChange
import kotlin.math.ceil

fun applyEditChange(
    change: EditChange,
    entry: EntryData,
    shares: List<ShareData>,
): Pair<EntryData, List<ShareData>>? {
    return when (change) {

        is EditChange.BillTotal -> {
            val n = shares.size
            if (n == 0) null
            else {
                val amounts = splitEqual(change.newTotal, n)
                entry.copy(total = change.newTotal) to
                    shares.mapIndexed { i, s -> s.copy(amount = amounts[i]) }
            }
        }

        is EditChange.PersonShare -> {
            val target = shares.firstOrNull { it.personId == change.personId } ?: return null
            val diff = target.amount - change.newAmount
            val others = shares.filter { it.personId != change.personId }
            if (others.isEmpty()) return null

            val updated = if (change.absorberId != null) {
                shares.map {
                    when (it.personId) {
                        change.personId -> it.copy(amount = change.newAmount)
                        change.absorberId -> it.copy(amount = it.amount + diff)
                        else -> it
                    }
                }
            } else {
                val perOther = ceil(diff.toDouble() / others.size).toInt()
                shares.map {
                    if (it.personId == change.personId) it.copy(amount = change.newAmount)
                    else it.copy(amount = it.amount + perOther)
                }
            }

            if (updated.any { it.amount < 0 }) null
            else entry to updated
        }

        is EditChange.AddParticipant -> {
            if (shares.any { it.personId == change.personId }) return entry to shares
            val ids = shares.map { it.personId } + change.personId
            val amounts = splitEqual(entry.total, ids.size)
            entry to ids.mapIndexed { i, id -> ShareData(entry.id, id, amounts[i]) }
        }

        is EditChange.RemoveParticipant -> {
            val remaining = shares.filter { it.personId != change.personId }
            if (remaining.isEmpty()) null
            else {
                val amounts = splitEqual(entry.total, remaining.size)
                entry to remaining.mapIndexed { i, s -> s.copy(amount = amounts[i]) }
            }
        }

        is EditChange.ChangePayer ->
            entry.copy(payerId = change.newPayerId) to shares

        is EditChange.ChangePurpose ->
            entry.copy(label = change.newLabel, purposeText = change.newPurposeText) to shares

        is EditChange.ChangeDate ->
            entry.copy(expenseDate = change.newExpenseDate) to shares
    }
}
