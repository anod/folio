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
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.folio.app.MainActivity
import com.folio.app.market.Refresh
import com.folio.app.market.Store

class FolioWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val store = Store.get(context)
        provideContent {
            val entries by store.entries.collectAsState()
            val config by remember(appId) { store.observeWidgetConfig(appId) }.collectAsState(initial = store.widgetConfig(appId))
            val size = LocalSize.current
            val layout = if (size.height >= 320.dp && size.width >= 220.dp) WidgetLayout.WATCHLIST
                else if (size.width >= 220.dp) WidgetLayout.WIDE else WidgetLayout.COMPACT
            WidgetContent(context, appId, presentWidget(config, entries, layout), config)
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

private val lightText = ColorProvider(androidx.compose.ui.graphics.Color(0xfff1f6e8))
private val darkText = ColorProvider(androidx.compose.ui.graphics.Color(0xff263723))

@Composable
internal fun WidgetContent(context: Context, appId: Int, presentation: WidgetPresentation, config: WidgetConfig) {
    val light = config.theme != "Graphite"
    val fg = if (light) darkText else lightText
    val bg = if (config.theme == "Sage") 0xffe2edcf else if (light) 0xfff5f3eb else 0xff1b2923
    val configIntent = Intent(context, WidgetConfigActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .setData(Uri.parse("folio://widget/$appId/config"))
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appId)
    val background = remember(config.theme, config.opacity) {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor((bg.toInt() and 0x00ffffff) or ((config.opacity * 255 / 100) shl 24))
        }
    }
    val corner = if (android.os.Build.VERSION.SDK_INT >= 31)
        (context.resources.getDimension(android.R.dimen.system_app_widget_background_radius) / context.resources.displayMetrics.density).dp
        else 24.dp
    Box(GlanceModifier.fillMaxSize().cornerRadius(corner)) {
        Image(ImageProvider(background), contentDescription = null, modifier = GlanceModifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds)
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Vertical.Top) {
        Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
        val row = presentation.rows.firstOrNull()
        if (row == null) {
            Text("Folio", style = TextStyle(color = fg, fontSize = 12.sp))
            Text("A little space for your stocks.", style = TextStyle(color = fg, fontSize = 15.sp),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()))
            Text("Follow a stock in Folio.", style = TextStyle(color = fg, fontSize = 12.sp))
        } else if (presentation.layout == WidgetLayout.WATCHLIST) {
            Text("Folio · Your watchlist", style = TextStyle(color = fg, fontSize = 15.sp),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()))
            val visibleCount = ((LocalSize.current.height.value - 80) / 90).toInt().coerceIn(1, 4)
            presentation.rows.take(visibleCount).forEach { WidgetRow(context, appId, it, fg, light) }
        } else {
            val accent = marketAccent(row, light)
            Column(GlanceModifier.fillMaxWidth().clickable(actionStartActivityIntent(detailIntent(context, appId, row.symbol)))) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text("Folio · ${row.symbol}", modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = fg, fontSize = 12.sp), maxLines = 1)
                    if (presentation.layout == WidgetLayout.COMPACT && row.points.size > 1)
                        Image(ImageProvider(sparkline(row.points, accent)),
                            contentDescription = "Intraday price chart", modifier = GlanceModifier.width(32.dp).height(16.dp))
                }
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Column(GlanceModifier.defaultWeight()) {
                        if (presentation.layout == WidgetLayout.WIDE && LocalSize.current.height >= 220.dp)
                            Text(row.name, style = TextStyle(color = fg, fontSize = 12.sp), maxLines = 1)
                        Text(row.price, style = TextStyle(color = fg, fontSize = 22.sp), maxLines = 1)
                        Text(row.change, style = TextStyle(color = ColorProvider(androidx.compose.ui.graphics.Color(accent)), fontSize = 12.sp), maxLines = 1)
                    }
                    if (presentation.layout == WidgetLayout.WIDE && row.points.size > 1)
                        Image(ImageProvider(sparkline(row.points, accent)),
                            contentDescription = "Intraday price chart", modifier = GlanceModifier.width(90.dp).height(40.dp))
                }
            }
            Text(row.quoteTime, style = TextStyle(color = fg, fontSize = 11.sp), maxLines = 2)
            Text(row.fetchedAt, style = TextStyle(color = fg, fontSize = 11.sp), maxLines = 2)
            row.status?.let { Text(it, style = TextStyle(color = fg, fontSize = 11.sp), maxLines = 1) }
            if (LocalSize.current.height >= 250.dp)
                row.lastAttempt?.let { Text(it, style = TextStyle(color = fg, fontSize = 11.sp), maxLines = 2) }
        }
        }
        Row(GlanceModifier.fillMaxWidth()) {
            Box(GlanceModifier.defaultWeight().height(48.dp).clickable(actionRunCallback<RefreshAction>()),
                contentAlignment = Alignment.CenterStart) { Text("Refresh", style = TextStyle(color = fg, fontSize = 12.sp), maxLines = 1) }
            Box(GlanceModifier.defaultWeight().height(48.dp).clickable(actionStartActivityIntent(configIntent)),
                contentAlignment = Alignment.CenterEnd) { Text("Settings", style = TextStyle(color = fg, fontSize = 12.sp)) }
        }
        }
    }
}

@Composable
private fun WidgetRow(context: Context, appId: Int, row: WidgetRowData, fg: ColorProvider, light: Boolean) {
    val accent = marketAccent(row, light)
    Column(GlanceModifier.fillMaxWidth().clickable(actionStartActivityIntent(detailIntent(context, appId, row.symbol))).padding(vertical = 3.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Row {
                    Text(row.symbol, modifier = GlanceModifier.width(65.dp), style = TextStyle(color = fg, fontSize = 12.sp))
                    Text(row.price, style = TextStyle(color = fg, fontSize = 12.sp), maxLines = 1)
                }
                Text(row.change, style = TextStyle(color = ColorProvider(androidx.compose.ui.graphics.Color(accent)), fontSize = 11.sp), maxLines = 1)
            }
            if (row.points.size > 1)
                Image(ImageProvider(sparkline(row.points, accent)), contentDescription = "Intraday price chart",
                    modifier = GlanceModifier.width(40.dp).height(24.dp))
        }
        Text(row.quoteTime, style = TextStyle(color = fg, fontSize = 11.sp), maxLines = 1)
        Text(row.fetchedAt, style = TextStyle(color = fg, fontSize = 11.sp), maxLines = 1)
        row.status?.let { Text(it, style = TextStyle(color = fg, fontSize = 11.sp), maxLines = 1) }
    }
}

private fun marketAccent(row: WidgetRowData, light: Boolean): Int =
    if (row.negative) { if (light) 0xffa65a47.toInt() else 0xffe2a38e.toInt() }
    else { if (light) 0xff506e37.toInt() else 0xffc9ef9a.toInt() }

private fun detailIntent(context: Context, appId: Int, symbol: String): Intent =
    Intent(context, MainActivity::class.java).setData(Uri.parse("folio://widget/$appId/stock/${Uri.encode(symbol)}"))
        .putExtra("symbol", symbol).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

fun sparkline(points: List<Double>, color: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(240, 56, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 3f }
    val min = points.min()
    val range = (points.max() - min).takeIf { it > 0 } ?: 1.0
    for (i in 1 until points.size) {
        fun x(index: Int) = index * 239f / (points.size - 1)
        fun y(index: Int) = 53f - ((points[index] - min) / range * 50).toFloat()
        canvas.drawLine(x(i - 1), y(i - 1), x(i), y(i), paint)
    }
    return bitmap
}
