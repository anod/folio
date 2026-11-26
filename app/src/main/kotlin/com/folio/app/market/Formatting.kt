package com.folio.app.market

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

fun number(value: Double?): String = value?.let {
    NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(it)
} ?: "—"

fun signed(value: Double?): String = value?.let {
    (if (it >= 0) "+" else "−") + number(kotlin.math.abs(it))
} ?: "—"

fun changeText(quote: Quote): String =
    if (quote.change == null || quote.percent == null) "Change unavailable"
    else "${signed(quote.change)} (${signed(quote.percent)}%)"

fun dateTime(value: Long?): String = value?.let {
    val zone = java.util.TimeZone.getDefault()
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(Date(it)) +
        " " + zone.getDisplayName(zone.inDaylightTime(Date(it)), java.util.TimeZone.SHORT)
} ?: "Unknown"

fun compactDateTime(value: Long?): String = value?.let {
    java.text.SimpleDateFormat("MMM d, yyyy HH:mm z", Locale.getDefault()).format(Date(it))
} ?: "Unknown"
