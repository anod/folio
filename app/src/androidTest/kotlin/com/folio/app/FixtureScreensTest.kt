package com.folio.app

import android.appwidget.AppWidgetManager
import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.folio.app.market.MarketDataProvider
import com.folio.app.market.MarketException
import com.folio.app.market.Quote
import com.folio.app.market.ChartRange
import com.folio.app.market.ChartSeries
import com.folio.app.market.Store
import com.folio.app.market.dateTime
import com.folio.app.market.Refresh
import androidx.work.WorkManager
import androidx.core.view.WindowCompat
import java.util.concurrent.TimeUnit
import com.folio.app.ui.FolioApp
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FixtureScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private companion object {
        val automation by lazy { InstrumentationRegistry.getInstrumentation().uiAutomation }
    }
    private val context = instrumentation.targetContext
    private val preferenceName = "fixture-screen-test"
    private val quote = Quote(
        "FIXT", "Fixture Company", "USD", "TEST", "UTC", 123.45, 120.0,
        1735686000000, 1735689600000, listOf(120.0, 121.0, 123.45),
        pointTimes = listOf(1735655400000, 1735657200000, 1735678800000),
    )
    private var failRefresh = false
    private var failHistory = false
    private var historyRateLimited = false
    private var historyCalls = 0
    private var historyGate: CompletableDeferred<Unit>? = null
    private val provider = object : MarketDataProvider {
        override suspend fun fetch(symbol: String): Quote {
            if (failRefresh) throw MarketException("Simulated network outage")
            check(symbol == quote.symbol)
            return quote
        }
        override suspend fun fetchHistory(symbol: String, range: ChartRange): ChartSeries {
            historyCalls++
            check(symbol == quote.symbol)
            historyGate?.await()
            if (historyRateLimited) throw MarketException("History rate limited", true, System.currentTimeMillis() + 60_000, true)
            if (failHistory) throw MarketException("History unavailable")
            return ChartSeries(listOf(110.0, 115.0, 120.0),
                listOf(1735516800000, 1735603200000, 1735689600000), "UTC", 1735693200000)
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
        val reviewNoHeader = InstrumentationRegistry.getArguments().getString("reviewNoHeader") == "true"
        if (reviewNoHeader) {
            compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                if (reviewNoHeader) {
                    FolioApp(providedStore = store)
                } else Column(Modifier.fillMaxSize().safeDrawingPadding()) {
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
        compose.onNodeWithText("123.45").assertExists().assertWidthIsAtLeast(70.dp)
        screenshot("preview")
        compose.onNodeWithText("Wide").performClick()
        screenshot("dark-wide")
        compose.onAllNodesWithText("Watchlist").onFirst().performClick()
        screenshot("dark-tall")
        compose.onNodeWithText("Compact").performClick()
        compose.onNodeWithText("Data").performClick()
        screenshot("dark-data")
        compose.onNode(hasText("Settings") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
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
        compose.runOnIdle {
            val window = compose.activity.window
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            check(!bars.isAppearanceLightStatusBars && !bars.isAppearanceLightNavigationBars)
        }
        screenshot("dark-welcome")
        compose.onNodeWithText("Explore the widget preview").performClick()
        compose.onNode(hasText("Watchlist") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
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
        compose.runOnIdle {
            val window = compose.activity.window
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            check(bars.isAppearanceLightStatusBars && bars.isAppearanceLightNavigationBars)
        }
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
        compose.onNode(hasText("Settings") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        screenshot("light-settings")
    }

    @Test fun widgetDesignerSelectsStocksAndAppearance() {
        runBlocking {
            store.add(quote)
            store.add(quote.copy(symbol = "DEMO", name = "Example Labs"))
        }
        showApp()
        compose.onNodeWithText("Widgets").performClick()
        compose.onNodeWithText("FIXT · Change").performScrollTo().performClick()
        compose.onNodeWithText("Choose and order stocks · first is featured").assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Sage widget theme").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Sage widget theme, selected").assertExists()
        compose.onNodeWithText("Quote: ${com.folio.app.widget.widgetDateTime(quote.quoteTime)}").assertExists()
        compose.onNodeWithText("Fetched: ${com.folio.app.widget.widgetDateTime(quote.fetchedAt)}").assertExists()
    }

    @Test fun detailBackAndSettingsPillsKeepSelectionAccessible() {
        store.putString("appTheme", "Dark")
        runBlocking { store.add(quote) }
        showApp()
        compose.onNodeWithText("FIXT").performClick()
        compose.onNodeWithContentDescription("Price chart").assertExists()
        compose.onNodeWithText("14:30").assertExists()
        compose.onNodeWithText("21:00").assertExists()
        compose.onNodeWithText("Chart time · UTC").assertExists()
        compose.onNode(hasText("Watchlist") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertIsSelected()
        compose.onNodeWithContentDescription("Back to watchlist").performClick()
        compose.onNodeWithText("A little clarity.").assertExists()
        compose.onNode(hasText("Settings") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.onNodeWithText("Dark").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Light").assertIsNotSelected().performClick()
        compose.onNodeWithText("Light").assertIsSelected()
        compose.onNodeWithText("Dark").assertIsNotSelected()
        check(store.getString("appTheme", "System") == "Light")
    }

    @Test fun chartRangesKeepTheDailyQuoteAndRecoverFromHistoryFailure() {
        runBlocking { store.add(quote) }
        showApp()
        compose.onNodeWithText("FIXT").performClick()
        compose.onNodeWithText("1W").performClick()
        compose.waitUntil(5_000) { historyCalls == 1 }
        compose.onNodeWithText("1W").assertIsSelected()
        compose.onNodeWithText("1W · 5 trading days").assertExists()
        compose.onNodeWithText("Chart time · UTC").assertExists()
        compose.onNodeWithText("1M").performClick()
        compose.waitUntil(5_000) { historyCalls == 2 }
        compose.onNodeWithText("1W").performClick()
        compose.onNodeWithText("1W · 5 trading days").assertExists()
        check(historyCalls == 2)
        failHistory = true
        compose.onNodeWithText("3M").performClick()
        compose.waitUntil(5_000) { historyCalls == 3 }
        compose.onNodeWithText("History unavailable").assertExists()
        failHistory = false
        compose.onNodeWithText("Retry chart").performClick()
        compose.waitUntil(5_000) { historyCalls == 4 }
        compose.onNodeWithText("3M · 3 months").assertExists()
        compose.onNodeWithText("1D").performClick()
        compose.onNodeWithText("1D · Intraday").assertExists()
        check(store.entries.value.single().quote == quote)
    }

    @Test fun historyRateLimitAlsoBlocksQuoteRequestsWithoutChangingSavedData() {
        runBlocking {
            store.add(quote)
            historyRateLimited = true
            try {
                store.chartHistory("FIXT", ChartRange.WEEK)
                error("Expected a provider rate limit")
            } catch (e: MarketException) {
                check(e.rateLimited)
            }

            try {
                store.lookup("FIXT")
                error("Expected the shared provider rate limit")
            } catch (e: MarketException) {
                check(e.rateLimited)
            }
            check(store.entries.value.single().quote == quote)
            check(historyCalls == 1)
        }
    }

    @Test fun chartRangeDoesNotMoveDetailsDuringLoading() = checkChartLayoutStability(1f)

    @Test fun chartRangeDoesNotMoveDetailsAtLargeFont() = checkChartLayoutStability(1.5f)

    private fun checkChartLayoutStability(fontScale: Float) {
        runBlocking { store.add(quote) }
        showApp(fontScale)
        compose.onNodeWithText("FIXT").performClick()
        val initial = compose.onNodeWithText("Previous close").fetchSemanticsNode().positionInRoot.y
        val gate = CompletableDeferred<Unit>()
        historyGate = gate
        compose.onNodeWithText("1W").performClick()
        compose.waitUntil(5_000) { historyCalls == 1 }
        compose.onNodeWithText("Loading 1W chart…").assertExists()
        val loading = compose.onNodeWithText("Previous close").fetchSemanticsNode().positionInRoot.y
        gate.complete(Unit)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Chart time · UTC").fetchSemanticsNodes().isNotEmpty() }
        val loaded = compose.onNodeWithText("Previous close").fetchSemanticsNode().positionInRoot.y
        check(initial == loading && initial == loaded) { "Detail moved: $initial -> $loading -> $loaded" }
    }

    @Test fun tradingDotsDistinguishActiveFromInactiveEverywhere() {
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        val live = quote.copy(symbol = "LIVE", name = "Open Session Fixture", quoteTime = now - 60_000, fetchedAt = now - 60_000,
            regularStart = now - hour, regularEnd = now + hour)
        val shut = quote.copy(symbol = "REST", name = "Closed Session Fixture", price = 118.25, points = listOf(120.0, 119.0, 118.25),
            quoteTime = now - 4 * hour, fetchedAt = now - 30 * 60_000, regularStart = now - 10 * hour, regularEnd = now - 3 * hour)
        store.putString("appTheme", InstrumentationRegistry.getArguments().getString("tradingTheme") ?: "Dark")
        runBlocking { store.add(live); store.add(shut) }
        showApp()
        fun count(description: String) =
            compose.onAllNodesWithContentDescription(description, useUnmergedTree = true).fetchSemanticsNodes().size
        compose.waitUntil(5_000) { count("Trading now") >= 1 && count("Not trading now") >= 1 }
        screenshot("trading-watchlist")
        compose.onNodeWithText("LIVE").performClick()
        compose.onNodeWithText("Open Session Fixture").assertExists()
        check(count("Trading now") >= 1)
        screenshot("trading-detail")
        compose.onNodeWithText("Widgets").performClick()
        compose.onAllNodesWithText("Watchlist").onFirst().performClick()
        compose.waitUntil(5_000) { count("Trading now") >= 1 && count("Not trading now") >= 1 }
        screenshot("trading-preview")
        compose.onNodeWithText("Data").performClick()
        compose.waitUntil(5_000) { count("Trading now") >= 1 && count("Not trading now") >= 1 }
        screenshot("trading-data")
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
        compose.onNodeWithText("Illustrative widget preview").assertExists()
        compose.onNodeWithText("Clear prices. Honest timestamps.").assertExists()
        compose.onNodeWithText("Widgets").assertDoesNotExist()
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
        compose.onNodeWithText("Explore the widget preview").performClick()
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
