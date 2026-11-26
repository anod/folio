package com.folio.app.ui

import android.appwidget.AppWidgetManager
import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.folio.app.market.*
import com.folio.app.ui.theme.FolioTheme
import com.folio.app.widget.FolioWidgetReceiver
import com.folio.app.widget.WidgetConfig
import com.folio.app.widget.WidgetLayout
import com.folio.app.widget.WidgetPresentation
import com.folio.app.widget.moveSymbol
import com.folio.app.widget.presentWidget
import com.folio.app.widget.PinResultReceiver
import com.folio.app.widget.refreshIcon
import com.folio.app.widget.sparkline
import com.folio.app.widget.tradingDot
import com.folio.app.widget.tradingDotColor
import com.folio.app.widget.widgetColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var palette by remember { mutableStateOf("System") }
    var opacity by remember { mutableFloatStateOf(100f) }
    var chosen by remember { mutableStateOf<List<String>?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var pinMessage by remember(screen) { mutableStateOf("") }
    var refreshMessage by remember(screen) { mutableStateOf("") }
    var settingsMessage by remember(screen) { mutableStateOf("") }
    var interval by remember { mutableStateOf(store.getString("interval", "15")) }
    var reordering by remember { mutableStateOf(false) }
    val selectedSymbols = chosen ?: entries.map { it.symbol }
    val config = WidgetConfig(selectedSymbols, palette, opacity.toInt())
    LaunchedEffect(requestedSymbol) {
        requestedSymbol?.first?.let { detail = it; screen = "Detail" }
    }
    BackHandler(enabled = screen in listOf("Detail", "Add")) { screen = "Watchlist" }
    val scroll = key(screen) { rememberScrollState() }
    val darkTheme = when (theme) {
        "Dark" -> true
        "Light" -> false
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    FolioTheme(darkTheme = darkTheme) {
        Scaffold(
            bottomBar = {
                if (screen != "Welcome") {
                    FolioNavigation(if (screen in listOf("Detail", "Add")) "Watchlist" else screen) { screen = it }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(scroll).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (screen) {
                    "Welcome" -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Eyebrow("MEET FOLIO")
                        }
                        WelcomeIllustration()
                        Title("Follow the market.\nKeep your calm.")
                        Text("Your favorite stocks, beautifully at home. No account. No noise. Just perspective.")
                        WelcomeFeature("Widgets that feel like yours")
                        WelcomeFeature("Clear prices. Honest timestamps.")
                        Button(onClick = { screen = "Add" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Choose your first stock") }
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { screen = "Widgets" }) { Text("Explore the widget preview") }
                        }
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            QuoteTradingDot(e.quote)
                                            Text(e.symbol, fontWeight = FontWeight.Bold)
                                        }
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
                                    TextButton(onClick = { scope.launch { store.remove(e.symbol); updateWidgets(context) } }) { Text("Remove") }
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
                        WidgetDesigner(entries, config, size, onSizeChange = { size = it }, onConfigChange = {
                            chosen = it.symbols; palette = it.theme; opacity = it.opacity.toFloat()
                        })
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
                        StockDetail(detail, e, store, onBack = { screen = "Watchlist" })
                    }
                    "Add" -> AddScreen(store, onBack = { screen = "Watchlist" }, onSave = { quote ->
                        scope.launch { store.add(quote); updateWidgets(context); screen = "Watchlist" }
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
                                updateWidgets(context)
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
                        Text("A companion that respects your battery.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SettingsHeading("Refresh")
                        SettingsGroup {
                            Text("Background refresh", fontWeight = FontWeight.Medium)
                            Text("Best effort while widgets are active", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ChoicePills(listOf("15", "30", "60"), interval, { "$it min" }) { option ->
                                interval = option
                                store.putString("interval", option)
                                val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, FolioWidgetReceiver::class.java))
                                if (ids.isNotEmpty()) Refresh.schedule(context, option.toLong())
                                settingsMessage = "$option-minute preference saved"
                            }
                            HorizontalDivider()
                            Text("Conserves battery outside known sessions", fontWeight = FontWeight.Medium)
                            Text("Uses recent provider session information when available; not an exchange calendar.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (settingsMessage.isNotEmpty()) Text(settingsMessage)
                        SettingsGroup {
                            Text("Android may delay updates to save battery. The interval is a preference, not a guarantee.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        SettingsHeading("Appearance")
                        SettingsGroup {
                            Text("App theme", fontWeight = FontWeight.Medium)
                            ChoicePills(listOf("System", "Light", "Dark"), theme, { it }) { option ->
                                theme = option
                                store.putString("appTheme", option)
                            }
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Show + / − signs")
                                Text("Always on", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        SettingsHeading("Your data")
                        SettingsGroup {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Market provider"); Text("Yahoo · unofficial", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Accounts"); Text("None", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("The provider receives your IP and requested symbols. Prices may be delayed or unavailable.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeIllustration() {
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
    Box(
        Modifier.fillMaxWidth().height(270.dp)
            .background(Brush.radialGradient(listOf(glow, Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.offset(x = 12.dp, y = 16.dp).size(width = 190.dp, height = 220.dp)
                .graphicsLayer { rotationZ = -7f }
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f), RoundedCornerShape(24.dp)),
        )
        Column(
            Modifier.size(width = 190.dp, height = 220.dp)
                .graphicsLayer { rotationZ = -7f }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp)).padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("MSFT", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text("Microsoft", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Column {
                Text("489.93", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineMedium)
                Text("+0.99%", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            Chart(listOf(1.0, 1.5, 1.3, 2.2, 1.9, 2.8, 2.6, 3.5, 3.1, 4.0, 3.8, 4.6), MaterialTheme.colorScheme.primary)
            Text("Illustrative widget preview", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun WelcomeFeature(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("✓", color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium)
@Composable private fun Eyebrow(text: String) = Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)

@Composable
private fun FolioNavigation(selected: String, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(colors.background)) {
        HorizontalDivider(color = colors.outlineVariant)
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).selectableGroup().padding(vertical = 5.dp)) {
            listOf("Watchlist", "Widgets", "Data", "Settings").forEach { tab ->
                val active = tab == selected
                Column(
                    Modifier.weight(1f).selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) })
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(32.dp).background(
                            if (active) colors.secondaryContainer else Color.Transparent, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CompositionLocalProvider(LocalContentColor provides if (active) colors.onSecondaryContainer else colors.onSurfaceVariant) {
                            NavigationIcon(tab)
                        }
                    }
                    Text(tab, style = MaterialTheme.typography.labelSmall,
                        color = if (active) colors.onBackground else colors.onSurfaceVariant,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun BackControl(onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(50))
            .clickable(onClickLabel = "Back to watchlist", onClick = onClick)
            .semantics { contentDescription = "Back to watchlist" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 1.7.dp.toPx()
            drawLine(color, Offset(size.width * .8f, size.height * .5f), Offset(size.width * .2f, size.height * .5f), stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * .2f, size.height * .5f), Offset(size.width * .5f, size.height * .2f), stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width * .2f, size.height * .5f), Offset(size.width * .5f, size.height * .8f), stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun StockDetail(symbol: String, entry: Entry?, store: Store, onBack: () -> Unit) {
    val quote = entry?.quote
    var range by remember(symbol) { mutableStateOf(ChartRange.DAY) }
    val history = remember(symbol) { mutableStateMapOf<ChartRange, ChartSeries>() }
    var loadingRange by remember(symbol) { mutableStateOf<ChartRange?>(null) }
    var historyError by remember(symbol) { mutableStateOf<String?>(null) }
    var retry by remember(symbol) { mutableIntStateOf(0) }
    LaunchedEffect(symbol, range, retry) {
        val selected = range
        historyError = null
        if (quote != null && selected != ChartRange.DAY && history[selected] == null) {
            loadingRange = selected
            try {
                history[selected] = store.chartHistory(symbol, selected)
            } catch (e: MarketException) {
                historyError = e.message ?: "Chart could not be loaded."
            } finally {
                loadingRange = null
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        BackControl(onBack)
        quote?.let {
            Text(listOfNotNull(it.exchange, it.currency).joinToString(" · "),
                Modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Title(quote?.name ?: symbol)
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuoteTradingDot(quote)
                Text(symbol, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) {
            Text(symbol.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
    when {
        entry == null -> Text("This stock is no longer in your watchlist.")
        quote == null -> Text("No saved quote is available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> {
            Spacer(Modifier.height(4.dp))
            Text(number(quote.price), style = MaterialTheme.typography.displaySmall)
            Text(changeText(quote), color = changeColor(quote))
            Text("Daily change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoicePills(ChartRange.entries.map { it.name }, range.name,
                label = { ChartRange.valueOf(it).label }, onSelect = { range = ChartRange.valueOf(it) })
            val series = if (range == ChartRange.DAY) {
                ChartSeries(quote.points, quote.pointTimes, quote.timezone, quote.fetchedAt)
            } else history[range]
            val axis = remember(series?.pointTimes, series?.points?.size, series?.timezone) {
                series?.let { chartTimeAxis(it.pointTimes, it.points.size, it.timezone) }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                when {
                    series != null && series.points.size > 1 -> {
                        val chartColor = if (range == ChartRange.DAY) changeColor(quote)
                            else if (series.points.last() < series.points.first()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        DetailChart(series.points, axis?.fractions, chartColor, Modifier.requiredWidth(maxWidth + 40.dp).fillMaxHeight())
                    }
                    historyError != null && range != ChartRange.DAY && loadingRange != range -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(checkNotNull(historyError), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { retry++ }) { Text("Retry chart") }
                        }
                    }
                    range != ChartRange.DAY -> Text("Loading ${range.label} chart…", style = MaterialTheme.typography.bodySmall)
                    else -> Text("Chart unavailable for this quote.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val fontScale = LocalDensity.current.fontScale
            Column(Modifier.fillMaxWidth().height(maxOf(48f, 38f * fontScale).dp)) {
                if (axis != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        axis.labels.forEach { label ->
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("Chart time · ${axis.timezone}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (series != null && series.points.size > 1) {
                    Text("Chart times unavailable in this saved snapshot. Refresh in Data to load time marks.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.fillMaxWidth().height(maxOf(48f, 38f * fontScale).dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${range.label} · ${range.description}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (series != null) Text("Provider samples", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (range != ChartRange.DAY && series != null) Text("Chart fetched: ${dateTime(series.fetchedAt)}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    DetailMetric("Previous close", number(quote.previousClose), Modifier.weight(1f))
                    DetailMetric("Currency", quote.currency ?: "Unknown", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth()) {
                    DetailMetric("Exchange", quote.exchange ?: "Unknown", Modifier.weight(1f))
                    DetailMetric("Exchange timezone", quote.timezone ?: "Unknown", Modifier.weight(1f))
                }
            }
            if (quote.previousCloseFallback) Text("Price is previous close, not a current trade.")
            QuoteTimes(quote)
        }
    }
    entry?.let {
        Text("Last refresh attempt: ${dateTime(it.attemptedAt)} · ${it.outcome ?: "Not attempted"}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailChart(points: List<Double>, timeFractions: List<Float>?, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "Price chart" }) {
        val fractions = chartFractions(points)
        fun point(i: Int) = Offset(size.width * (timeFractions?.get(i) ?: (i.toFloat() / (points.size - 1))),
            size.height * (.1f + .8f * (1 - fractions[i])))
        val line = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1 until points.size) lineTo(point(i).x, point(i).y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = .22f), Color.Transparent)))
        drawPath(line, color, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun ChoicePills(options: List<String>, selected: String, label: (String) -> String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val active = option == selected
            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, RoundedCornerShape(12.dp))
                .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(option) }),
                contentAlignment = Alignment.Center) {
                Text(label(option), style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SettingsHeading(text: String) {
    Text(text, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
fun WidgetDesigner(
    entries: List<Entry>,
    config: WidgetConfig,
    size: String,
    onSizeChange: (String) -> Unit,
    onConfigChange: (WidgetConfig) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    ChoicePills(listOf("Compact", "Wide", "Watchlist"), size, { it }, onSizeChange)
    val layout = when (size) { "Watchlist" -> WidgetLayout.WATCHLIST; "Wide" -> WidgetLayout.WIDE; else -> WidgetLayout.COMPACT }
    WidgetPreview(presentWidget(config, entries, layout, rememberNow()), config.theme, config.opacity.toFloat())
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Stocks", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { choosing = !choosing }) {
            Text(if (choosing) "Done" else "${config.symbols.firstOrNull() ?: "None"} · Change")
        }
    }
    if (choosing) {
        Text("Choose and order stocks · first is featured", style = MaterialTheme.typography.bodySmall)
        val available = entries.map { it.symbol }
        (config.symbols.filter { it in available } + available.filterNot { it in config.symbols }).forEach { symbol ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = symbol in config.symbols, onCheckedChange = { checked ->
                    onConfigChange(config.copy(symbols = if (checked) config.symbols + symbol else config.symbols - symbol))
                })
                Text(symbol, Modifier.weight(1f))
                TextButton(enabled = symbol in config.symbols, onClick = { onConfigChange(config.copy(symbols = moveSymbol(config.symbols, symbol, -1))) }) { Text("↑") }
                TextButton(enabled = symbol in config.symbols, onClick = { onConfigChange(config.copy(symbols = moveSymbol(config.symbols, symbol, 1))) }) { Text("↓") }
            }
        }
        if (entries.isEmpty()) Text("Follow a stock in Folio first.", style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Appearance", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("System" to widgetColors(LocalContext.current, "System").accent,
                "Graphite" to graphite, "Sage" to Color(0xffe2edcf), "Paper" to Color(0xfff5f3eb)).forEach { (name, color) ->
                Box(Modifier.size(40.dp).clickable { onConfigChange(config.copy(theme = name)) }
                    .semantics { contentDescription = "$name widget theme${if (config.theme == name) ", selected" else ""}" },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(26.dp).border(if (config.theme == name) 2.dp else 0.dp,
                        MaterialTheme.colorScheme.primary, RoundedCornerShape(50)).padding(3.dp)
                        .background(color, RoundedCornerShape(50)))
                }
            }
        }
    }
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Background opacity: ${config.opacity}%", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Slider(value = config.opacity.toFloat(), onValueChange = { onConfigChange(config.copy(opacity = it.toInt())) },
            valueRange = 35f..100f, modifier = Modifier.width(120.dp))
    }
    Text("Saved data · no network request. Quote is the stock price time; fetched is the last successful refresh. Widget times use your device time zone; full dates are in Data.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun AddScreen(store: Store, onBack: () -> Unit, onSave: (Quote) -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var quote by remember { mutableStateOf<Quote?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        BackControl(onBack)
        Eyebrow("GROW YOUR SHORTLIST")
    }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuoteTradingDot(q)
                    Text("${q.symbol} · ${q.name}", fontWeight = FontWeight.Bold)
                }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QuoteTradingDot(e.quote)
                        Text(e.symbol, fontWeight = FontWeight.Bold)
                    }
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

@Composable
private fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun QuoteTradingDot(quote: Quote?) {
    val status = tradingStatus(quote, rememberNow())
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    StatusDot(tradingDotColor(status == TradingStatus.ACTIVE, dark), status, 8.dp, 6.dp)
}

@Composable
private fun StatusDot(color: Color, status: TradingStatus, size: Dp, gap: Dp) {
    Box(Modifier.padding(end = gap).size(size).background(color, CircleShape)
        .semantics { contentDescription = status.description })
}

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
    val colors = widgetColors(LocalContext.current, palette)
    val bg = colors.background
    val fg = colors.text
    val muted = colors.muted
    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.secondaryContainer)), RoundedCornerShape(24.dp)).padding(20.dp),
        contentAlignment = Alignment.Center) {
    Column(Modifier.widthIn(max = if (presentation.layout == WidgetLayout.COMPACT) 200.dp else 360.dp)
        .fillMaxWidth().height(if (presentation.layout == WidgetLayout.WATCHLIST) 400.dp else 210.dp)
        .background(bg.copy(alpha = opacity / 100f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 12.dp)) {
        val row = presentation.rows.firstOrNull()
        Column(Modifier.weight(1f)) {
        if (row == null) {
            Text("Your watchlist", color = fg, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
            Text("A little space for your stocks.", color = fg, fontSize = 15.sp, lineHeight = 18.sp)
            Text("Follow a stock in Folio.", color = muted, fontSize = 11.sp, lineHeight = 13.sp)
        }
        else if (presentation.layout == WidgetLayout.WATCHLIST) {
            Text("Your watchlist", color = fg, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
            presentation.rows.forEach { item ->
                val accent = if (item.negative) colors.negative else colors.accent
                Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(colors.tradingDot(item.trading == TradingStatus.ACTIVE), item.trading, 7.dp, 5.dp)
                            Text(item.symbol, color = fg, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(item.name, color = muted, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.width(40.dp)) { WidgetChart(item.points, accent, 24) }
                    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.End) {
                        Text(item.price, color = fg, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1)
                        Text(item.percentChange, color = accent, fontSize = 9.sp, lineHeight = 11.sp)
                    }
                }
                Text(item.quoteTime, fontSize = 9.sp, lineHeight = 11.sp, color = muted, maxLines = 1)
                Text(item.fetchedAt, fontSize = 9.sp, lineHeight = 11.sp, color = muted, maxLines = 1)
                item.status?.let { Text(it, fontSize = 9.sp, lineHeight = 11.sp, color = muted, maxLines = 1) }
                HorizontalDivider(color = fg.copy(alpha = 0.12f))
            }
        } else {
            if (presentation.layout == WidgetLayout.WIDE) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(colors.tradingDot(row.trading == TradingStatus.ACTIVE), row.trading, 7.dp, 5.dp)
                    Text(row.symbol, fontSize = 12.sp, lineHeight = 14.sp, color = fg, fontWeight = FontWeight.Bold)
                    Text(" / ${row.name}", Modifier.weight(1f), fontSize = 10.sp, lineHeight = 12.sp, color = muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    row.exchange?.let { Text(it, fontSize = 9.sp, lineHeight = 11.sp, color = muted, maxLines = 1) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(colors.tradingDot(row.trading == TradingStatus.ACTIVE), row.trading, 7.dp, 5.dp)
                    Text(row.symbol, fontSize = 12.sp, lineHeight = 14.sp, color = fg, fontWeight = FontWeight.Bold)
                }
                Text(row.name, fontSize = 10.sp, lineHeight = 12.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(row.price, fontSize = 28.sp, lineHeight = 32.sp, color = fg, fontWeight = FontWeight.Medium, maxLines = 1)
                        row.currency?.let { Text(" $it", fontSize = 10.sp, lineHeight = 12.sp, color = muted, maxLines = 1) }
                    }
                    Text(row.change, color = if (row.negative) colors.negative else colors.accent, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
                }
                if (presentation.layout == WidgetLayout.WIDE && row.points.size > 1)
                    Box(Modifier.width(110.dp)) { WidgetChart(row.points, if (row.negative) colors.negative else colors.accent, 54) }
            }
            if (presentation.layout == WidgetLayout.COMPACT && row.points.size > 1)
                Box(Modifier.fillMaxWidth()) { WidgetChart(row.points, if (row.negative) colors.negative else colors.accent, 39) }
        }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (presentation.layout != WidgetLayout.WATCHLIST) row?.let {
                    Text(it.quoteTime, fontSize = 9.sp, lineHeight = 11.sp, color = muted, maxLines = 1)
                    Text(it.fetchedAt, fontSize = 9.sp, lineHeight = 11.sp, color = muted, maxLines = 1)
                    it.status?.let { status -> Text(status, fontSize = 9.sp, lineHeight = 11.sp, color = muted, maxLines = 1) }
                }
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                val color = colors.accent
                Image(remember(color) { refreshIcon(color.toArgb()).asImageBitmap() }, contentDescription = "Refresh preview",
                    modifier = Modifier.size(16.dp))
            }
        }
    }
    }
}

@Composable
private fun WidgetChart(points: List<Double>, color: Color, height: Int) {
    if (points.size < 2) return
    val bitmap = remember(points, color) { sparkline(points, color.toArgb()).asImageBitmap() }
    Image(bitmap, contentDescription = "Intraday price chart", contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxWidth().height(height.dp))
}

@Composable
private fun Chart(points: List<Double>, color: Color, height: Int = 45) {
    if (points.size < 2) return
    Canvas(Modifier.fillMaxWidth().height(height.dp)) {
        val fractions = chartFractions(points)
        fun point(i: Int) = Offset(size.width * i / (points.size - 1), size.height * (1 - fractions[i]))
        for (i in 1 until points.size) drawLine(color, point(i - 1), point(i), strokeWidth = 2.dp.toPx())
    }
}
