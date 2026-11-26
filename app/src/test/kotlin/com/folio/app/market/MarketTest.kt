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
        assertEquals(listOf(1000000L, 1002000L), q.pointTimes)
        assertEquals(q, decodeEntries(encodeEntries(listOf(Entry("MSFT", q)))).single().quote)
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

    @Test fun chartScalePreservesDirectionAndCentersFlatValues() {
        assertEquals(listOf(0f, 1f, 0.5f), chartFractions(listOf(100.0, 110.0, 105.0)))
        assertEquals(listOf(0.5f, 0.5f), chartFractions(listOf(100.0, 100.0)))
        assertTrue(chartFractions(emptyList()).isEmpty())
    }

    @Test fun chartSamplesRejectInvalidOrOutOfOrderTimesAndStayPairedWhenBounded() {
        val q = parseQuote(response(""""regularMarketPrice":105""",
            "[100,101,102,103,104,105]", "[1000,0,999,1000,1004,1005]"), "MSFT", 1)
        assertEquals(listOf(100.0, 104.0, 105.0), q.points)
        assertEquals(listOf(1000000L, 1004000L, 1005000L), q.pointTimes)
        val long = parseQuote(response(""""regularMarketPrice":105""",
            (1..205).joinToString(",", "[", "]"), (1000..1204).joinToString(",", "[", "]")), "MSFT", 1)
        assertEquals(200, long.points.size)
        assertEquals(6.0, long.points.first(), 0.0)
        assertEquals(1005000L, long.pointTimes.first())
    }

    @Test fun axisUsesActualTimeSpacingExchangeZoneAndDatesForLongerSpans() {
        fun instant(value: String) = java.time.Instant.parse(value).toEpochMilli()
        val times = listOf("2026-11-25T14:30:00Z", "2026-11-25T15:00:00Z", "2026-11-25T21:00:00Z").map(::instant)
        val axis = chartTimeAxis(times, 3, "America/New_York", java.util.Locale.US)!!
        assertEquals(listOf("09:30", "11:40", "13:50", "16:00"), axis.labels)
        assertEquals(1f / 13, axis.fractions[1], 0.0001f)
        assertEquals("America/New_York", axis.timezone)
        val week = chartTimeAxis(listOf(instant("2026-11-20T14:30:00Z"), instant("2026-11-26T14:30:00Z")), 2,
            "America/New_York", java.util.Locale.US)!!
        assertEquals(listOf("Nov 20", "Nov 22", "Nov 24", "Nov 26"), week.labels)
        assertNull(chartTimeAxis(emptyList(), 3, "UTC"))
        assertNull(chartTimeAxis(listOf(1, 1), 2, "UTC"))
        assertEquals("UTC", chartTimeAxis(times, 3, "Unknown exchange")!!.timezone)
        val old = encodeEntries(listOf(Entry("MSFT", Quote("MSFT", "Microsoft", null, null, null, 1.0, null, null, 1, listOf(1.0, 2.0)))))
        val json = org.json.JSONArray(old)
        json.getJSONObject(0).getJSONObject("quote").remove("pointTimes")
        assertTrue(decodeEntries(json.toString()).single().quote!!.pointTimes.isEmpty())
    }
}
