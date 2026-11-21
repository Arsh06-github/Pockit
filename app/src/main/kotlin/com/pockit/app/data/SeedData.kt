package com.pockit.app.data

// Seed data inserted once on first launch.
// People: main name, isVendor, nicknames (lower-cased)
data class SeedPerson(val mainName: String, val isVendor: Boolean, val nicknames: List<String>)

val SEED_PEOPLE = listOf(
    SeedPerson("Me", false, listOf("arsh", "boss", "myself", "me", "khushu", "i")),
    SeedPerson("Vaibhav", false, listOf("vaibhav", "vaibhsa")),
    SeedPerson("Vighnesh Bhati", false, listOf("vighnesh", "vanshu", "demon", "vanshi", "vansh")),
    SeedPerson("Dhruv Parihar", false, listOf("dhruv", "dhruvi")),
    SeedPerson("Hunny Bhati", false, listOf("hunny", "hashant")),
    SeedPerson("Puneet Soni", false, listOf("puneet", "punit", "punsa")),
    SeedPerson("Priyanjali Bhati", false, listOf("bhati", "piya", "pina colada")),
    SeedPerson("Pratya Kalla", false, listOf("pratya")),
    SeedPerson("Hiya Jain", false, listOf("hiya", "chiya")),
    SeedPerson("Kaavi Sharma", false, listOf("kaavi")),
    SeedPerson("Papa", false, listOf("papa", "ritesh", "monu")),
    SeedPerson("Matashree", false, listOf("mumma", "bhawana mumma", "bhawna mumma", "matashree", "bhawana", "bhawna")),
    SeedPerson("Dadi", false, listOf("gd", "dadi", "kamla", "kamala", "khushu mumma", "kamla ji")),
    SeedPerson("Dadaji", false, listOf("dadaji", "dada", "papaji", "johari saab", "johari sahab")),
    SeedPerson("Jeet Shah", false, listOf("jeet", "jeet bhaiya", "jeet bhai", "jeet shah")),
    SeedPerson("JMD Tiffin", true, listOf("jmdt", "jmdts", "tiffin", "tiffin service")),
    SeedPerson("Harsh Kumar", false, listOf("harsh", "harsh kumar", "harsh bhaiya")),
)

val HOME_MESSAGES = listOf(
    "Who paid today?",
    "What did we spend today?",
    "Any dues to settle?",
    "Log a new expense",
    "Who owes you right now?",
    "Split a bill in seconds",
    "Dinner, auto or tickets?",
    "Who needs a reminder?",
    "What's pending in your groups?",
    "Keep the count, skip the awkward asks",
)
