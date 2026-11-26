package com.folio.app.market

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import com.folio.app.widget.WidgetConfig

fun recordFailure(entry: Entry, attemptedAt: Long, outcome: String): Entry =
    entry.copy(attemptedAt = attemptedAt, outcome = outcome)

data class RefreshSummary(val successes: Int, val attempted: Int, val total: Int, val retryPending: Boolean, val providerRetryAfter: Long? = null)

class Store internal constructor(context: Context, private val provider: MarketDataProvider, preferenceName: String = "folio") {
    private val prefs = context.applicationContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var lastRefreshElapsed = 0L
    private var lastSummary = RefreshSummary(0, 0, 0, false)
    private val mutableEntries = MutableStateFlow(readEntries())
    val entries: StateFlow<List<Entry>> = mutableEntries
    private val mutableClock = MutableStateFlow(System.currentTimeMillis())
    /** Render time for widgets; Glance only recomposes on state changes, so time-based status needs a tick. */
    val clock: StateFlow<Long> = mutableClock
    fun tick() { mutableClock.value = System.currentTimeMillis() }

    private fun readEntries(): List<Entry> = decodeEntries(prefs.getString("entries", "[]") ?: "[]")

    private fun save(entries: List<Entry>) {
        check(prefs.edit().putString("entries", encodeEntries(entries)).commit()) { "Could not save watchlist" }
        mutableEntries.value = entries
    }

    private fun globalRetryAfter() = prefs.getLong("provider.retryAfter", 0L)
    private fun rememberRateLimit(e: MarketException, now: Long) {
        if (e.rateLimited) {
            val until = maxOf(e.retryAfter ?: now + 15 * 60_000, now + 60_000)
            check(prefs.edit().putLong("provider.retryAfter", until).commit()) { "Could not save provider rate limit" }
        }
    }
    suspend fun lookup(symbol: String): Quote = mutex.withLock {
        val now = System.currentTimeMillis()
        val until = globalRetryAfter()
        if (providerBlockedUntil(until, now) != null) throw MarketException("Provider rate limited; retry after ${dateTime(until)}", true, until, true)
        try {
            provider.fetch(normalizeSymbol(symbol))
        } catch (e: MarketException) {
            rememberRateLimit(e, System.currentTimeMillis())
            throw e
        }
    }

    suspend fun chartHistory(symbol: String, range: ChartRange): ChartSeries = withContext(NonCancellable) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val until = globalRetryAfter()
            if (providerBlockedUntil(until, now) != null) {
                throw MarketException("Provider rate limited; retry after ${dateTime(until)}", true, until, true)
            }
            try {
                provider.fetchHistory(normalizeSymbol(symbol), range)
            } catch (e: MarketException) {
                rememberRateLimit(e, System.currentTimeMillis())
                throw e
            }
        }
    }

    suspend fun add(quote: Quote) = mutex.withLock {
        val symbol = normalizeSymbol(quote.symbol)
        if (mutableEntries.value.none { it.symbol == symbol }) {
            save(mutableEntries.value + Entry(symbol, quote, quote.fetchedAt, "Success"))
            lastRefreshElapsed = 0L
        }
    }

    suspend fun remove(symbol: String) = mutex.withLock {
        save(mutableEntries.value.filterNot { it.symbol == symbol })
        lastRefreshElapsed = 0L
    }

    suspend fun move(symbol: String, delta: Int) = mutex.withLock {
        val list = mutableEntries.value.toMutableList()
        val i = list.indexOfFirst { it.symbol == symbol }
        val target = i + delta
        if (i >= 0 && target in list.indices) {
            java.util.Collections.swap(list, i, target)
            save(list)
        }
    }

    suspend fun refresh(manual: Boolean = true): RefreshSummary = mutex.withLock {
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastRefreshElapsed != 0L && lastSummary.attempted > 0 && now - lastRefreshElapsed < 30_000) {
            return@withLock lastSummary.copy(providerRetryAfter = providerBlockedUntil(globalRetryAfter(), System.currentTimeMillis()))
        }
        val current = mutableEntries.value
        var successes = 0
        var attemptedCount = 0
        current.forEach { entry ->
            val attempted = System.currentTimeMillis()
            if (providerBlockedUntil(globalRetryAfter(), attempted) != null || !eligibleForRefresh(entry, attempted, manual)) return@forEach
            attemptedCount++
            val (updated, error) = refreshEntry(provider, entry, attempted)
            if (error == null) successes++ else rememberRateLimit(error, attempted)
            save(mutableEntries.value.map { if (it.symbol == entry.symbol) updated else it })
        }
        lastRefreshElapsed = android.os.SystemClock.elapsedRealtime()
        val retryPending = System.currentTimeMillis() >= globalRetryAfter() &&
            mutableEntries.value.any { it.retryAfter != null && it.failures in 1..3 }
        val rateLimit = providerBlockedUntil(globalRetryAfter(), System.currentTimeMillis())
        RefreshSummary(successes, attemptedCount, current.size, retryPending, rateLimit).also { lastSummary = it }
    }

    fun getString(key: String, default: String) = prefs.getString(key, default) ?: default
    fun putString(key: String, value: String) {
        check(prefs.edit().putString(key, value).commit()) { "Could not save preference" }
    }

    fun widgetConfig(id: Int): WidgetConfig {
        val key = "widget.$id.config"
        val saved = prefs.getString(key, null)
        if (saved != null) return WidgetConfig.decode(saved)
        val symbol = prefs.getString("widget.$id.symbol", null)
        return WidgetConfig(
            if (symbol == null) entries.value.map { it.symbol } else listOfNotNull(symbol.takeIf { it.isNotEmpty() }),
            getString("widget.$id.theme", "System"),
            getString("widget.$id.opacity", "100").toIntOrNull()?.coerceIn(35, 100) ?: 100,
        )
    }

    fun saveWidgetConfig(id: Int, config: WidgetConfig) = putString("widget.$id.config", config.encode())
    fun observeWidgetConfig(id: Int) = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "widget.$id.config") trySend(widgetConfig(id))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(widgetConfig(id))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    fun hasWidgetConfig(id: Int): Boolean = prefs.contains("widget.$id.config") || prefs.contains("widget.$id.symbol")
    fun deleteWidgetConfig(id: Int) {
        check(prefs.edit().remove("widget.$id.config").remove("widget.$id.symbol")
            .remove("widget.$id.theme").remove("widget.$id.opacity").commit()) { "Could not delete widget settings" }
    }

    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context, YahooProvider()).also { instance = it }
        }
    }
}
