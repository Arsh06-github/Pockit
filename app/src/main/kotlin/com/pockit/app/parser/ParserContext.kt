package com.pockit.app.parser

// All data the parser needs at runtime.
// Built once at startup, never rebuilt per-message.
data class PersonRecord(
    val id: Long,
    val mainName: String,       // exact case
    val isOwner: Boolean,
    val isVendor: Boolean,
    val deletedAt: Long?,
    val nicknames: List<String>, // already lower-cased
)

data class GroupRecord(
    val id: Long,
    val name: String,
    val deletedAt: Long?,
    val memberIds: Set<Long>,
)

data class ParserContext(
    val people: List<PersonRecord>,
    val groups: List<GroupRecord>,
    val purposeMap: Map<String, String>,   // word -> label
    val cityMap: Map<String, String>,      // short -> full
    val today: java.time.LocalDate,
)
