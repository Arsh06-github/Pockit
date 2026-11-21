package com.pockit.app.parser

// Builds a lookup from lowercased alias -> PersonRecord.
// Longest match first is enforced at call site by trying longer spans first.
fun buildAliasIndex(people: List<PersonRecord>): Map<String, PersonRecord> {
    val map = mutableMapOf<String, PersonRecord>()
    for (person in people) {
        if (person.deletedAt != null) continue // deleted: nicknames gone
        for (nick in person.nicknames) {
            map[nick.lowercase()] = person
        }
    }
    // Exact main name match (case-sensitive as entered, but we store lower for lookup too)
    // Main names match only with exact case per spec; we handle that separately.
    return map
}

// Returns the owner aliases used for payer detection.
val OWNER_ALIASES = setOf("me", "i", "myself", "arsh", "boss", "khushu")
