package com.folio.app.market

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RefreshPolicyTest {
    private val quote = Quote("ABC", "Example", "USD", null, "America/New_York",
        105.0, 100.0, 1_000L, 2_000L, listOf(100.0, 105.0),
        regularStart = 10_000_000L, regularEnd = 30_000_000L)

    @Test fun failedRetryPreservesQuoteAndEnforcesProviderDeadline() = runTest {
        var calls = 0
        val provider = object : MarketDataProvider {
            override suspend fun fetch(symbol: String): Quote {
                calls++
                throw MarketException("Rate limited", true, 900_000L, true)
            }
        }
        val old = Entry("ABC", quote, 2_000L, "Success")
        val (failed, error) = refreshEntry(provider, old, 100_000L)
        assertEquals(1, calls)
        assertTrue(error!!.rateLimited)
        assertEquals(quote, failed.quote)
        assertEquals(1_000L, failed.quote!!.quoteTime)
        assertEquals(2_000L, failed.quote!!.fetchedAt)
        assertEquals(100_000L, failed.attemptedAt)
        assertEquals(900_000L, failed.retryAfter)
        assertEquals(failed, decodeEntries(encodeEntries(listOf(failed))).single())
        assertFalse(eligibleForRefresh(failed, 899_999L, manual = true))
        assertTrue(eligibleForRefresh(failed, 900_000L, manual = true))
    }

    @Test fun transientFailuresBackOffAndSuccessfulRetryResetsState() = runTest {
        var calls = 0
        val provider = object : MarketDataProvider {
            override suspend fun fetch(symbol: String): Quote {
                calls++
                if (calls <= 2) throw MarketException("Network unavailable", true)
                return quote.copy(fetchedAt = 500_000L)
            }
        }
        val (first, _) = refreshEntry(provider, Entry("ABC", quote), 100_000L)
        val (second, _) = refreshEntry(provider, first, first.retryAfter!!)
        assertEquals(160_000L, first.retryAfter)
        assertEquals(280_000L, second.retryAfter)
        assertEquals(quote, second.quote)
        val (success, _) = refreshEntry(provider, second, second.retryAfter!!)
        assertEquals(0, success.failures)
        assertNull(success.retryAfter)
        assertEquals(500_000L, success.quote!!.fetchedAt)
        assertEquals(1_000L, success.quote!!.quoteTime)
        assertEquals(3_700_000L, retryDeadline(100_000L, 20, null))
    }

    @Test fun marketMetadataOnlySlowsKnownRecentClosedSessions() {
        val entry = Entry("ABC", quote, 31_000_000L, "Success")
        assertTrue(eligibleForRefresh(entry, 20_000_000L, false))
        assertFalse(eligibleForRefresh(entry, 31_500_000L, false))
        assertTrue(eligibleForRefresh(entry, 34_600_000L, false))
        assertTrue(eligibleForRefresh(entry, 31_500_000L, true))
        assertTrue(eligibleForRefresh(entry.copy(quote = quote.copy(regularStart = null)), 31_500_000L, false))
        assertTrue(eligibleForRefresh(entry, 120_000_000L, false))
    }

    @Test fun parsesRetryAfterWithoutIgnoringServerMinimum() {
        assertEquals(130_000L, retryAfterDeadline("30", 100_000L))
        assertEquals(1_445_412_480_000L, retryAfterDeadline("Wed, 21 Oct 2015 07:28:00 GMT", 0))
        assertNull(retryAfterDeadline("-1", 0))
        assertEquals(900_000L, retryDeadline(100_000L, 1, 900_000L))
        assertEquals(900_000L, providerBlockedUntil(900_000L, 899_999L))
        assertNull(providerBlockedUntil(900_000L, 900_000L))
    }
}
