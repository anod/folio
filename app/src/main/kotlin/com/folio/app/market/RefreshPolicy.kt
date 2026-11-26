package com.folio.app.market

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE

fun providerBlockedUntil(deadline: Long, now: Long): Long? = deadline.takeIf { it > now }

fun retryDeadline(now: Long, failures: Int, providerDeadline: Long?): Long {
    val delay = (MINUTE * (1L shl (failures - 1).coerceIn(0, 6))).coerceAtMost(HOUR)
    return maxOf(now + delay, providerDeadline ?: 0L)
}

fun eligibleForRefresh(entry: Entry, now: Long, manual: Boolean): Boolean {
    if (entry.retryAfter?.let { now < it } == true) return false
    if (manual) return true
    if (entry.failures > 0 && entry.retryAfter == null) return false
    val quote = entry.quote ?: return true
    val start = quote.regularStart ?: return true
    val end = quote.regularEnd ?: return true
    // Old session metadata is not a market calendar; never defer on it indefinitely.
    if (end <= start || end - start > 24 * HOUR || now < start - 24 * HOUR || now > end + 24 * HOUR) return true
    if (now in start..end) return true
    return entry.attemptedAt?.let { now - it >= HOUR || now < it } ?: true
}

suspend fun refreshEntry(provider: MarketDataProvider, entry: Entry, attemptedAt: Long): Pair<Entry, MarketException?> {
    return try {
        val quote = provider.fetch(entry.symbol)
        entry.copy(quote = quote, attemptedAt = attemptedAt, outcome = "Success", retryAfter = null, failures = 0) to null
    } catch (e: MarketException) {
        val failures = (entry.failures + 1).coerceAtMost(30)
        val next = if (e.retryable) retryDeadline(attemptedAt, failures, e.retryAfter) else null
        entry.copy(attemptedAt = attemptedAt, outcome = e.message ?: "Refresh failed", retryAfter = next, failures = failures) to e
    }
}
