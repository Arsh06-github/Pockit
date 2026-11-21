package com.pockit.app.parser

enum class VerbClass { EXPENSE, LOAN, REPAYMENT }

data class VerbMatch(val verb: String, val kind: VerbClass, val index: Int)

private val EXPENSE_VERBS = setOf(
    "paid", "pay", "pays", "spent", "gave", "bought",
    "treated", "covered", "booked", "ordered", "purchased"
)
private val LOAN_VERBS = setOf("lent", "loaned", "sent", "transferred")
private val REPAYMENT_VERBS = setOf("returned", "repaid")

// "paid back", "gave back", "sent back" are repayment
private val BACK_TRIGGERS = setOf("paid", "gave", "sent")

fun findVerb(tokens: List<String>): VerbMatch? {
    for (i in tokens.indices) {
        val t = tokens[i].lowercase()
        val next = tokens.getOrNull(i + 1)?.lowercase()
        // Check repayment compound first
        if (t in BACK_TRIGGERS && next == "back") return VerbMatch(tokens[i], VerbClass.REPAYMENT, i)
        if (t in REPAYMENT_VERBS) return VerbMatch(tokens[i], VerbClass.REPAYMENT, i)
        if (t == "gave" && next == "to") return VerbMatch(tokens[i], VerbClass.LOAN, i)
        if (t in LOAN_VERBS) return VerbMatch(tokens[i], VerbClass.LOAN, i)
        if (t in EXPENSE_VERBS) return VerbMatch(tokens[i], VerbClass.EXPENSE, i)
    }
    return null
}
