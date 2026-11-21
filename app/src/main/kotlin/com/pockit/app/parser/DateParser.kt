package com.pockit.app.parser

import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter

private val MONTH_NAMES = mapOf(
    "jan" to Month.JANUARY, "january" to Month.JANUARY,
    "feb" to Month.FEBRUARY, "february" to Month.FEBRUARY,
    "mar" to Month.MARCH, "march" to Month.MARCH,
    "apr" to Month.APRIL, "april" to Month.APRIL,
    "may" to Month.MAY,
    "jun" to Month.JUNE, "june" to Month.JUNE,
    "jul" to Month.JULY, "july" to Month.JULY,
    "aug" to Month.AUGUST, "august" to Month.AUGUST,
    "sep" to Month.SEPTEMBER, "sept" to Month.SEPTEMBER, "september" to Month.SEPTEMBER,
    "oct" to Month.OCTOBER, "october" to Month.OCTOBER,
    "nov" to Month.NOVEMBER, "november" to Month.NOVEMBER,
    "dec" to Month.DECEMBER, "december" to Month.DECEMBER,
)

sealed class DateResult {
    data class Exact(val date: LocalDate) : DateResult()
    data class AmbiguousDay(val day: Int) : DateResult() // needs dialog
}

// Tries to parse a date phrase from a list of tokens starting at `start`.
// Returns (DateResult, tokensConsumed) or null if no date found.
fun tryParseDate(tokens: List<String>, start: Int, today: LocalDate): Pair<DateResult, Int>? {
    val t = tokens.getOrNull(start)?.lowercase() ?: return null

    // today / yesterday / day before yesterday / tomorrow / day after tomorrow
    when {
        t == "today" -> return DateResult.Exact(today) to 1
        t == "yesterday" -> return DateResult.Exact(today.minusDays(1)) to 1
        t == "tomorrow" -> return DateResult.Exact(today.plusDays(1)) to 1
        t == "day" && tokens.getOrNull(start + 1)?.lowercase() == "before" &&
                tokens.getOrNull(start + 2)?.lowercase() == "yesterday" ->
            return DateResult.Exact(today.minusDays(2)) to 3
        t == "day" && tokens.getOrNull(start + 1)?.lowercase() == "after" &&
                tokens.getOrNull(start + 2)?.lowercase() == "tomorrow" ->
            return DateResult.Exact(today.plusDays(2)) to 3
    }

    // "on" prefix — consume it and recurse
    if (t == "on") {
        val inner = tryParseDate(tokens, start + 1, today)
        return inner?.let { (r, n) -> r to (n + 1) }
    }

    // DD/MM/YYYY or DD/MM
    val slashParts = t.split("/")
    if (slashParts.size == 3) {
        val d = slashParts[0].toIntOrNull() ?: return null
        val m = slashParts[1].toIntOrNull() ?: return null
        val y = slashParts[2].toIntOrNull() ?: return null
        return runCatching { DateResult.Exact(LocalDate.of(y, m, d)) to 1 }.getOrNull()
    }
    if (slashParts.size == 2) {
        val d = slashParts[0].toIntOrNull() ?: return null
        val m = slashParts[1].toIntOrNull() ?: return null
        return runCatching { DateResult.Exact(LocalDate.of(today.year, m, d)) to 1 }.getOrNull()
    }

    // 5th, 25th, 5th nov, 5th november
    val ordinal = t.removeSuffix("st").removeSuffix("nd").removeSuffix("rd").removeSuffix("th")
    val day = ordinal.toIntOrNull()
    if (day != null && day in 1..31) {
        val next = tokens.getOrNull(start + 1)?.lowercase()
        val month = next?.let { MONTH_NAMES[it] }
        return if (month != null) {
            runCatching {
                DateResult.Exact(LocalDate.of(today.year, month, day)) to 2
            }.getOrNull()
        } else {
            // Day only — if the day is in the future this month, ambiguous
            val thisMonth = runCatching { LocalDate.of(today.year, today.month, day) }.getOrNull()
            if (thisMonth != null) {
                if (thisMonth.isAfter(today)) DateResult.AmbiguousDay(day) to 1
                else DateResult.Exact(thisMonth) to 1
            } else null
        }
    }

    return null
}

// Time: "at 10 pm", "at 10:30 pm", "10:30 pm", "10 pm"
// Returns (timeText, tokensConsumed) or null
fun tryParseTime(tokens: List<String>, start: Int): Pair<String, Int>? {
    var idx = start
    val t = tokens.getOrNull(idx)?.lowercase() ?: return null
    if (t == "at") idx++
    val timePart = tokens.getOrNull(idx) ?: return null
    val amPm = tokens.getOrNull(idx + 1)?.lowercase()

    if (amPm == "am" || amPm == "pm") {
        // Could be "10 pm" or "10:30 pm"
        val colon = timePart.contains(":")
        return if (colon) {
            val parts = timePart.split(":")
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val formatted = "%d:%02d %s".format(h, m, amPm.uppercase())
            formatted to (idx - start + 2)
        } else {
            val h = timePart.toIntOrNull() ?: return null
            val formatted = "%d:00 %s".format(h, amPm.uppercase())
            formatted to (idx - start + 2)
        }
    }
    // "10:30pm" single token
    val combined = timePart.lowercase()
    val ampmInToken = when {
        combined.endsWith("am") -> "AM"
        combined.endsWith("pm") -> "PM"
        else -> return null
    }
    val timeOnly = combined.dropLast(2)
    return if (timeOnly.contains(":")) {
        val parts = timeOnly.split(":")
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        "%d:%02d %s".format(h, m, ampmInToken) to (idx - start + 1)
    } else {
        val h = timeOnly.toIntOrNull() ?: return null
        "%d:00 %s".format(h, ampmInToken) to (idx - start + 1)
    }
}
