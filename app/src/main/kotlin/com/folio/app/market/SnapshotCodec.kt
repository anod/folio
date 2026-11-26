package com.folio.app.market

import org.json.JSONArray
import org.json.JSONObject

fun decodeEntries(json: String): List<Entry> {
    val array = JSONArray(json)
    return (0 until array.length()).map { i ->
        val obj = array.getJSONObject(i)
        val quote = obj.optJSONObject("quote")?.let { q ->
            val points = q.optJSONArray("points") ?: JSONArray()
            val pointTimes = q.optJSONArray("pointTimes") ?: JSONArray()
            Quote(q.getString("symbol"), q.getString("name"), q.optString("currency").ifEmpty { null },
                q.optString("exchange").ifEmpty { null }, q.optString("timezone").ifEmpty { null },
                q.getDouble("price"), if (q.isNull("previous")) null else q.getDouble("previous"),
                if (q.isNull("quoteTime")) null else q.getLong("quoteTime"), q.getLong("fetchedAt"),
                (0 until points.length()).map { points.getDouble(it) }, q.optBoolean("fallback"),
                if (q.isNull("regularStart")) null else q.getLong("regularStart"),
                if (q.isNull("regularEnd")) null else q.getLong("regularEnd"),
                (0 until pointTimes.length()).map { pointTimes.getLong(it) })
        }
        Entry(obj.getString("symbol"), quote,
            if (obj.isNull("attemptedAt")) null else obj.getLong("attemptedAt"),
            obj.optString("outcome").ifEmpty { null },
            if (obj.isNull("retryAfter")) null else obj.getLong("retryAfter"),
            obj.optInt("failures", 0))
    }
}

fun encodeEntries(entries: List<Entry>): String {
    val array = JSONArray()
    entries.forEach { entry ->
        val o = JSONObject().put("symbol", entry.symbol)
            .put("attemptedAt", entry.attemptedAt ?: JSONObject.NULL)
            .put("outcome", entry.outcome ?: "")
            .put("retryAfter", entry.retryAfter ?: JSONObject.NULL)
            .put("failures", entry.failures)
        entry.quote?.let { q ->
            o.put("quote", JSONObject().put("symbol", q.symbol).put("name", q.name)
                .put("currency", q.currency ?: "").put("exchange", q.exchange ?: "")
                .put("timezone", q.timezone ?: "").put("price", q.price)
                .put("previous", q.previousClose ?: JSONObject.NULL)
                .put("quoteTime", q.quoteTime ?: JSONObject.NULL).put("fetchedAt", q.fetchedAt)
                .put("points", JSONArray(q.points)).put("fallback", q.previousCloseFallback)
                .put("pointTimes", JSONArray(q.pointTimes))
                .put("regularStart", q.regularStart ?: JSONObject.NULL).put("regularEnd", q.regularEnd ?: JSONObject.NULL))
        }
        array.put(o)
    }
    return array.toString()
}
