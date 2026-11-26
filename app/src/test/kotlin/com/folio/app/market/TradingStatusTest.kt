package com.folio.app.market

import org.junit.Assert.*
import org.junit.Test

class TradingStatusTest {
    private val start = 10_000_000L
    private val end = 30_000_000L
    private val hour = 3_600_000L
    private fun quote(quoteTime: Long?, fetchedAt: Long, regularEnd: Long? = end) = Quote("ABC", "Example", "USD", null,
        "America/New_York", 105.0, 100.0, quoteTime, fetchedAt, listOf(100.0, 105.0),
        regularStart = start, regularEnd = regularEnd)

    @Test fun activeOnlyWithALivePriceInsideTheReportedSession() {
        val live = quote(15_000_000L, 15_000_000L)
        assertEquals(TradingStatus.ACTIVE, tradingStatus(live, 20_000_000L))
        // Holiday or not yet refreshed today: the saved price predates the session, so no claim is made.
        assertEquals(TradingStatus.UNKNOWN, tradingStatus(quote(1_000L, 5_000_000L), 20_000_000L))
        assertEquals(TradingStatus.UNKNOWN, tradingStatus(live.copy(previousCloseFallback = true), 20_000_000L))
        assertEquals(TradingStatus.UNKNOWN, tradingStatus(live.copy(quoteTime = null), 20_000_000L))
    }

    @Test fun closedOnlyFromARecentFetchOutsideTheSession() {
        assertEquals(TradingStatus.CLOSED, tradingStatus(quote(29_000_000L, 31_000_000L), 32_000_000L))
        assertEquals(TradingStatus.CLOSED, tradingStatus(quote(1_000L, 5_000_000L), 6_000_000L))
        assertEquals(TradingStatus.UNKNOWN, tradingStatus(quote(29_000_000L, 31_000_000L), 31_000_000L + 6 * hour + 1))
        // Fetched during trading, now past the reported end: inactive but not confirmed closed.
        assertEquals(TradingStatus.UNKNOWN, tradingStatus(quote(25_000_000L, 25_000_000L), 31_000_000L))
    }

    @Test fun missingOrImplausibleBoundsAreUnknown() {
        assertEquals(TradingStatus.UNKNOWN, tradingStatus(null, 20_000_000L))
        assertEquals(TradingStatus.UNKNOWN, tradingStatus(quote(15_000_000L, 15_000_000L).copy(regularStart = null), 20_000_000L))
        assertEquals(TradingStatus.UNKNOWN, tradingStatus(quote(15_000_000L, 15_000_000L, regularEnd = start), 20_000_000L))
        assertEquals(TradingStatus.UNKNOWN, tradingStatus(quote(15_000_000L, 15_000_000L, regularEnd = start + 25 * hour), 20_000_000L))
    }

    @Test fun nextChangeIsTheEarliestEndOfAnActiveSession() {
        val laterActive = quote(15_000_000L, 15_000_000L)
        val earlierActive = quote(15_000_000L, 15_000_000L, regularEnd = 25_000_000L)
        val unknown = quote(1_000L, 5_000_000L, regularEnd = 21_000_000L)
        assertEquals(25_000_000L, nextTradingStatusChange(listOf(laterActive, unknown, earlierActive), 20_000_000L))
        assertNull(nextTradingStatusChange(listOf(unknown), 20_000_000L))
        assertNull(nextTradingStatusChange(listOf(laterActive), end))
    }
}
