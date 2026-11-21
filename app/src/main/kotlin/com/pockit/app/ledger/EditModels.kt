package com.pockit.app.ledger

// Plain data models used at the ledger boundary for entry editing.
// Room entities are never referenced here — data/ maps to and from these.

data class EntryData(
    val id: Long,
    val kind: String,
    val groupId: Long?,
    val payerId: Long,
    val receiverId: Long?,
    val vendorId: Long?,
    val total: Int,
    val label: String,
    val labelNumber: Int,
    val purposeText: String?,
    val extraInfo: String?,
    val expenseDate: Long,
    val forDate: Long?,
    val timeText: String?,
    val originalText: String,
    val enteredAt: Long,
)

data class ShareData(val entryId: Long, val personId: Long, val amount: Int)
