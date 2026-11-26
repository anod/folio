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
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.folio.app.market.Refresh
import com.folio.app.market.Store
import com.folio.app.market.scheduleTradingStatus
import com.folio.app.ui.theme.FolioTheme
import com.folio.app.ui.WidgetDesigner
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
            val appTheme = remember { store.getString("appTheme", "System") }
            val dark = when (appTheme) {
                "Dark" -> true
                "Light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            FolioTheme(darkTheme = dark) {
                val entries by store.entries.collectAsState()
                val initial = remember {
                    if (store.hasWidgetConfig(id)) store.widgetConfig(id)
                    else WidgetConfig.decode(store.getString("draft.config", WidgetConfig(entries.map { it.symbol }).encode()))
                }
                var config by remember { mutableStateOf(initial) }
                val options = remember { AppWidgetManager.getInstance(this).getAppWidgetOptions(id) }
                var size by remember {
                    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    mutableStateOf(if (height >= 320 && width >= 220) "Watchlist" else if (width >= 220) "Wide" else "Compact")
                }
                val scope = rememberCoroutineScope()
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("YOUR HOME SCREEN, YOUR WAY", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary)
                    Text("Make it yours.", style = MaterialTheme.typography.headlineMedium)
                    Text("Preview saved values for this widget. Resize it on your home screen.")
                    WidgetDesigner(entries, config, size, onSizeChange = { size = it }, onConfigChange = { config = it })
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        store.saveWidgetConfig(id, config)
                        scope.launch {
                            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(id)
                            FolioWidget().update(this@WidgetConfigActivity, glanceId)
                            scheduleTradingStatus(this@WidgetConfigActivity)
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
}
