package com.folio.app

import android.appwidget.AppWidgetManager
import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.folio.app.market.MarketDataProvider
import com.folio.app.market.MarketException
import com.folio.app.market.Quote
import com.folio.app.market.Store
import com.folio.app.market.dateTime
import com.folio.app.market.Refresh
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.folio.app.ui.FolioApp
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixtureScreensTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private companion object {
        val automation by lazy { InstrumentationRegistry.getInstrumentation().uiAutomation }
    }
    private val context = instrumentation.targetContext
    private val preferenceName = "fixture-screen-test"
    private val quote = Quote(
        "FIXT", "Fixture Company", "USD", "TEST", "UTC", 123.45, 120.0,
        1735686000000, 1735689600000, listOf(120.0, 121.0, 123.45),
    )
    private var failRefresh = false
    private val provider = object : MarketDataProvider {
        override suspend fun fetch(symbol: String): Quote {
            if (failRefresh) throw MarketException("Simulated network outage")
            check(symbol == quote.symbol)
            return quote
        }
    }
    private lateinit var store: Store

    @Before fun setUp() {
        context.deleteSharedPreferences(preferenceName)
        store = Store(context, provider, preferenceName)
    }

    @After fun tearDown() {
        context.deleteSharedPreferences(preferenceName)
    }

    private fun showApp(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Text("TEST DATA · simulated provider")
                Box(Modifier.weight(1f)) { FolioApp(providedStore = store) }
            }
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = automation.takeScreenshot()
        try {
            File(context.cacheDir, "fixture-$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun populatedScreensPreserveQuoteAndShowFailedRefresh() {
        store.putString("appTheme", "Dark")
        runBlocking { store.add(store.lookup("FIXT")) }
        showApp()
        compose.onNodeWithText("FIXT").assertExists()
        screenshot("watchlist")

        compose.onNodeWithText("FIXT").performClick()
        compose.onNodeWithText("Fixture Company").assertExists()
        screenshot("dark-detail")
        compose.onNodeWithText("Stock price time: ${dateTime(quote.quoteTime)}").assertExists()
        screenshot("detail")

        compose.onNodeWithText("Widgets").performClick()
        compose.onNodeWithText("Fixture Company").assertExists()
        compose.onNodeWithText("123.45 USD").assertExists()
        screenshot("preview")
        compose.onNodeWithText("Wide").performClick()
        screenshot("dark-wide")
        compose.onAllNodesWithText("Watchlist").onFirst().performClick()
        screenshot("dark-tall")
        compose.onNodeWithText("Compact").performClick()
        compose.onNodeWithText("Data").performClick()
        screenshot("dark-data")
        compose.onNodeWithText("Settings").performClick()
        screenshot("dark-settings")
        compose.onNodeWithText("Widgets").performClick()

        failRefresh = true
        val result = runBlocking { store.refresh() }
        check(result.attempted == 1 && result.successes == 0)
        check(store.entries.value.single().quote == quote)
        compose.onNodeWithText("Cached · Simulated network outage").assertExists()
        screenshot("preview-error")

        compose.onNodeWithText("Data").performClick()
        compose.onNodeWithText("Last successful refresh: ${dateTime(quote.fetchedAt)}").performScrollTo().assertExists()
        val attemptedAt = store.entries.value.single().attemptedAt
        compose.onNodeWithText("Last refresh attempt: ${dateTime(attemptedAt)}")
            .performScrollTo().assertExists()
        compose.onNodeWithText("Simulated network outage").assertExists()
        screenshot("data-error")
    }

    @Test fun emptyDataOffersFollowAction() {
        store.putString("appTheme", "Dark")
        showApp()
        screenshot("dark-welcome")
        compose.onNodeWithText("Watchlist").performClick()
        screenshot("empty-watchlist")
        compose.onNodeWithText("Data").performClick()
        screenshot("empty-data")
        compose.onNodeWithText("Follow a stock").performScrollTo().performClick()
        compose.onNodeWithText("Find your next perspective.").assertExists()
        screenshot("dark-add-empty")
    }

    @Test fun lightDesignAndAllPreviewLayouts() {
        store.putString("appTheme", "Light")
        runBlocking {
            store.add(quote)
            store.add(quote.copy(symbol = "DEMO", name = "Example Labs", price = 118.25, points = listOf(120.0, 119.0, 118.25)))
            store.add(quote.copy(symbol = "SAMP", name = "Sample Industries", price = 127.6))
        }
        showApp()
        screenshot("light-watchlist")
        compose.onNodeWithText("FIXT").performClick()
        screenshot("light-detail")
        compose.onNodeWithText("Data").performClick()
        check(compose.onAllNodesWithText("Stock price time: ${dateTime(quote.quoteTime)}").fetchSemanticsNodes().size == 3)
        screenshot("light-data")
        compose.onNodeWithText("Widgets").performClick()
        screenshot("light-compact")
        compose.onNodeWithText("Wide").performClick()
        screenshot("light-wide")
        compose.onNodeWithText("Wide").assertExists()
        compose.onAllNodesWithText("Watchlist").onFirst().performClick()
        screenshot("light-tall")
        compose.onNodeWithText("Add to home screen ↗").performScrollTo()
        screenshot("light-widget-controls")
        compose.onNodeWithText("Settings").performClick()
        screenshot("light-settings")
    }

    @Test fun largeFontKeepsRefreshTimesAccessible() {
        runBlocking { store.add(quote) }
        showApp(fontScale = 1.5f)
        compose.onNodeWithText("FIXT").performScrollTo().performClick()
        compose.onNodeWithText("Stock price time: ${dateTime(quote.quoteTime)}").performScrollTo().assertExists()
        compose.onNodeWithText("Last successful refresh: ${dateTime(quote.fetchedAt)}").performScrollTo().assertExists()
        screenshot("large-font-detail")
    }

    @Test fun exactLookupCanBeSavedAndReopenedWithoutTouchingProductionCache() {
        store.putString("appTheme", "Light")
        showApp()
        screenshot("light-welcome")
        compose.onNodeWithText("Choose your first stock").performClick()
        screenshot("light-add-empty")
        compose.onNodeWithText("Symbol").performTextInput("FIXT")
        compose.onNodeWithText("Look up").performClick()
        compose.onNodeWithText("+ Add to watchlist").performScrollTo()
        screenshot("light-add-verified")
        compose.onNodeWithText("+ Add to watchlist").performClick()
        compose.onNodeWithText("FIXT").assertExists()
        check(Store(context, provider, preferenceName).entries.value.single().quote == quote)
    }

    @Test fun stoppingWidgetRefreshAlsoCancelsPendingRetry() {
        val manager = WorkManager.getInstance(context)
        try {
            Refresh.retry(context)
            check(manager.getWorkInfosForUniqueWork("folio-retry").get(5, TimeUnit.SECONDS).any { !it.state.isFinished })
            Refresh.stop(context)
            compose.waitUntil(5_000) {
                manager.getWorkInfosForUniqueWork("folio-retry").get(5, TimeUnit.SECONDS).all { it.state.isFinished }
            }
        } finally {
            Refresh.stop(context)
        }
    }

    @Test fun pinFeedbackDoesNotFollowNavigation() {
        assumeTrue(AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported)
        showApp()
        compose.onNodeWithText("Widgets").performClick()
        compose.onNodeWithText("Add to home screen ↗").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            automation.rootInActiveWindow?.packageName?.toString()?.let { it != context.packageName } == true
        }
        check(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitUntil(5_000) {
            automation.rootInActiveWindow?.packageName?.toString() == context.packageName
        }
        compose.onNodeWithText("Confirm placement in your launcher.").assertExists()
        compose.onNodeWithText("Data").performClick()
        compose.onNodeWithText("Confirm placement in your launcher.").assertDoesNotExist()
        compose.onNodeWithText("Widgets").performClick()
        compose.onNodeWithText("Confirm placement in your launcher.").assertDoesNotExist()
    }
}
