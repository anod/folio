package com.folio.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.folio.app.market.Entry
import com.folio.app.market.Quote
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val quote = Quote("FIXT", "Fixture Company", "USD", "TEST", "UTC", 123.45, 120.0,
        1735686000000, 1735689600000, listOf(120.0, 121.0, 123.45))
    private val entry = Entry("FIXT", quote, 1735693200000, "Simulated outage")

    @Test fun compactWidgetKeepsBothTimesAndActionsVisible() = render(DpSize(180.dp, 210.dp), WidgetLayout.COMPACT, "compact")
    @Test fun wideWidgetKeepsBothTimesAndActionsVisible() = render(DpSize(350.dp, 210.dp), WidgetLayout.WIDE, "wide")
    @Test fun tallWidgetKeepsBothTimesAndActionsVisible() = render(DpSize(350.dp, 400.dp), WidgetLayout.WATCHLIST, "tall")

    @OptIn(androidx.glance.ExperimentalGlanceApi::class)
    private fun render(size: DpSize, layout: WidgetLayout, name: String) {
        val entries = if (layout == WidgetLayout.WATCHLIST) listOf(
            entry,
            entry.copy(symbol = "DEMO", quote = quote.copy(symbol = "DEMO", price = 118.25, points = listOf(120.0, 119.0, 118.25))),
            entry.copy(symbol = "SAMP", quote = quote.copy(symbol = "SAMP", price = 127.60, points = listOf(120.0, 124.0, 127.60))),
        ) else listOf(entry)
        val config = WidgetConfig(entries.map { it.symbol }, "Sage")
        val presentation = presentWidget(config, entries, layout)
        val widget = object : GlanceAppWidget() {
            override suspend fun provideGlance(context: Context, id: GlanceId) {
                provideContent { WidgetContent(context, 0, presentation, config) }
            }
        }
        val views = runBlocking { widget.compose(context, size = size) }
        lateinit var host: FrameLayout
        composeRule.setContent {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Text("TEST DATA · production Glance layout")
                AndroidView(modifier = Modifier.size(size.width, size.height), factory = { hostContext ->
                    FrameLayout(hostContext).also {
                        host = it
                        it.addView(views.apply(hostContext, it))
                    }
                })
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            fun texts(view: View): List<TextView> =
                if (view is TextView) listOf(view)
                else if (view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                else emptyList()
            val nodes = texts(host)
            for (prefix in listOf("Folio", "123.45", "Stock price:", "Refreshed:", "Refresh", "Settings")) {
                val matches = nodes.filter { if (prefix in listOf("Refresh", "Settings")) it.text.toString() == prefix else it.text.startsWith(prefix) }
                check(matches.size == if (prefix in listOf("Stock price:", "Refreshed:")) entries.size else 1)
                matches.forEach { node ->
                    val rect = android.graphics.Rect()
                    check(node.getLocalVisibleRect(rect) && rect.height() == node.height) { "$name: $prefix is clipped" }
                    val textLayout = checkNotNull(node.layout)
                    check((0 until textLayout.lineCount).all { textLayout.getEllipsisCount(it) == 0 }) { "$name: $prefix is truncated" }
                    if (prefix in listOf("Refresh", "Settings")) check(textLayout.lineCount == 1) { "$name: $prefix wraps" }
                }
            }
        }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            File(context.cacheDir, "fixture-widget-$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
