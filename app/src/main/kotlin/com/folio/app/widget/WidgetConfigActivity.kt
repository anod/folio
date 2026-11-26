package com.folio.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.folio.app.market.Refresh
import com.folio.app.market.Store
import com.folio.app.ui.theme.FolioTheme
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED)
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || provider != ComponentName(this, FolioWidgetReceiver::class.java)) {
            finish()
            return
        }
        val store = Store.get(this)
        setContent {
            FolioTheme {
                val entries by store.entries.collectAsState()
                val initial = remember {
                    if (store.hasWidgetConfig(id)) store.widgetConfig(id)
                    else WidgetConfig.decode(store.getString("draft.config", WidgetConfig(entries.map { it.symbol }).encode()))
                }
                var symbols by remember { mutableStateOf(initial.symbols) }
                var theme by remember { mutableStateOf(initial.theme) }
                var opacity by remember { mutableFloatStateOf(initial.opacity.toFloat()) }
                val scope = rememberCoroutineScope()
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Make it yours.", style = MaterialTheme.typography.headlineMedium)
                    Text("Choose and order stocks for this widget. The first is featured in compact and wide layouts.")
                    if (entries.isEmpty()) Text("Add a stock in Folio first. You can still add an empty widget.")
                    val available = entries.map { it.symbol }
                    (symbols.filter { it in available } + available.filterNot { it in symbols }).forEach { symbol ->
                        Row {
                            Checkbox(checked = symbol in symbols, onCheckedChange = { checked ->
                                symbols = if (checked) symbols + symbol else symbols - symbol
                            })
                            Text(symbol, Modifier.weight(1f).padding(top = 12.dp))
                            TextButton(enabled = symbol in symbols, onClick = { symbols = moveSymbol(symbols, symbol, -1) }) { Text("↑") }
                            TextButton(enabled = symbol in symbols, onClick = { symbols = moveSymbol(symbols, symbol, 1) }) { Text("↓") }
                        }
                    }
                    Row {
                        listOf("Graphite", "Sage", "Paper").forEach { value ->
                            TextButton(onClick = { theme = value }) { Text(if (theme == value) "✓ $value" else value) }
                        }
                    }
                    Text("Background opacity: ${opacity.toInt()}%")
                    Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 35f..100f)
                    Button(onClick = {
                        store.saveWidgetConfig(id, WidgetConfig(symbols, theme, opacity.toInt()))
                        scope.launch {
                            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(id)
                            FolioWidget().update(this@WidgetConfigActivity, glanceId)
                            Refresh.schedule(this@WidgetConfigActivity, store.getString("interval", "15").toLong())
                            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                            finish()
                        }
                    }) { Text("Save widget") }
                }
            }
        }
    }
}
