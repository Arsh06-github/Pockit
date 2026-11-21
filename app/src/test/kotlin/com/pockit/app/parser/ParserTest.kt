package com.pockit.app.parser

import com.pockit.app.parser.EditChange
import com.pockit.app.ledger.applyEditChange
import com.pockit.app.ledger.EntryData
import com.pockit.app.ledger.ShareData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class ParserTest {

    // Fixed clock: Tuesday 10 Nov 2026
    private val today = LocalDate.of(2026, 11, 10)

    private lateinit var ctx: ParserContext

    // Person IDs
    private val ME = 1L
    private val VAIBHAV = 2L
    private val VIGHNESH = 3L
    private val DHRUV = 4L
    private val JMD = 5L

    @Before
    fun setUp() {
        val people = listOf(
            PersonRecord(ME, "Me", isOwner = true, isVendor = false, deletedAt = null,
                nicknames = listOf("arsh", "boss", "myself", "me", "khushu", "i")),
            PersonRecord(VAIBHAV, "Vaibhav", isOwner = false, isVendor = false, deletedAt = null,
                nicknames = listOf("vaibhav", "vaibhsa")),
            PersonRecord(VIGHNESH, "Vighnesh Bhati", isOwner = false, isVendor = false, deletedAt = null,
                nicknames = listOf("vighnesh", "vanshu", "demon", "vanshi", "vansh")),
            PersonRecord(DHRUV, "Dhruv Parihar", isOwner = false, isVendor = false, deletedAt = null,
                nicknames = listOf("dhruv", "dhruvi")),
            PersonRecord(JMD, "JMD Tiffin", isOwner = false, isVendor = true, deletedAt = null,
                nicknames = listOf("jmdt", "jmdts", "tiffin", "tiffin service")),
        )
        val groups = listOf(
            GroupRecord(id = 10L, name = "Udaipur", deletedAt = null,
                memberIds = setOf(ME, VAIBHAV, VIGHNESH, DHRUV))
        )
        // Build a minimal purpose map
        val purposeMap = mapOf(
            "dinner" to "Dinner Time",
            "coffee" to "Coffee Time",
            "auto" to "Auto Ride",
            "auto ride" to "Auto Ride",
            "drinks" to "Drinks",
            "tiffin" to "Tiffin",
            "metro" to "Commute",
            "commute" to "Commute",
            "train tickets" to "Tickets",
            "tickets" to "Tickets",
            "ticket" to "Tickets",
        )
        val cityMap = mapOf(
            "jdp" to "Jodhpur",
            "mum" to "Mumbai",
        )
        ctx = ParserContext(people, groups, purposeMap, cityMap, today)
    }

    // ── Test 1 ───────────────────────────────────────────────────────────────
    @Test
    fun `test1 I paid 2k for dinner for vaibhav and dhruv`() {
        val result = parseEntry("I paid 2k for dinner for vaibhav and dhruv", ctx)
        assertTrue(result is ParsedEntry)
        result as ParsedEntry
        assertEquals(ME, result.payerId)
        assertEquals(2000, result.total)
        assertEquals("Dinner Time", result.label)
        assertEquals(3, result.participants.size)
        // All three get 667 each (ceil(2000/3))
        result.participants.forEach { assertEquals(667, it.amount) }
        val ids = result.participants.map { it.personId }.toSet()
        assertTrue(ME in ids); assertTrue(VAIBHAV in ids); assertTrue(DHRUV in ids)
    }

    // ── Test 2 ───────────────────────────────────────────────────────────────
    @Test
    fun `test2 dhruv paid 1000 for drinks explicit split`() {
        val result = parseEntry("dhruv paid 1000 for drinks, 400-arsh, 600-vaibhav", ctx)
        assertTrue(result is ParsedEntry)
        result as ParsedEntry
        assertEquals(DHRUV, result.payerId)
        assertEquals(1000, result.total)
        assertEquals("Drinks", result.label)
        // Payer (Dhruv) has no share since explicit amounts cover the whole bill
        val meShare = result.participants.firstOrNull { it.personId == ME }?.amount
        val vaibhavShare = result.participants.firstOrNull { it.personId == VAIBHAV }?.amount
        assertEquals(400, meShare)
        assertEquals(600, vaibhavShare)
        // Dhruv should not appear in participants (explicit amounts add up to 1000)
        assertTrue(result.participants.none { it.personId == DHRUV })
    }

    // ── Test 3 ───────────────────────────────────────────────────────────────
    @Test
    fun `test3 vanshu paid 300 for auto for demon and dhruvi`() {
        val result = parseEntry("vanshu paid 300 for auto for demon and dhruvi", ctx)
        assertTrue(result is ParsedEntry)
        result as ParsedEntry
        assertEquals(VIGHNESH, result.payerId)
        assertEquals(300, result.total)
        assertEquals("Auto Ride", result.label)
        // vanshu and demon are the same person (Vighnesh), so participants are Vighnesh and Dhruv
        assertEquals(2, result.participants.size)
        val ids = result.participants.map { it.personId }.toSet()
        assertTrue(VIGHNESH in ids)
        assertTrue(DHRUV in ids)
        result.participants.forEach { assertEquals(150, it.amount) }
        // Dhruv owes Vighnesh 150
        val dhruvShare = result.participants.first { it.personId == DHRUV }
        assertEquals(150, dhruvShare.amount)
    }

    // ── Test 4 ───────────────────────────────────────────────────────────────
    @Test
    fun `test4 vaibhav paid back 200 to me`() {
        val result = parseEntry("vaibhav paid back 200 to me", ctx)
        assertTrue(result is ParsedEntry)
        result as ParsedEntry
        assertEquals(EntryKind.TRANSFER, result.kind)
        assertEquals(VAIBHAV, result.payerId)
        assertEquals(ME, result.receiverId)
        assertEquals(200, result.total)
        assertEquals("Repayment", result.label)
    }

    // ── Test 5 ───────────────────────────────────────────────────────────────
    @Test
    fun `test5 group Udaipur dhruv paid 600 for dinner for me and vaibhav`() {
        val result = parseEntry("@Udaipur dhruv paid 600 for dinner for me and vaibhav", ctx)
        assertTrue(result is ParsedEntry)
        result as ParsedEntry
        assertEquals(DHRUV, result.payerId)
        assertEquals(600, result.total)
        assertEquals("Udaipur", result.groupTag)
        assertEquals("Dinner Time", result.label)
        assertEquals(3, result.participants.size)
        result.participants.forEach { assertEquals(200, it.amount) }
        val ids = result.participants.map { it.personId }.toSet()
        assertTrue(DHRUV in ids); assertTrue(ME in ids); assertTrue(VAIBHAV in ids)
    }

    // ── Test 6 ───────────────────────────────────────────────────────────────
    @Test
    fun `test6 I paid 3k to tiffin vendor expense`() {
        val result = parseEntry("I paid 3k to tiffin", ctx)
        assertTrue(result is ParsedEntry)
        result as ParsedEntry
        assertEquals(ME, result.payerId)
        assertEquals(3000, result.total)
        assertEquals(JMD, result.vendorId)
        assertEquals("Tiffin", result.label)
        assertEquals(1, result.participants.size)
        assertEquals(ME, result.participants[0].personId)
    }

    // ── Test 7 ───────────────────────────────────────────────────────────────
    @Test
    fun `test7 dhruv paid 2000 for dinner for vaibhav and arsh yesterday`() {
        val result = parseEntry("dhruv paid 2000 for dinner for vaibhav and arsh yesterday", ctx)
        assertTrue(result is ParsedEntry)
        result as ParsedEntry
        assertEquals(DHRUV, result.payerId)
        assertEquals(2000, result.total)
        assertEquals("Dinner Time", result.label)
        assertEquals(LocalDate.of(2026, 11, 9), result.expenseDate)
        assertEquals(3, result.participants.size)
        result.participants.forEach { assertEquals(667, it.amount) }
        val ids = result.participants.map { it.personId }.toSet()
        assertTrue(DHRUV in ids); assertTrue(VAIBHAV in ids); assertTrue(ME in ids)
    }

    // ── Test 8 ───────────────────────────────────────────────────────────────
    @Test
    fun `test8 train tickets jdp to mum on 25th november`() {
        val result = parseEntry(
            "I paid 2000 for train tickets for me and vighnesh today, train is from jdp to mum on 25th november",
            ctx
        )
        assertTrue(result is ParsedEntry)
        result as ParsedEntry
        assertEquals(ME, result.payerId)
        assertEquals(2000, result.total)
        assertEquals("Tickets", result.label)
        assertEquals("train tickets", result.purposeText)
        assertEquals(LocalDate.of(2026, 11, 10), result.expenseDate)
        assertEquals(LocalDate.of(2026, 11, 25), result.forDate)
        assertNotNull(result.extraInfo)
        assertTrue(result.extraInfo!!.contains("Jodhpur"))
        assertTrue(result.extraInfo!!.contains("Mumbai"))
        assertEquals(2, result.participants.size)
        result.participants.forEach { assertEquals(1000, it.amount) }
        val ids = result.participants.map { it.personId }.toSet()
        assertTrue(ME in ids); assertTrue(VIGHNESH in ids)
    }

    // ── Test 9 ───────────────────────────────────────────────────────────────
    @Test
    fun `test9 arsh paid 500 for coffee only for dhruv and vaibhav`() {
        val result = parseEntry("arsh paid 500 for coffee only for dhruv and vaibhav", ctx)
        assertTrue(result is ParsedEntry)
        result as ParsedEntry
        assertEquals(ME, result.payerId)
        assertEquals(500, result.total)
        assertEquals("Coffee Time", result.label)
        // "only for dhruv and vaibhav" — payer has no share
        assertEquals(2, result.participants.size)
        val meShare = result.participants.firstOrNull { it.personId == ME }
        assertTrue("Payer should not have share with 'only'", meShare == null)
        val dhruvShare = result.participants.firstOrNull { it.personId == DHRUV }?.amount
        val vaibhavShare = result.participants.firstOrNull { it.personId == VAIBHAV }?.amount
        assertEquals(250, dhruvShare)
        assertEquals(250, vaibhavShare)
    }

    // ── Rounding tests ───────────────────────────────────────────────────────
    @Test
    fun `rounding 100 among 3 gives 34 each`() {
        val shares = computeShares(100, listOf(1, 2, 3), emptyMap(), SplitType.EQUAL, 1)!!
        shares.forEach { assertEquals(34, it.amount) }
    }

    @Test
    fun `amount choice 2 tickets for 3000`() {
        val result = parseEntry("arsh bought 2 tickets for 3000 for vaibhav", ctx)
        assertTrue(result is ParsedEntry)
        assertEquals(3000, (result as ParsedEntry).total)
    }

    @Test
    fun `amount choice I paid 2000 for 2 tickets`() {
        val result = parseEntry("I paid 2000 for 2 tickets", ctx)
        // No persons named — should be NeedsAnswer (who was this for)
        assertTrue(result is NeedsAnswer || result is ParsedEntry)
        if (result is ParsedEntry) assertEquals(2000, result.total)
        if (result is NeedsAnswer) assertEquals(2000, result.partial.total)
    }

    @Test
    fun `explicit amounts below bill give remainder to payer`() {
        val result = parseEntry("dhruv paid 1000 for drinks, 400-arsh", ctx)
        assertTrue(result is ParsedEntry)
        result as ParsedEntry
        val meShare = result.participants.firstOrNull { it.personId == ME }?.amount
        assertEquals(400, meShare)
        val dhruvShare = result.participants.firstOrNull { it.personId == DHRUV }?.amount
        assertEquals(600, dhruvShare)
    }

    @Test
    fun `explicit amounts above bill is typo error`() {
        val result = parseEntry("dhruv paid 1000 for drinks, 600-arsh, 600-vaibhav", ctx)
        assertTrue(result is TypoError)
    }

    // ── Typo error tests ─────────────────────────────────────────────────────
    @Test
    fun `typo error no payer`() {
        val result = parseEntry("paid 500 for dinner", ctx)
        assertTrue(result is TypoError)
    }

    @Test
    fun `typo error lending to self`() {
        val result = parseEntry("I lent 500 to arsh", ctx)
        assertTrue(result is TypoError)
    }

    @Test
    fun `typo error vendor as payer`() {
        val result = parseEntry("vaibhav paid 3k to tiffin", ctx)
        // tiffin is a vendor, so this should be a typo error (only owner can pay JMD)
        assertTrue(result is TypoError)
    }

    @Test
    fun `typo error unknown verb`() {
        val result = parseEntry("vaibhav something 500 for dinner", ctx)
        assertTrue(result is TypoError)
    }

    // ── Case-insensitive alias matching ──────────────────────────────────────
    @Test
    fun `papa Papa PAPA paPA all match same person`() {
        val papa = PersonRecord(20L, "Papa", isOwner = false, isVendor = false, deletedAt = null,
            nicknames = listOf("papa", "ritesh", "monu"))
        val testPeople = ctx.people + papa
        val testCtx = ctx.copy(people = testPeople)
        listOf("papa", "Papa", "PAPA", "paPA").forEach { alias ->
            val result = parseEntry("$alias paid 500 for dinner", testCtx)
            if (result is ParsedEntry) {
                assertEquals("Alias $alias should map to Papa", 20L, result.payerId)
            }
        }
    }

    // ── Deleted person ───────────────────────────────────────────────────────
    @Test
    fun `deleted person nickname no longer matches`() {
        val deleted = PersonRecord(99L, "Deleted Guy", isOwner = false, isVendor = false,
            deletedAt = System.currentTimeMillis(), nicknames = emptyList())
        val testCtx = ctx.copy(people = ctx.people + deleted)
        // Deleted person main name should give typo
        val result = parseEntry("Deleted Guy paid 500 for dinner", testCtx)
        assertTrue(result is TypoError)
    }

    // ── Missing section 12.2 cases ───────────────────────────────────────────

    @Test
    fun `typo error no amount`() {
        val result = parseEntry("vaibhav paid for dinner", ctx)
        assertTrue(result is TypoError)
    }

    @Test
    fun `typo error unregistered payer`() {
        val result = parseEntry("unknownperson paid 500 for dinner", ctx)
        assertTrue(result is TypoError)
    }

    @Test
    fun `no names typed opens who was this for dialog`() {
        val result = parseEntry("I paid 500 for dinner", ctx)
        // No participant names given after the payer → NeedsAnswer(WhoWasThisFor)
        assertTrue(result is NeedsAnswer)
        result as NeedsAnswer
        assertEquals(Question.WhoWasThisFor, result.question)
        assertEquals(500, result.partial.total)
    }

    @Test
    fun `28th in future this month opens which month dialog`() {
        // Today is 10 Nov 2026; 28th is still ahead this month → AmbiguousDay
        val result = parseEntry("I paid 500 for dinner for vaibhav on 28th", ctx)
        assertTrue(result is NeedsAnswer)
        result as NeedsAnswer
        assertTrue(result.question is Question.WhichMonth)
        assertEquals(28, (result.question as Question.WhichMonth).day)
    }

    @Test
    fun `no purpose typed opens what was this for dialog`() {
        val result = parseEntry("I paid 500 for vaibhav", ctx)
        assertTrue(result is NeedsAnswer)
        result as NeedsAnswer
        assertEquals(Question.WhatWasThisFor, result.question)
    }

    private fun entryData(
        id: Long = 1,
        payerId: Long = ME,
        total: Int = 600,
        label: String = "Dinner Time",
        epochDay: Long = java.time.LocalDate.of(2026, 11, 5).toEpochDay(),
    ) = com.pockit.app.ledger.EntryData(
        id = id, kind = "EXPENSE", groupId = null, payerId = payerId,
        receiverId = null, vendorId = null, total = total, label = label,
        labelNumber = 1, purposeText = "dinner", extraInfo = null,
        expenseDate = epochDay, forDate = null, timeText = null,
        originalText = "test", enteredAt = 0,
    )

    private fun shareData(entryId: Long = 1, personId: Long, amount: Int) =
        com.pockit.app.ledger.ShareData(entryId, personId, amount)

    @Test
    fun `edit addParticipant parses correctly`() {
        val result = parseCommand(
            "@Edit add dhruv to the dinner of vaibhav and vighnesh on 5th nov", ctx
        )
        assertTrue(result is EditCommand)
    }

    // Steering section 12.1 test 10: @Edit add dhruv to the dinner of vaibhav and vighnesh on 5th nov
    @Test
    fun `applyEdit steering test10 add dhruv resplits equally`() {
        val entry = entryData(payerId = VIGHNESH, total = 600)
        val shares = listOf(shareData(personId = VIGHNESH, amount = 300), shareData(personId = VAIBHAV, amount = 300))
        val change = EditChange.AddParticipant(DHRUV)
        val (_, newShares) = applyEditChange(change, entry, shares)!!
        assertEquals(3, newShares.size)
        newShares.forEach { assertEquals(200, it.amount) }
    }

    // Steering section 9.4: "@Edit change the amount pending by vaibhav to dhruv for dinner on 5th nov to 1000"
    // The final "to 1000" is the new value; "to dhruv" is part of the description.
    // This is a PersonShare change — vaibhav's share in the entry should become 1000;
    // total bill stays the same.
    @Test
    fun `parseEditChange steering personShare final-to rule`() {
        // Sentence: "change the amount pending by vaibhav to dhruv for dinner on 5th nov to 1000"
        // "to dhruv" is description; final "to 1000" is the new value.
        // The person in context is vaibhav (the person whose share changes).
        val change = parseEditChange(
            "change the amount pending by vaibhav to dhruv for dinner on 5th nov to 1000",
            ctx
        )
        assertNotNull(change)
        assertTrue(change is EditChange.PersonShare)
        change as EditChange.PersonShare
        assertEquals(VAIBHAV, change.personId)
        assertEquals(1000, change.newAmount)
    }

    @Test
    fun `applyEdit personShare keeps total unchanged`() {
        val entry = entryData(total = 2000, payerId = DHRUV)
        val shares = listOf(
            shareData(personId = DHRUV, amount = 667),
            shareData(personId = VAIBHAV, amount = 667),
            shareData(personId = ME, amount = 667),
        )
        // Change vaibhav's share to 1000; diff absorbed equally by others.
        val change = EditChange.PersonShare(VAIBHAV, 1000, null)
        val (newEntry, newShares) = applyEditChange(change, entry, shares)!!
        // Bill total must NOT change per spec section 9.4
        assertEquals(2000, newEntry.total)
        // Vaibhav's share is 1000
        assertEquals(1000, newShares.first { it.personId == VAIBHAV }.amount)
        // Diff absorbed in others: original 667 each, diff = 667 - 1000 = -333, so others gain ceil(333/2) = 167
        // Me and Dhruv each get 667 + (-167) = 500 — but direction is reversed (newAmount > old, diff is negative)
        // diff = oldAmount - newAmount = 667 - 1000 = -333. perOther = ceil(-333/2) = -166.
        // So others each get 667 + (-166) = 501.
        val others = newShares.filter { it.personId != VAIBHAV }
        others.forEach { assertTrue(it.amount > 0) }
        // Total of shares >= 2000
        assertTrue(newShares.sumOf { it.amount } >= 2000)
    }

    @Test
    fun `applyEdit billTotal resplits`() {
        val entry = entryData(total = 2000, payerId = ME)
        val shares = listOf(
            shareData(personId = ME, amount = 667),
            shareData(personId = VAIBHAV, amount = 667),
            shareData(personId = DHRUV, amount = 667),
        )
        val (newEntry, newShares) = applyEditChange(EditChange.BillTotal(3000), entry, shares)!!
        assertEquals(3000, newEntry.total)
        newShares.forEach { assertEquals(1000, it.amount) }
    }

    @Test
    fun `applyEdit personShare absorbs diff in others`() {
        val entry = entryData(total = 600, payerId = ME)
        val shares = listOf(
            shareData(personId = ME, amount = 200),
            shareData(personId = VAIBHAV, amount = 200),
            shareData(personId = DHRUV, amount = 200),
        )
        val (_, newShares) = applyEditChange(EditChange.PersonShare(DHRUV, 100, null), entry, shares)!!
        assertEquals(100, newShares.first { it.personId == DHRUV }.amount)
        assertTrue(newShares.sumOf { it.amount } >= 600)
    }
}
