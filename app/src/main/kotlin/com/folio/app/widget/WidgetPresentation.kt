package com.folio.app.widget

import com.folio.app.market.Entry
import com.folio.app.market.changeText
import com.folio.app.market.compactDateTime
import com.folio.app.market.number
import org.json.JSONArray
import org.json.JSONObject

data class WidgetConfig(
    val symbols: List<String>,
    val theme: String = "Graphite",
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
                o.optString("theme", "Graphite").takeIf { it in listOf("Graphite", "Sage", "Paper") } ?: "Graphite",
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
    val price: String,
    val change: String,
    val quoteTime: String,
    val fetchedAt: String,
    val status: String?,
    val lastAttempt: String?,
    val points: List<Double>,
    val negative: Boolean,
)

data class WidgetPresentation(val layout: WidgetLayout, val rows: List<WidgetRowData>)

fun presentWidget(config: WidgetConfig, entries: List<Entry>, layout: WidgetLayout): WidgetPresentation {
    val bySymbol = entries.associateBy { it.symbol }
    val rows = config.symbols.mapNotNull { bySymbol[it] }.map { entry ->
        val q = entry.quote
        WidgetRowData(
            entry.symbol, q?.name ?: "No saved quote",
            q?.let { "${number(it.price)} ${it.currency ?: ""}".trim() } ?: "Unavailable",
            q?.let(::changeText) ?: (entry.outcome ?: "No quote yet"),
            "Stock price: ${compactDateTime(q?.quoteTime)}",
            "Refreshed: ${compactDateTime(q?.fetchedAt)}",
            listOfNotNull(
                if (q?.previousCloseFallback == true) "Previous close, not a current trade" else null,
                if (entry.outcome != null && entry.outcome != "Success")
                    "${if (q == null) "" else "Cached · "}${entry.outcome}" else null,
            ).joinToString(" · ").ifEmpty { null },
            if (entry.outcome != null && entry.outcome != "Success") "Attempt: ${compactDateTime(entry.attemptedAt)}" else null,
            q?.points ?: emptyList(),
            (q?.change ?: 0.0) < 0,
        )
    }
    return WidgetPresentation(layout, if (layout == WidgetLayout.WATCHLIST) rows.take(4) else rows.take(1))
}
