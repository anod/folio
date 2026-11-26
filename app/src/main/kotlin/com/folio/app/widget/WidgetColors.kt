package com.folio.app.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

data class WidgetColors(
    val background: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val negative: Color,
    val divider: Color,
)

/** Green only when trading is confirmed; neutral gray (never a theme tint) for everything else. */
fun tradingDotColor(active: Boolean, darkBackground: Boolean): Color = when {
    active && darkBackground -> Color(0xff5fd68b)
    active -> Color(0xff1e8e4a)
    darkBackground -> Color(0xff8d918f)
    else -> Color(0xff9a9e9c)
}

fun WidgetColors.tradingDot(active: Boolean): Color = tradingDotColor(active, background.luminance() < 0.5f)

fun widgetColors(context: Context, theme: String): WidgetColors {
    val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    if (theme == "System" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        val background = Color(context.getColor(
            if (dark) android.R.color.system_accent2_800 else android.R.color.system_accent2_50
        ))
        val text = Color(context.getColor(
            if (dark) android.R.color.system_accent2_100 else android.R.color.system_accent2_900
        ))
        val accent = Color(context.getColor(
            if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
        ))
        return WidgetColors(background, text, scheme.onSurfaceVariant,
            accent, scheme.error, scheme.outlineVariant)
    }
    val graphite = theme == "Graphite" || (theme == "System" && dark)
    val background = when {
        graphite -> Color(0xff1b2923)
        theme == "Paper" -> Color(0xfff5f3eb)
        else -> Color(0xffe2edcf)
    }
    return WidgetColors(
        background,
        if (graphite) Color(0xfff1f6e8) else Color(0xff263723),
        if (graphite) Color(0xff9bad9f) else Color(0xff687a5f),
        if (graphite) Color(0xffc9ef9a) else Color(0xff506e37),
        if (graphite) Color(0xffe2a38e) else Color(0xffa65a47),
        if (graphite) Color(0xff35443a) else Color(0xffcbd9bd),
    )
}
