package com.folio.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.folio.app.widget.widgetColors
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {
    @Test fun launcherIconsHaveAVisibleThemedGlyph() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (resource in listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)) {
            val icon = context.getDrawable(resource)
            check(icon is AdaptiveIconDrawable)
            if (Build.VERSION.SDK_INT >= 33) {
                val monochrome = checkNotNull(icon.monochrome)
                val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
                try {
                    monochrome.setBounds(0, 0, bitmap.width, bitmap.height)
                    monochrome.draw(Canvas(bitmap))
                    check((0 until bitmap.height).any { y ->
                        (0 until bitmap.width).any { x -> bitmap.getPixel(x, y) ushr 24 != 0 }
                    })
                    check(bitmap.getPixel(0, 0) ushr 24 == 0)
                } finally {
                    bitmap.recycle()
                }

            }
        }
    }

    @Test fun systemWidgetPaletteTracksLightAndDarkWithoutChangingFixedPalettes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun themed(mode: Int) = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
        })
        val light = themed(Configuration.UI_MODE_NIGHT_NO)
        val dark = themed(Configuration.UI_MODE_NIGHT_YES)
        val lightColors = widgetColors(light, "System")
        val darkColors = widgetColors(dark, "System")
        check(lightColors.background.luminance() > darkColors.background.luminance())
        check(lightColors.text.luminance() < lightColors.background.luminance())
        check(darkColors.text.luminance() > darkColors.background.luminance())
        if (Build.VERSION.SDK_INT >= 31) {
            check(lightColors.background.toArgb() == light.getColor(android.R.color.system_accent2_50))
            check(darkColors.background.toArgb() == dark.getColor(android.R.color.system_accent2_800))
            check(lightColors.text.toArgb() == light.getColor(android.R.color.system_accent2_900))
            check(darkColors.text.toArgb() == dark.getColor(android.R.color.system_accent2_100))
            check(lightColors.accent.toArgb() == light.getColor(android.R.color.system_accent1_600))
            check(darkColors.accent.toArgb() == dark.getColor(android.R.color.system_accent1_200))
        }
        for (colors in listOf(lightColors, darkColors)) {
            check(ColorUtils.calculateContrast(colors.text.toArgb(), colors.background.toArgb()) >= 4.5)
            check(ColorUtils.calculateContrast(colors.muted.toArgb(), colors.background.toArgb()) >= 4.5)
            check(ColorUtils.calculateContrast(colors.accent.toArgb(), colors.background.toArgb()) >= 3.0)
        }
        check(widgetColors(light, "Graphite") == widgetColors(dark, "Graphite"))
        check(widgetColors(light, "Sage") == widgetColors(dark, "Sage"))
    }

    @Test fun launchBackgroundMatchesAppSystemTheme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun themed(mode: Int) = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
        })
        val light = themed(Configuration.UI_MODE_NIGHT_NO)
        val dark = themed(Configuration.UI_MODE_NIGHT_YES)
        if (Build.VERSION.SDK_INT >= 34) {
            for ((configured, expected) in listOf(
                light.getColor(R.color.launch_background) to dynamicLightColorScheme(light).background.toArgb(),
                dark.getColor(R.color.launch_background) to dynamicDarkColorScheme(dark).background.toArgb(),
            )) {
                check(configured == expected) {
                    "Launch background ${configured.toUInt().toString(16)} differs from app background ${expected.toUInt().toString(16)}"
                }
            }
        } else if (Build.VERSION.SDK_INT >= 31) {
            check(kotlin.math.abs(
                ColorUtils.calculateLuminance(light.getColor(R.color.launch_background)) -
                    ColorUtils.calculateLuminance(dynamicLightColorScheme(light).background.toArgb())
            ) < 0.1)
            check(kotlin.math.abs(
                ColorUtils.calculateLuminance(dark.getColor(R.color.launch_background)) -
                    ColorUtils.calculateLuminance(dynamicDarkColorScheme(dark).background.toArgb())
            ) < 0.1)
        } else {
            check(light.getColor(R.color.launch_background) != dark.getColor(R.color.launch_background))
        }
    }

    @Test fun nightModeChangeSchedulesWidgetRedraw() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as FolioApplication
        val workManager = WorkManager.getInstance(application)
        val original = Configuration(application.resources.configuration)
        val changed = Configuration(original).apply {
            val current = uiMode and Configuration.UI_MODE_NIGHT_MASK
            val other = if (current == Configuration.UI_MODE_NIGHT_YES) {
                Configuration.UI_MODE_NIGHT_NO
            } else {
                Configuration.UI_MODE_NIGHT_YES
            }
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or other
        }
        val oldIds = workManager.getWorkInfosForUniqueWork("folio-widget-theme")
            .get(10, TimeUnit.SECONDS).map { it.id }.toSet()
        try {
            instrumentation.runOnMainSync { application.onConfigurationChanged(changed) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var scheduled = false
            while (System.nanoTime() < deadline && !scheduled) {
                scheduled = workManager.getWorkInfosForUniqueWork("folio-widget-theme")
                    .get(10, TimeUnit.SECONDS).any { it.id !in oldIds }
                if (!scheduled) Thread.sleep(100)
            }
            check(scheduled)
        } finally {
            instrumentation.runOnMainSync { application.onConfigurationChanged(original) }
        }
    }
}
