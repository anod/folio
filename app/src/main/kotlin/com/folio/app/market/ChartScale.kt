package com.folio.app.market

import java.time.Instant
import java.time.ZoneId
import java.time.DateTimeException
import java.time.format.DateTimeFormatter
import java.util.Locale

fun chartFractions(points: List<Double>): List<Float> {
    if (points.isEmpty()) return emptyList()
    val min = points.min()
    val range = points.max() - min
    return if (range == 0.0) points.map { 0.5f }
    else points.map { ((it - min) / range).toFloat() }
}

data class ChartTimeAxis(val fractions: List<Float>, val labels: List<String>, val timezone: String)

fun chartTimeAxis(times: List<Long>, pointCount: Int, timezone: String?, locale: Locale = Locale.getDefault()): ChartTimeAxis? {
    if (pointCount < 2 || times.size != pointCount || times.any { it <= 0 } ||
        times.zipWithNext().any { (a, b) -> a >= b }) return null
    val zone = try { timezone?.let { ZoneId.of(it) } ?: ZoneId.of("UTC") }
    catch (_: DateTimeException) { ZoneId.of("UTC") }
    val first = times.first()
    val span = times.last() - first
    val sameDay = Instant.ofEpochMilli(first).atZone(zone).toLocalDate() ==
        Instant.ofEpochMilli(times.last()).atZone(zone).toLocalDate()
    val pattern = when {
        sameDay -> "HH:mm"
        span < 2 * 86_400_000L -> "MMM d HH:mm"
        span < 365 * 86_400_000L -> "MMM d"
        else -> "MMM yyyy"
    }
    val format = DateTimeFormatter.ofPattern(pattern, locale).withZone(zone)
    return ChartTimeAxis(
        times.map { ((it - first).toDouble() / span).toFloat() },
        (0..3).map { format.format(Instant.ofEpochMilli(first + span / 3 * it + span % 3 * it / 3)) },
        zone.id,
    )
}
