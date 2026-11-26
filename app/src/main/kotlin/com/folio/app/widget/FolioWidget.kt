package com.folio.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityIntent
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.Spacer
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import com.folio.app.MainActivity
import com.folio.app.market.Refresh
import com.folio.app.market.Store
import com.folio.app.market.TradingStatus
import com.folio.app.market.chartFractions

class FolioWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val store = Store.get(context)
        store.tick()
        provideContent {
            val entries by store.entries.collectAsState()
            val now by store.clock.collectAsState()
            val config by remember(appId) { store.observeWidgetConfig(appId) }.collectAsState(initial = store.widgetConfig(appId))
            val size = LocalSize.current
            val layout = if (size.height >= 320.dp && size.width >= 220.dp) WidgetLayout.WATCHLIST
                else if (size.width >= 220.dp) WidgetLayout.WIDE else WidgetLayout.COMPACT
            WidgetContent(context, appId, presentWidget(config, entries, layout, now), config)
        }
    }
}

class FolioWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FolioWidget()
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Refresh.schedule(context, Store.get(context).getString("interval", "15").toLong())
    }
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Refresh.stop(context)
    }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { Store.get(context).deleteWidgetConfig(it) }
        super.onDeleted(context, appWidgetIds)
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: androidx.glance.action.ActionParameters) {
        Refresh.now(context)
    }
}

@Composable
internal fun WidgetContent(context: Context, appId: Int, presentation: WidgetPresentation, config: WidgetConfig) {
    val colors = widgetColors(context, config.theme)
    val fg = ColorProvider(colors.text)
    val muted = ColorProvider(colors.muted)
    val iconColor = colors.accent.toArgb()
    val background = remember(colors.background, config.opacity) {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor((colors.background.toArgb() and 0x00ffffff) or ((config.opacity * 255 / 100) shl 24))
        }
    }
    val corner = if (android.os.Build.VERSION.SDK_INT >= 31)
        (context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) / context.resources.displayMetrics.density).dp
        else 24.dp
    Box(GlanceModifier.fillMaxSize().cornerRadius(corner)) {
        Image(ImageProvider(background), contentDescription = null, modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds)
        val row = presentation.rows.firstOrNull()
        val horizontalPadding = if (LocalSize.current.width < 180.dp) 12.dp else 16.dp
        Column(GlanceModifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = 12.dp), verticalAlignment = Alignment.Vertical.Top) {
        Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
        if (row == null) {
            Text("Your watchlist", style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold))
            Text("A little space for your stocks.", style = TextStyle(color = fg, fontSize = 15.sp),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()))
            Text("Follow a stock in Folio.", style = TextStyle(color = muted, fontSize = 11.sp))
        } else if (presentation.layout == WidgetLayout.WATCHLIST) {
            Text("Your watchlist", style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()))
            val visibleCount = ((LocalSize.current.height.value - 85) / 76).toInt().coerceIn(1, 4)
            presentation.rows.take(visibleCount).forEach { WidgetRow(context, appId, it, colors) }
        } else {
            val accent = (if (row.negative) colors.negative else colors.accent).toArgb()
            Column(GlanceModifier.fillMaxWidth().clickable(actionStartActivityIntent(detailIntent(context, appId, row.symbol)))) {
                if (presentation.layout == WidgetLayout.WIDE) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                        TradingDot(row, colors)
                        Text(row.symbol, style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(" / ${row.name}", modifier = GlanceModifier.defaultWeight(),
                            style = TextStyle(color = muted, fontSize = 10.sp), maxLines = 1)
                        row.exchange?.let { Text(it, style = TextStyle(color = muted, fontSize = 9.sp), maxLines = 1) }
                    }
                } else {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                        TradingDot(row, colors)
                        Text(row.symbol, style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                    Text(row.name, style = TextStyle(color = muted, fontSize = 10.sp), maxLines = 1)
                }
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        Row(verticalAlignment = Alignment.Vertical.Bottom) {
                            Text(row.price, style = TextStyle(color = fg, fontSize = 28.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                            row.currency?.let {
                                Text(" $it", style = TextStyle(color = muted, fontSize = 10.sp), maxLines = 1)
                            }
                        }
                        Text(row.change, style = TextStyle(color = ColorProvider(androidx.compose.ui.graphics.Color(accent)), fontSize = 10.sp), maxLines = 1)
                    }
                    if (presentation.layout == WidgetLayout.WIDE && row.points.size > 1)
                        Image(ImageProvider(sparkline(row.points, accent)),
                            contentDescription = "Intraday price chart", modifier = GlanceModifier.width(110.dp).height(54.dp))
                }
                if (presentation.layout == WidgetLayout.COMPACT && row.points.size > 1)
                    Image(ImageProvider(sparkline(row.points, accent)), contentDescription = "Intraday price chart",
                        modifier = GlanceModifier.fillMaxWidth().height(if (LocalSize.current.height >= 200.dp) 39.dp else 24.dp),
                        contentScale = ContentScale.FillBounds)
            }
        }
        }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                if (presentation.layout != WidgetLayout.WATCHLIST) {
                    row?.let {
                        Text(it.quoteTime, style = TextStyle(color = muted, fontSize = 9.sp), maxLines = 1)
                        Text(it.fetchedAt, style = TextStyle(color = muted, fontSize = 9.sp), maxLines = 1)
                        it.status?.let { status -> Text(status, style = TextStyle(color = muted, fontSize = 9.sp), maxLines = 1) }
                    }
                }
            }
            Box(GlanceModifier.width(48.dp).height(48.dp).clickable(actionRunCallback<RefreshAction>()),
                contentAlignment = Alignment.Center) {
                Image(ImageProvider(refreshIcon(iconColor)), contentDescription = "Refresh", modifier = GlanceModifier.width(16.dp).height(16.dp))
            }
        }
        }
    }
}

