package com.folio.app.ui

import android.appwidget.AppWidgetManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.folio.app.market.*
import com.folio.app.ui.theme.FolioTheme
import com.folio.app.widget.FolioWidget
import com.folio.app.widget.FolioWidgetReceiver
import com.folio.app.widget.WidgetConfig
import com.folio.app.widget.WidgetLayout
import com.folio.app.widget.WidgetPresentation
import com.folio.app.widget.moveSymbol
import com.folio.app.widget.presentWidget
import com.folio.app.widget.PinResultReceiver
import kotlinx.coroutines.launch

private val sage = Color(0xffc9ef9a)
private val graphite = Color(0xff1b2923)

@Composable
fun FolioApp(requestedSymbol: Pair<String, Long>? = null, providedStore: Store? = null) {
    val context = LocalContext.current
    val store = remember(context, providedStore) { providedStore ?: Store.get(context) }
    val entries by store.entries.collectAsState()
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(if (entries.isEmpty()) "Welcome" else "Watchlist") }
    var detail by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf(store.getString("appTheme", "System")) }
    var size by remember { mutableStateOf("Compact") }
    var palette by remember { mutableStateOf("Graphite") }
    var opacity by remember { mutableFloatStateOf(100f) }
    var chosen by remember { mutableStateOf<List<String>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var pinMessage by remember(screen) { mutableStateOf("") }
    var refreshMessage by remember(screen) { mutableStateOf("") }
    var settingsMessage by remember(screen) { mutableStateOf("") }
    var reordering by remember { mutableStateOf(false) }
    val selectedSymbols = chosen ?: entries.map { it.symbol }
    val config = WidgetConfig(selectedSymbols, palette, opacity.toInt())
    LaunchedEffect(requestedSymbol) {
        requestedSymbol?.first?.let { detail = it; screen = "Detail" }
    }
    BackHandler(enabled = screen in listOf("Detail", "Add")) { screen = "Watchlist" }
    val scroll = key(screen) { rememberScrollState() }
    FolioTheme(darkTheme = when (theme) {
        "Dark" -> true
        "Light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    listOf("Watchlist", "Widgets", "Data", "Settings").forEach { tab ->
                        NavigationBarItem(selected = screen == tab, onClick = { screen = tab },
                            icon = { NavigationIcon(tab) },
                            label = { Text(tab) })
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (screen) {
                    "Welcome" -> {
                        Eyebrow("MEET FOLIO")
                        WidgetPreview(presentWidget(WidgetConfig(emptyList()), emptyList(), WidgetLayout.COMPACT), "Graphite", 100f)
                        Title("Follow the market.\nKeep your calm.")
                        Text("Your favorite stocks, beautifully at home. No account. No noise. Just perspective.")
                        Button(onClick = { screen = "Add" }, modifier = Modifier.fillMaxWidth()) { Text("Choose your first stock") }
                        TextButton(onClick = { screen = "Widgets" }) { Text("Explore the widget preview") }
                    }
                    "Watchlist" -> {
                        Eyebrow("YOUR WATCHLIST")
                        Title("A little clarity.")
                        Text("Your shortlist. Not the whole market.")
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Eyebrow("YOUR WATCHLIST")
                                Text("${entries.size} ${if (entries.size == 1) "stock" else "stocks"}. Your perspective.",
                                    style = MaterialTheme.typography.titleLarge)
                                Text("Saved prices, not guaranteed real time.", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Following", Modifier.align(Alignment.CenterVertically), fontWeight = FontWeight.Medium)
                            TextButton(onClick = { reordering = !reordering }) { Text(if (reordering) "Done" else "Reorder") }
                        }
                        TextButton(onClick = { screen = "Data" }) { Text("Review refresh times and values →") }
                        if (entries.isEmpty()) Text("No stocks yet. Add an exact symbol to get started.")
                        entries.forEach { e ->
                            Column(Modifier.fillMaxWidth().clickable { detail = e.symbol; screen = "Detail" }.padding(vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center) {
                                        Text(e.symbol.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(e.symbol, fontWeight = FontWeight.Bold)
                                        Text(e.quote?.name ?: "No saved quote", style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    e.quote?.let { q -> Box(Modifier.width(42.dp)) { Chart(q.points, changeColor(q)) } }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(e.quote?.let { "${number(it.price)} ${it.currency ?: ""}" } ?: "Unavailable",
                                            fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                                        e.quote?.let { Text(changeText(it), color = changeColor(it), style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                                e.quote?.let { q ->
                                    Text("Stock price: ${compactDateTime(q.quoteTime)}", style = MaterialTheme.typography.bodySmall)
                                    Text("Refreshed: ${compactDateTime(q.fetchedAt)}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (q.previousCloseFallback)
                                        Text("Previous close, not a current trade", style = MaterialTheme.typography.bodySmall)
                                }
                                if (e.outcome != null && e.outcome != "Success")
                                    Text(e.outcome, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                if (reordering) Row {
                                    TextButton(onClick = { scope.launch { store.move(e.symbol, -1) } }) { Text("Move up") }
                                    TextButton(onClick = { scope.launch { store.move(e.symbol, 1) } }) { Text("Move down") }
                                    TextButton(onClick = { scope.launch { store.remove(e.symbol); FolioWidget().updateAll(context) } }) { Text("Remove") }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        OutlinedButton(onClick = { screen = "Add" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("+ Follow a stock") }
                    }
                    "Widgets" -> {
                        Eyebrow("YOUR HOME SCREEN, YOUR WAY")
                        Title("Make it yours.")
                        Text("Preview saved values here. Place a widget anywhere.")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("Compact", "Wide", "Watchlist").forEachIndexed { index, value ->
                                SegmentedButton(selected = size == value, onClick = { size = value },
                                    shape = SegmentedButtonDefaults.itemShape(index, 3)) {
                                    Text(value, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        val layout = when (size) { "Watchlist" -> WidgetLayout.WATCHLIST; "Wide" -> WidgetLayout.WIDE; else -> WidgetLayout.COMPACT }
                        WidgetPreview(presentWidget(config, entries, layout), palette, opacity)
                        Text("Preview uses your saved data; no network request.")
                        Row { listOf("Graphite", "Sage", "Paper").forEach {
                            TextButton(onClick = { palette = it }) { Text(if (palette == it) "✓ $it" else it) }
                        } }
                        Text("Background opacity: ${opacity.toInt()}%")
                        Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 35f..100f)
                        Text("Choose and order stocks · first is featured")
                        val available = entries.map { it.symbol }
                        (selectedSymbols.filter { it in available } + available.filterNot { it in selectedSymbols }).forEach { symbol ->
                            Row {
                                Checkbox(checked = symbol in selectedSymbols, onCheckedChange = { checked ->
                                    chosen = if (checked) selectedSymbols + symbol else selectedSymbols - symbol
                                })
                                Text(symbol, Modifier.weight(1f).padding(top = 12.dp))
                                TextButton(enabled = symbol in selectedSymbols, onClick = { chosen = moveSymbol(selectedSymbols, symbol, -1) }) { Text("↑") }
                                TextButton(enabled = symbol in selectedSymbols, onClick = { chosen = moveSymbol(selectedSymbols, symbol, 1) }) { Text("↓") }
                            }
                        }
                        Button(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), onClick = {
                            val manager = AppWidgetManager.getInstance(context)
                            if (manager.isRequestPinAppWidgetSupported) {
                                store.putString("draft.config", config.encode())
                                val callback = Intent(context, PinResultReceiver::class.java)
                                    .setData(Uri.parse("folio://pin/${java.util.UUID.randomUUID()}"))
                                    .putExtra("config", config.encode())
                                val pending = PendingIntent.getBroadcast(context, 0, callback,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                                val requested = manager.requestPinAppWidget(ComponentName(context, FolioWidgetReceiver::class.java), null, pending)
                                pinMessage = if (requested) "Confirm placement in your launcher." else "Your launcher declined the pin request."
                            } else pinMessage = "Your launcher does not support pinning here. Add Folio from the home screen widget picker."
                        }) { Text("Add to home screen ↗") }
                        if (pinMessage.isNotEmpty()) Text(pinMessage)
                    }
                    "Detail" -> {
                        val e = entries.find { it.symbol == detail }
                        TextButton(onClick = { screen = "Watchlist" }) { Text("← Watchlist") }
                        Title(e?.quote?.name ?: detail)
                        Text(detail)
                        if (e == null) Text("This stock is no longer in your watchlist.")
                        e?.quote?.let { q ->
                            Text("${number(q.price)} ${q.currency ?: ""}", style = MaterialTheme.typography.displaySmall)
                            Text(changeText(q), color = changeColor(q))
                            Chart(q.points, changeColor(q))
                            Text("Previous close: ${number(q.previousClose)}")
                            Text("Exchange: ${q.exchange ?: "Unknown"} · ${q.timezone ?: "Unknown timezone"}")
                            if (q.previousCloseFallback) Text("Price is previous close, not a current trade.")
                            QuoteTimes(q)
                        }
                        e?.let { Text("Last refresh attempt: ${dateTime(it.attemptedAt)} · ${it.outcome ?: "Not attempted"}") }
                    }
                    "Add" -> AddScreen(store, onBack = { screen = "Watchlist" }, onSave = { quote ->
                        scope.launch { store.add(quote); FolioWidget().updateAll(context); screen = "Watchlist" }
                    })
                    "Data" -> {
                        Eyebrow("DATA, WITH CONTEXT")
                        Title("Nothing hidden.")
                        Text("Your last saved values, easy to review.")
                        Text("Stock price time is when the provider priced the stock. Refresh time is when Folio received it; an old price can have a recent refresh.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                            enabled = !refreshing && entries.isNotEmpty(), onClick = {
                            refreshing = true
                            scope.launch {
                                val result = store.refresh()
                                FolioWidget().updateAll(context)
                                if (result.retryPending) Refresh.retry(context)
                                refreshMessage = "${result.successes} of ${result.attempted} attempted stocks refreshed · ${result.total - result.attempted} deferred"
                                result.providerRetryAfter?.let { refreshMessage += " · Provider rate limited until ${dateTime(it)}" }
                                refreshing = false
                            }
                        }) { Text(if (refreshing) "Refreshing…" else "↻ Refresh now") }
                        if (refreshMessage.isNotEmpty()) Text(refreshMessage)
                        entries.forEach { e -> ReviewCard(e) }
                        if (entries.isEmpty()) {
                            Text("No saved quotes yet.")
                            Button(onClick = { screen = "Add" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Follow a stock") }
                        }
                    }
                    "Settings" -> {
                        Eyebrow("SMALL DETAILS. YOUR CHOICE.")
                        Title("Set your rhythm.")
                        Text("Refresh interval · best effort, not a guarantee")
                        Row { listOf("15", "30", "60").forEach { interval ->
                            TextButton(onClick = {
                                store.putString("interval", interval)
                                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, FolioWidgetReceiver::class.java))
                                if (ids.isNotEmpty()) Refresh.schedule(context, interval.toLong())
                                settingsMessage = "$interval-minute preference saved"
                            }) { Text("$interval min") }
                        } }
                        if (settingsMessage.isNotEmpty()) Text(settingsMessage)
                        Text("App theme")
                        Row { listOf("System", "Light", "Dark").forEach { option ->
                            TextButton(onClick = { theme = option; store.putString("appTheme", option) }) {
                                Text(if (theme == option) "✓ $option" else option)
                            }
                        } }
                        Text("Market provider: Yahoo chart endpoint (unofficial). It may be delayed, rate limited or unavailable. Requests disclose your IP and symbols to the provider.")
                        Text("Android and your launcher can delay background updates. Prices are not guaranteed real time.")
                    }
                }
            }
        }
    }
}

@Composable private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium)
@Composable private fun Eyebrow(text: String) = Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)

@Composable
private fun AddScreen(store: Store, onBack: () -> Unit, onSave: (Quote) -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var quote by remember { mutableStateOf<Quote?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    TextButton(onClick = onBack) { Text("← Watchlist") }
    Title("Find your next perspective.")
    Text("Enter an exact stock symbol to preview it.")
    OutlinedTextField(input, onValueChange = { input = it; quote = null; error = "" }, label = { Text("Symbol") }, singleLine = true, enabled = !loading)
    Button(enabled = !loading, onClick = {
        loading = true
        scope.launch {
            try { quote = store.lookup(input); error = "" }
            catch (e: MarketException) { error = e.message ?: "Lookup failed" }
            finally { loading = false }
        }
    }) { Text(if (loading) "Looking up…" else "Look up") }
    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
    quote?.let { q ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${q.symbol} · ${q.name}", fontWeight = FontWeight.Bold)
                Text("${number(q.price)} ${q.currency ?: "Unknown currency"}", style = MaterialTheme.typography.headlineSmall)
                Text("${q.exchange ?: "Unknown exchange"} · ${changeText(q)}")
                if (q.previousCloseFallback) Text("Previous close, not a current trade")
                QuoteTimes(q)
            }
        }
        Button(onClick = { onSave(q) }) { Text("+ Add to watchlist") }
    }
}

@Composable
private fun ReviewCard(e: Entry) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(e.symbol, fontWeight = FontWeight.Bold)
                    Text(e.quote?.name ?: "No saved value", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                e.quote?.let { Text("${number(it.price)} ${it.currency ?: "Unknown currency"}",
                    fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium) }
            }
            e.quote?.let { q ->
                Text("${changeText(q)} · Previous close ${number(q.previousClose)}",
                    style = MaterialTheme.typography.bodySmall, color = changeColor(q))
                if (q.previousCloseFallback) Text("Previous close fallback, not a current trade")
                QuoteTimes(q)
            }
            Text("Last refresh attempt: ${dateTime(e.attemptedAt)}",
                style = MaterialTheme.typography.bodySmall)
            Text(e.outcome ?: "Not attempted", style = MaterialTheme.typography.bodySmall,
                color = if (e.outcome != null && e.outcome != "Success") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun QuoteTimes(quote: Quote) {
    Text("Stock price time: ${dateTime(quote.quoteTime)}", style = MaterialTheme.typography.bodySmall)
    Text("Last successful refresh: ${dateTime(quote.fetchedAt)}", style = MaterialTheme.typography.bodySmall)
}

@Composable private fun changeColor(quote: Quote): Color =
    if ((quote.change ?: 0.0) < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

@Composable private fun NavigationIcon(tab: String) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(20.dp).semantics { contentDescription = tab }) {
        val stroke = 1.6.dp.toPx()
        val step = size.width / 24f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * step, y1 * step), Offset(x2 * step, y2 * step), stroke)
        when (tab) {
            "Watchlist" -> {
                line(3f, 4f, 3f, 21f); line(3f, 21f, 22f, 21f)
                line(6f, 16f, 11f, 10f); line(11f, 10f, 16f, 13f); line(16f, 13f, 22f, 5f)
            }
            "Widgets" -> for (x in listOf(3f, 14f)) for (y in listOf(3f, 14f))
                drawRoundRect(color, Offset(x * step, y * step), androidx.compose.ui.geometry.Size(7 * step, 7 * step),
                    androidx.compose.ui.geometry.CornerRadius(1.5f * step), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            "Data" -> {
                drawArc(color, 30f, 300f, false, Offset(4 * step, 4 * step), androidx.compose.ui.geometry.Size(16 * step, 16 * step),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                line(21f, 3f, 21f, 9f); line(21f, 9f, 15f, 9f)
            }
            else -> {
                line(3f, 7f, 21f, 7f); line(3f, 17f, 21f, 17f)
                drawCircle(color, 3 * step, Offset(9 * step, 7 * step))
                drawCircle(color, 3 * step, Offset(16 * step, 17 * step))
            }
        }
    }
}

@Composable
private fun WidgetPreview(presentation: WidgetPresentation, palette: String, opacity: Float) {
    val bg = when (palette) { "Sage" -> Color(0xffe2edcf); "Paper" -> Color(0xfff5f3eb); else -> graphite }
    val fg = if (palette == "Graphite") Color(0xfff1f6e8) else Color(0xff263723)
    Box(Modifier.fillMaxWidth().background(Color(0xffc9cec0), RoundedCornerShape(24.dp)).padding(16.dp),
        contentAlignment = Alignment.Center) {
    Column(Modifier.widthIn(max = if (presentation.layout == WidgetLayout.COMPACT) 200.dp else 360.dp)
        .fillMaxWidth().defaultMinSize(minHeight = if (presentation.layout == WidgetLayout.WATCHLIST) 280.dp else 180.dp)
        .background(bg.copy(alpha = opacity / 100f), RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val row = presentation.rows.firstOrNull()
        if (row == null) Text("A little space for your stocks.\nFollow a stock in Folio.", color = fg)
        else if (presentation.layout == WidgetLayout.WATCHLIST) {
            Text("Your watchlist", color = fg, fontWeight = FontWeight.Bold)
            presentation.rows.forEach { item ->
                val accent = previewAccent(item.negative, palette)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.symbol, color = fg, fontWeight = FontWeight.Bold)
                        Text(item.price, color = fg, style = MaterialTheme.typography.bodySmall)
                        Text(item.change, color = accent, style = MaterialTheme.typography.bodySmall)
                    }
                    Box(Modifier.width(44.dp)) { Chart(item.points, accent) }
                }
                Text(item.quoteTime, fontSize = 11.sp, color = fg)
                Text(item.fetchedAt, fontSize = 11.sp, color = fg)
                item.status?.let { Text(it, fontSize = 11.sp, color = fg) }
                HorizontalDivider(color = fg.copy(alpha = 0.12f))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.symbol, color = fg, fontWeight = FontWeight.Bold)
                    Text(row.name, color = fg, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(row.price, fontSize = 24.sp, color = fg, fontFamily = FontFamily.Monospace)
                    Text(row.change, color = previewAccent(row.negative, palette), style = MaterialTheme.typography.bodySmall)
                }
                if (presentation.layout == WidgetLayout.WIDE && row.points.size > 1)
                    Box(Modifier.weight(0.65f)) { Chart(row.points, previewAccent(row.negative, palette)) }
            }
            Text(row.quoteTime, fontSize = 11.sp, color = fg)
            Text(row.fetchedAt, fontSize = 11.sp, color = fg)
            row.status?.let { Text(it, fontSize = 11.sp, color = fg) }
            row.lastAttempt?.let { Text(it, fontSize = 11.sp, color = fg) }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Folio", color = fg, style = MaterialTheme.typography.bodySmall)
            Text("↻ Refresh  ·  Settings", fontSize = 11.sp, color = fg)
        }
    }
    }
}

private fun previewAccent(negative: Boolean, palette: String): Color =
    if (negative) { if (palette == "Graphite") Color(0xffe2a38e) else Color(0xffa65a47) }
    else { if (palette == "Graphite") sage else Color(0xff506e37) }

@Composable
private fun Chart(points: List<Double>, color: Color) {
    if (points.size < 2) return
    Canvas(Modifier.fillMaxWidth().height(45.dp)) {
        val min = points.min()
        val range = (points.max() - min).takeIf { it > 0 } ?: 1.0
        fun point(i: Int) = Offset(size.width * i / (points.size - 1), size.height * (1 - ((points[i] - min) / range).toFloat()))
        for (i in 1 until points.size) drawLine(color, point(i - 1), point(i), strokeWidth = 2.dp.toPx())
    }
}
