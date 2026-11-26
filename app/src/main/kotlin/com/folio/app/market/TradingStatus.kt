package com.folio.app.market

enum class TradingStatus(val description: String) {
    ACTIVE("Trading now"),
    CLOSED("Not trading now"),
    UNKNOWN("Trading status unknown"),
}

private const val MAX_SESSION = 24 * 60 * 60_000L
private const val CLOSED_CONFIDENCE = 6 * 60 * 60_000L

/**
 * Uses only the provider's reported regular session and the saved quote; this is not an exchange calendar.
 * Active requires a live price inside the current session, so holidays and not-yet-refreshed opens stay inactive.
 * Closed is claimed only from a recent fetch made outside that session; anything else is unknown.
 */
fun tradingStatus(quote: Quote?, now: Long): TradingStatus {
    val q = quote ?: return TradingStatus.UNKNOWN
    val start = q.regularStart ?: return TradingStatus.UNKNOWN
    val end = q.regularEnd ?: return TradingStatus.UNKNOWN
    if (end <= start || end - start > MAX_SESSION) return TradingStatus.UNKNOWN
    if (now in start until end) {
        val priced = q.quoteTime
        return if (!q.previousCloseFallback && priced != null && priced in start..end) TradingStatus.ACTIVE
            else TradingStatus.UNKNOWN
    }
    val fetchedOutsideSession = q.fetchedAt !in start until end
    return if (fetchedOutsideSession && now >= q.fetchedAt && now - q.fetchedAt <= CLOSED_CONFIDENCE) TradingStatus.CLOSED
        else TradingStatus.UNKNOWN
}

/** Earliest moment an active indicator must turn inactive; becoming active always needs a new fetch. */
fun nextTradingStatusChange(quotes: List<Quote>, now: Long): Long? =
    quotes.filter { tradingStatus(it, now) == TradingStatus.ACTIVE }.mapNotNull { it.regularEnd }.minOrNull()
