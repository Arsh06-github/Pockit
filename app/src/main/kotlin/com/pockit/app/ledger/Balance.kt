package com.pockit.app.ledger

// A due: `debtor` owes `creditor` the given amount, within one space.
data class Due(val debtorId: Long, val creditorId: Long, val amount: Int)

// Net balance for a pair: positive means first owes second.
data class PairNet(val personA: Long, val personB: Long, val amount: Int)

fun netBalances(dues: List<Due>): Map<Pair<Long, Long>, Int> {
    val map = mutableMapOf<Pair<Long, Long>, Int>()
    for (due in dues) {
        if (due.amount == 0) continue
        val key = if (due.debtorId < due.creditorId)
            due.debtorId to due.creditorId else due.creditorId to due.debtorId
        val sign = if (due.debtorId < due.creditorId) 1 else -1
        map[key] = (map[key] ?: 0) + sign * due.amount
    }
    return map
}

data class Payment(val fromId: Long, val toId: Long, val amount: Int)

// Minimum number of payments to settle all dues in one space.
fun settleUp(dues: List<Due>): List<Payment> {
    // Build net position per person: positive = is owed, negative = owes
    val net = mutableMapOf<Long, Int>()
    for (due in dues) {
        net[due.debtorId] = (net[due.debtorId] ?: 0) - due.amount
        net[due.creditorId] = (net[due.creditorId] ?: 0) + due.amount
    }
    val debtors = net.entries.filter { it.value < 0 }
        .map { it.key to -it.value }.toMutableList()
    val creditors = net.entries.filter { it.value > 0 }
        .map { it.key to it.value }.toMutableList()

    val payments = mutableListOf<Payment>()
    var i = 0; var j = 0
    while (i < debtors.size && j < creditors.size) {
        val (debtorId, debt) = debtors[i]
        val (creditorId, credit) = creditors[j]
        val amount = minOf(debt, credit)
        payments.add(Payment(debtorId, creditorId, amount))
        debtors[i] = debtorId to (debt - amount)
        creditors[j] = creditorId to (credit - amount)
        if (debtors[i].second == 0) i++
        if (creditors[j].second == 0) j++
    }
    return payments
}
