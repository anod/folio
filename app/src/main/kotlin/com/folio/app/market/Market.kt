package com.folio.app.market

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class Quote(
    val symbol: String,
    val name: String,
    val currency: String?,
    val exchange: String?,
    val timezone: String?,
    val price: Double,
    val previousClose: Double?,
    val quoteTime: Long?,
    val fetchedAt: Long,
    val points: List<Double>,
    val previousCloseFallback: Boolean = false,
    val regularStart: Long? = null,
    val regularEnd: Long? = null,
) {
    val change: Double? get() = previousClose?.takeUnless { previousCloseFallback }?.let { price - it }
    val percent: Double? get() = previousClose?.takeUnless { previousCloseFallback }?.takeIf { it != 0.0 }?.let { (price - it) / it * 100 }
}

data class Entry(
    val symbol: String,
    val quote: Quote? = null,
    val attemptedAt: Long? = null,
    val outcome: String? = null,
    val retryAfter: Long? = null,
    val failures: Int = 0,
)

interface MarketDataProvider {
    suspend fun fetch(symbol: String): Quote
}

class MarketException(message: String, val retryable: Boolean = false, val retryAfter: Long? = null, val rateLimited: Boolean = false) : Exception(message)

fun normalizeSymbol(input: String): String {
    val symbol = input.trim().uppercase(java.util.Locale.ROOT)
    if (!symbol.matches(Regex("[A-Z0-9][A-Z0-9.\\-^=]{0,24}"))) {
        throw MarketException("Enter a valid exact symbol.")
    }
    return symbol
}

private fun JSONObject.finiteNumber(key: String): Double? {
    if (isNull(key)) return null
    return optDouble(key, Double.NaN).takeIf { it.isFinite() && it > 0 }
}

fun retryAfterDeadline(value: String?, now: Long): Long? {
    if (value.isNullOrBlank()) return null
    val seconds = value.trim().toLongOrNull()
    if (seconds != null) {
        if (seconds < 0) return null
        return if (seconds > (Long.MAX_VALUE - now) / 1000) Long.MAX_VALUE else now + seconds * 1000
    }
    return try {
        java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    } catch (_: java.time.format.DateTimeParseException) { null }
}

fun parseQuote(body: String, requested: String, fetchedAt: Long): Quote {
    try {
        val chart = JSONObject(body).getJSONObject("chart")
        val error = chart.optJSONObject("error")
        if (error != null) throw MarketException(error.optString("description", "Symbol unavailable"))
        val results = chart.optJSONArray("result") ?: throw MarketException("Symbol unavailable")
        val result = results.optJSONObject(0) ?: throw MarketException("Symbol unavailable")
        val meta = result.getJSONObject("meta")
        val previous = meta.finiteNumber("chartPreviousClose") ?: meta.finiteNumber("previousClose")
        val live = meta.finiteNumber("regularMarketPrice")
        val price = live ?: previous ?: throw MarketException("Provider returned no usable price")
        val quotes = result.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)
        val closes = quotes?.optJSONArray("close")
        val times = result.optJSONArray("timestamp")
        val points = mutableListOf<Double>()
        if (closes != null && times != null) {
            for (i in 0 until minOf(closes.length(), times.length())) {
                if (!times.isNull(i) && !closes.isNull(i)) {
                    val value = closes.optDouble(i, Double.NaN)
                    if (value.isFinite() && value > 0) points.add(value)
                }
            }
        }
        fun text(key: String): String? = meta.optString(key).takeIf { it.isNotBlank() && it != "null" }
        val regular = meta.optJSONObject("currentTradingPeriod")?.optJSONObject("regular")
        val start = regular?.optLong("start", 0)?.takeIf { it in 1..Long.MAX_VALUE / 1000 }?.times(1000)
        val end = regular?.optLong("end", 0)?.takeIf { it in 1..Long.MAX_VALUE / 1000 }?.times(1000)
        return Quote(
            symbol = text("symbol") ?: requested,
            name = text("shortName") ?: text("longName") ?: requested,
            currency = text("currency"),
            exchange = text("exchangeName"),
            timezone = text("exchangeTimezoneName"),
            price = price,
            previousClose = previous,
            quoteTime = if (live == null) null else meta.optLong("regularMarketTime", 0)
                .takeIf { it in 1..Long.MAX_VALUE / 1000 }?.times(1000),
            fetchedAt = fetchedAt,
            points = points.takeLast(200),
            previousCloseFallback = live == null,
            regularStart = start,
            regularEnd = end,
        )
    } catch (e: MarketException) {
        throw e
    } catch (e: org.json.JSONException) {
        throw MarketException("Provider response could not be read")
    }
}

class YahooProvider : MarketDataProvider {
    override suspend fun fetch(symbol: String): Quote = withContext(Dispatchers.IO) {
        val normalized = normalizeSymbol(symbol)
        val encoded = URLEncoder.encode(normalized, StandardCharsets.UTF_8.name())
        val connection = URL("https://query1.finance.yahoo.com/v8/finance/chart/$encoded?range=1d&interval=5m")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", "Folio Android")
            when (val status = connection.responseCode) {
                200 -> parseQuote(connection.inputStream.bufferedReader().use { it.readText() }, normalized, System.currentTimeMillis())
                404 -> throw MarketException("Symbol unavailable")
                429 -> {
                    val retry = retryAfterDeadline(connection.getHeaderField("Retry-After"), System.currentTimeMillis())
                        ?: System.currentTimeMillis() + 15 * 60_000
                    throw MarketException("Rate limited by provider; retry after ${dateTime(retry)}", true, retry, true)
                }
                in 500..599 -> throw MarketException("Provider unavailable (HTTP $status)", true,
                    retryAfterDeadline(connection.getHeaderField("Retry-After"), System.currentTimeMillis()))
                else -> throw MarketException("Provider request failed (HTTP $status)")
            }
        } catch (e: java.io.IOException) {
            throw MarketException("Network unavailable: ${e.localizedMessage ?: "connection failed"}", true)
        } finally {
            connection.disconnect()
        }
    }
}
