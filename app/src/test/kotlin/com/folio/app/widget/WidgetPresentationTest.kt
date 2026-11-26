package com.folio.app.widget

import com.folio.app.market.Entry
import com.folio.app.market.Quote
import com.folio.app.market.TradingStatus
import org.junit.Assert.*
import org.junit.Test

class WidgetPresentationTest {
    private fun entry(symbol: String) = Entry(symbol, Quote(symbol, symbol, "USD", null, null,
        12.0, 10.0, 60_000, 120_000, listOf(10.0, 12.0)), 180_000, "Network unavailable")

    @Test fun configurationsAreIndependentOrderedAndRoundTrip() {
        val first = WidgetConfig(listOf("B", "A"), "Sage", 65)
        val second = WidgetConfig(listOf("C"), "Graphite", 100)
        assertEquals(first, WidgetConfig.decode(first.encode()))
        assertEquals(second, WidgetConfig.decode(second.encode()))
        assertEquals("System", WidgetConfig.decode(WidgetConfig(listOf("A")).encode()).theme)
        assertEquals("Graphite", WidgetConfig.decode("""{"symbols":["A"],"theme":"Graphite"}""").theme)
        assertEquals(listOf("A", "B"), moveSymbol(first.symbols, "A", -1))
        assertEquals(listOf("B", "A"), first.symbols)
        assertEquals(listOf("B", "A"), moveSymbol(first.symbols, "B", -1))
    }

    @Test fun previewAndWidgetUseSameSelectionAndCachedStatus() {
        val entries = listOf(entry("A"), entry("B"), entry("C"))
        val config = WidgetConfig(listOf("B", "A"))
        val tall = presentWidget(config, entries, WidgetLayout.WATCHLIST)
        val compact = presentWidget(config, entries, WidgetLayout.COMPACT)
        assertEquals(listOf("B", "A"), tall.rows.map { it.symbol })
        assertEquals(tall.rows.first(), compact.rows.single())
        assertEquals("Cached · Network unavailable", compact.rows.single().status)
        assertFalse(compact.rows.single().quoteTime == compact.rows.single().fetchedAt)
        assertEquals("Quote: ${widgetDateTime(60_000)}", compact.rows.single().quoteTime)
        assertEquals("Fetched: ${widgetDateTime(120_000)}", compact.rows.single().fetchedAt)
        assertEquals("Attempt: ${widgetDateTime(180_000)}", compact.rows.single().lastAttempt)
        assertTrue(presentWidget(WidgetConfig(emptyList()), entries, WidgetLayout.COMPACT).rows.isEmpty())
    }

    @Test fun previousCloseIsNotPresentedAsACurrentTrade() {
        val saved = entry("A")
        val fallback = saved.copy(quote = saved.quote!!.copy(previousCloseFallback = true))
        val row = presentWidget(WidgetConfig(listOf("A")), listOf(fallback), WidgetLayout.COMPACT).rows.single()
        assertTrue(row.status!!.contains("Previous close, not a current trade"))
        assertTrue(row.status.contains("Network unavailable"))
    }

    @Test fun tradingStatusFollowsTheSuppliedClock() {
        val saved = entry("A")
        val session = saved.copy(quote = saved.quote!!.copy(regularStart = 30_000, regularEnd = 600_000))
        val entries = listOf(session, entry("B"))
        val during = presentWidget(WidgetConfig(listOf("A", "B")), entries, WidgetLayout.WATCHLIST, now = 300_000).rows
        assertEquals(listOf(TradingStatus.ACTIVE, TradingStatus.UNKNOWN), during.map { it.trading })
        val after = presentWidget(WidgetConfig(listOf("A")), entries, WidgetLayout.COMPACT, now = 600_000).rows.single()
        assertEquals(TradingStatus.UNKNOWN, after.trading)
    }

    @Test fun fallingPricesKeepTheirSignInEveryLayout() {
        val saved = entry("A")
        val falling = saved.copy(quote = saved.quote!!.copy(price = 9.0))
        WidgetLayout.entries.forEach { layout ->
            val row = presentWidget(WidgetConfig(listOf("A")), listOf(falling), layout).rows.single()
            assertTrue(row.negative)
            assertTrue(row.change.startsWith("↘ −"))
        }
    }
}
