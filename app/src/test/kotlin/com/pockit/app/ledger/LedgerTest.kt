package com.pockit.app.ledger

import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerTest {

    // --- parseAmount ---

    @Test fun `100 is 100`() = assertEquals(100, parseAmount("100"))

    @Test fun `2k is 2000`() = assertEquals(2000, parseAmount("2k"))

    @Test fun `1_5k is 1500`() = assertEquals(1500, parseAmount("1.5k"))

    @Test fun `17_5k is 17500`() = assertEquals(17500, parseAmount("17.5k"))

    @Test fun `100k is 100000`() = assertEquals(100000, parseAmount("100k"))

    @Test fun `202_44 rounds up to 203`() = assertEquals(203, parseAmount("202.44"))

    @Test fun `22_2 rounds up to 23`() = assertEquals(23, parseAmount("22.2"))

    @Test fun `22_5 rounds up to 23`() = assertEquals(23, parseAmount("22.5"))

    @Test fun `22_9 rounds up to 23`() = assertEquals(23, parseAmount("22.9"))

    @Test fun `rupee prefix stripped`() = assertEquals(500, parseAmount("₹500"))

    @Test fun `rs prefix stripped`() = assertEquals(500, parseAmount("rs 500"))

    @Test fun `comma stripped`() = assertEquals(2000, parseAmount("2,000"))

    @Test fun `250_50 rounds to 251`() = assertEquals(251, parseAmount("250.50"))

    // --- splitEqual ---

    @Test fun `100 among 3 gives 34 each`() {
        val shares = splitEqual(100, 3)
        assertEquals(3, shares.size)
        shares.forEach { assertEquals(34, it) }
    }

    @Test fun `2000 among 3 gives 667 each`() {
        val shares = splitEqual(2000, 3)
        assertEquals(3, shares.size)
        shares.forEach { assertEquals(667, it) }
    }

    @Test fun `300 among 2 gives 150 each`() {
        val shares = splitEqual(300, 2)
        assertEquals(listOf(150, 150), shares)
    }

    // --- settleUp ---

    @Test fun `simple two-person settle`() {
        // Vaibhav owes Me 200
        val dues = listOf(Due(debtorId = 2, creditorId = 1, amount = 200))
        val payments = settleUp(dues)
        assertEquals(1, payments.size)
        assertEquals(Payment(fromId = 2, toId = 1, amount = 200), payments[0])
    }

    @Test fun `three-person settle minimum payments`() {
        // A owes C 500, B owes C 300 — 2 payments
        val dues = listOf(
            Due(debtorId = 1, creditorId = 3, amount = 500),
            Due(debtorId = 2, creditorId = 3, amount = 300)
        )
        val payments = settleUp(dues)
        assertEquals(2, payments.size)
        val total = payments.sumOf { it.amount }
        assertEquals(800, total)
    }

    @Test fun `dinner example net balances`() {
        // Me pays 1000 for dinner for vighnesh(3), dhruv(4), vaibhav(2) — 250 each
        // Vaibhav pays 200 for dinner for Me — 100 each
        val dues = listOf(
            Due(debtorId = 3, creditorId = 1, amount = 250), // vighnesh owes me
            Due(debtorId = 4, creditorId = 1, amount = 250), // dhruv owes me
            Due(debtorId = 2, creditorId = 1, amount = 250), // vaibhav owes me
            Due(debtorId = 1, creditorId = 2, amount = 100), // me owes vaibhav
        )
        // Net: vaibhav owes me 150, dhruv owes me 250, vighnesh owes me 250
        val payments = settleUp(dues)
        val payToMe = payments.filter { it.toId == 1L }.sumOf { it.amount }
        // me owes vaibhav 100, so net payment from vaibhav to me is 150
        val fromVaibhav = payments.filter { it.fromId == 2L && it.toId == 1L }
            .sumOf { it.amount }
        val fromDhruv = payments.filter { it.fromId == 4L }.sumOf { it.amount }
        val fromVighnesh = payments.filter { it.fromId == 3L }.sumOf { it.amount }
        // The settle-up works on net positions: me net +400, vaibhav net -150, dhruv -250, vighnesh -250
        assertEquals(150, fromVaibhav)
        assertEquals(250, fromDhruv)
        assertEquals(250, fromVighnesh)
    }

    @Test fun `nothing to settle when no dues`() {
        assertEquals(emptyList<Payment>(), settleUp(emptyList()))
    }
}
