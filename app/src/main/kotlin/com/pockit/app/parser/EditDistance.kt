package com.pockit.app.parser

// Standard Levenshtein with transposition counted as one edit (Damerau).
fun editDistance(a: String, b: String): Int {
    val la = a.length; val lb = b.length
    if (la == 0) return lb
    if (lb == 0) return la
    val dp = Array(la + 1) { IntArray(lb + 1) { 0 } }
    for (i in 0..la) dp[i][0] = i
    for (j in 0..lb) dp[0][j] = j
    for (i in 1..la) {
        for (j in 1..lb) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                dp[i][j] = minOf(dp[i][j], dp[i - 2][j - 2] + cost)
            }
        }
    }
    return dp[la][lb]
}
