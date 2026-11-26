package com.folio.app.widget

import com.folio.app.market.Entry
import com.folio.app.market.TradingStatus
import com.folio.app.market.changeText
import com.folio.app.market.tradingStatus
import com.folio.app.market.number
import com.folio.app.market.signed
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun widgetDateTime(value: Long?): String = value?.let {
    SimpleDateFormat("MMM d HH:mm", Locale.getDefault()).format(Date(it))
} ?: "Unknown"

data class WidgetConfig(
    val symbols: List<String>,
    val theme: String = "System",
    val opacity: Int = 100,
) {
    fun encode(): String = JSONObject().put("symbols", JSONArray(symbols.distinct()))
        .put("theme", theme).put("opacity", opacity.coerceIn(35, 100)).toString()

    companion object {
        fun decode(json: String): WidgetConfig {
            val o = JSONObject(json)
            val a = o.getJSONArray("symbols")
            return WidgetConfig(
                (0 until a.length()).map { a.getString(it) }.distinct(),
                o.optString("theme", "System").takeIf { it in listOf("System", "Graphite", "Sage", "Paper") } ?: "System",
                o.optInt("opacity", 100).coerceIn(35, 100),
            )
        }
    }
}

fun moveSymbol(symbols: List<String>, symbol: String, delta: Int): List<String> {
    val index = symbols.indexOf(symbol)
    val target = index + delta
    if (index < 0 || target !in symbols.indices) return symbols
    return symbols.toMutableList().apply { java.util.Collections.swap(this, index, target) }
}

enum class WidgetLayout { COMPACT, WIDE, WATCHLIST }

data class WidgetRowData(
    val symbol: String,
    val name: String,
    val exchange: String?,
    val price: String,
    val currency: String?,
    val change: String,
    val percentChange: String,
    val quoteTime: String,
    val fetchedAt: String,
    val status: String?,
    val lastAttempt: String?,
    val points: List<Double>,
    val negative: Boolean,
    val trading: TradingStatus = TradingStatus.UNKNOWN,
)

data class WidgetPresentation(val layout: WidgetLayout, val rows: List<WidgetRowData>)

fun presentWidget(
    config: WidgetConfig,
    entries: List<Entry>,
    layout: WidgetLayout,
    now: Long = System.currentTimeMillis(),
): WidgetPresentation {
    val bySymbol = entries.associateBy { it.symbol }
    val rows = config.symbols.mapNotNull { bySymbol[it] }.map { entry ->
        val q = entry.quote
        WidgetRowData(
            entry.symbol, q?.name ?: "No saved quote", q?.exchange,
            q?.let { number(it.price) } ?: "Unavailable",
            q?.currency,
            q?.let { quote ->
                quote.change?.let { "${if (it < 0) "↘" else "↗"} ${changeText(quote)}" } ?: changeText(quote)
            } ?: (entry.outcome ?: "No quote yet"),
            q?.percent?.let { "${signed(it)}%" } ?: "—",
            "Quote: ${widgetDateTime(q?.quoteTime)}",
            "Fetched: ${widgetDateTime(q?.fetchedAt)}",
            listOfNotNull(
                if (q?.previousCloseFallback == true) "Previous close, not a current trade" else null,
                if (entry.outcome != null && entry.outcome != "Success")
                    "${if (q == null) "" else "Cached · "}${entry.outcome}" else null,
            ).joinToString(" · ").ifEmpty { null },
            if (entry.outcome != null && entry.outcome != "Success") "Attempt: ${widgetDateTime(entry.attemptedAt)}" else null,
            q?.points ?: emptyList(),
            (q?.change ?: 0.0) < 0,
            tradingStatus(q, now),
        )
    }
    return WidgetPresentation(layout, if (layout == WidgetLayout.WATCHLIST) rows.take(4) else rows.take(1))
}