@Composable
private fun WidgetRow(context: Context, appId: Int, row: WidgetRowData, colors: WidgetColors) {
    val fg = ColorProvider(colors.text)
    val muted = ColorProvider(colors.muted)
    val accent = (if (row.negative) colors.negative else colors.accent).toArgb()
    Column(GlanceModifier.fillMaxWidth().clickable(actionStartActivityIntent(detailIntent(context, appId, row.symbol))).padding(vertical = 3.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    TradingDot(row, colors)
                    Text(row.symbol, style = TextStyle(color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
                Text(row.name, style = TextStyle(color = muted, fontSize = 9.sp), maxLines = 1)
            }
            if (row.points.size > 1)
                Image(ImageProvider(sparkline(row.points, accent)), contentDescription = "Intraday price chart",
                    modifier = GlanceModifier.width(40.dp).height(24.dp))
            Column(GlanceModifier.width(72.dp), horizontalAlignment = Alignment.Horizontal.End) {
                Text(row.price, style = TextStyle(color = fg, fontSize = 12.sp), maxLines = 1)
                Text(row.percentChange, style = TextStyle(color = ColorProvider(androidx.compose.ui.graphics.Color(accent)), fontSize = 9.sp), maxLines = 1)
            }
        }
        Text(row.quoteTime, style = TextStyle(color = muted, fontSize = 9.sp), maxLines = 1)
        Text(row.fetchedAt, style = TextStyle(color = muted, fontSize = 9.sp), maxLines = 1)
        row.status?.let { Text(it, style = TextStyle(color = muted, fontSize = 9.sp), maxLines = 1) }
        Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(colors.divider)))
    }
}

@Composable
private fun TradingDot(row: WidgetRowData, colors: WidgetColors) {
    val color = colors.tradingDot(row.trading == TradingStatus.ACTIVE).toArgb()
    Image(ImageProvider(statusDot(color)), contentDescription = row.trading.description,
        modifier = GlanceModifier.width(7.dp).height(7.dp))
    Spacer(GlanceModifier.width(5.dp))
}

internal fun statusDot(color: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawCircle(14f, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    return bitmap
}

internal fun refreshIcon(color: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawArc(8f, 8f, 40f, 40f, 35f, 290f, false, paint)
    canvas.drawLine(40f, 5f, 40f, 16f, paint)
    canvas.drawLine(40f, 16f, 29f, 16f, paint)
    return bitmap
}

private fun detailIntent(context: Context, appId: Int, symbol: String): Intent =
    Intent(context, MainActivity::class.java).setData(Uri.parse("folio://widget/$appId/stock/${Uri.encode(symbol)}"))
        .putExtra("symbol", symbol).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

fun sparkline(points: List<Double>, color: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(240, 56, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 3f }
    val fractions = chartFractions(points)
    for (i in 1 until points.size) {
        fun x(index: Int) = index * 239f / (points.size - 1)
        fun y(index: Int) = 53f - fractions[index] * 50f
        canvas.drawLine(x(i - 1), y(i - 1), x(i), y(i), paint)
    }
    return bitmap
}
