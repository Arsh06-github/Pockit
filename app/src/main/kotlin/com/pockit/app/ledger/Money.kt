package com.pockit.app.ledger

import kotlin.math.ceil

fun parseAmount(raw: String): Int? {
    val s = raw.trim().removePrefix("₹").removePrefix("rs").trim().replace(",", "")
    return when {
        s.endsWith("k", ignoreCase = true) -> {
            val n = s.dropLast(1).toDoubleOrNull() ?: return null
            ceil(n * 1000).toInt()
        }
        else -> {
            val d = s.toDoubleOrNull() ?: return null
            ceil(d).toInt()
        }
    }
}

fun splitEqual(total: Int, count: Int): List<Int> {
    if (count <= 0) return emptyList()
    val share = ceil(total.toDouble() / count).toInt()
    return List(count) { share }
}
