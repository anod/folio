package com.folio.app.market

import org.junit.Assert.*
import org.junit.Test

class MarketTest {
    private fun response(meta: String, closes: String = "[100,null,103]", timestamps: String = "[1000,1001,1002]") =
        """{"chart":{"error":null,"result":[{"meta":{$meta},"timestamp":$timestamps,"indicators":{"quote":[{"close":$closes}]}}]}}"""

    @Test fun parsesQuoteAndKeepsQuoteAndFetchTimesDistinct() {
        val q = parseQuote(response(""""symbol":"MSFT","shortName":"Microsoft","currency":"USD","regularMarketPrice":105,"chartPreviousClose":100,"regularMarketTime":1700000000"""), "MSFT", 1800000000000)
        assertEquals(5.0, q.change!!, 0.001)
        assertEquals(5.0, q.percent!!, 0.001)
        assertEquals(1700000000000, q.quoteTime)
        assertEquals(1800000000000, q.fetchedAt)
        assertEquals(listOf(100.0, 103.0), q.points)
    }

    @Test fun fallbackIsNotPresentedAsNewTradeOrChange() {
        val q = parseQuote(response(""""symbol":"MSFT","chartPreviousClose":100,"regularMarketTime":1700000000"""), "MSFT", 20)
        assertTrue(q.previousCloseFallback)
        assertNull(q.quoteTime)
        assertNull(q.change)
        assertNull(q.percent)
    }

    @Test fun missingPriceAndInvalidSymbolFailExplicitly() {
        assertThrows(MarketException::class.java) { parseQuote(response(""""symbol":"BAD""""), "BAD", 1) }
        assertThrows(MarketException::class.java) { normalizeSymbol("../MSFT") }
        assertEquals("USDILS=X", normalizeSymbol(" usdils=x "))
    }

    @Test fun failurePreservesLastGoodSnapshotAndSuccessTime() {
        val q = parseQuote(response(""""symbol":"MSFT","regularMarketPrice":105,"chartPreviousClose":100"""), "MSFT", 123)
        val failed = recordFailure(Entry("MSFT", q, 123, "Success"), 456, "Rate limited")
        assertEquals(q, failed.quote)
        assertEquals(123, failed.quote!!.fetchedAt)
        assertEquals(456L, failed.attemptedAt)
        assertEquals("Rate limited", failed.outcome)
    }
}
