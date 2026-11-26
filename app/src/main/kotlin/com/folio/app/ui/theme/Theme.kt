/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.folio.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xffc9ef9a),
    onPrimary = Color(0xff263c25),
    primaryContainer = Color(0xff354b32),
    onPrimaryContainer = Color(0xffe6f4d5),
    secondary = Color(0xffadc39f),
    secondaryContainer = Color(0xff354b32),
    onSecondaryContainer = Color(0xffe6f4d5),
    background = Color(0xff19231e),
    onBackground = Color(0xffeef3e7),
    surface = Color(0xff19231e),
    onSurface = Color(0xffeef3e7),
    surfaceVariant = Color(0xff354236),
    onSurfaceVariant = Color(0xffc0ccba),
    surfaceContainer = Color(0xff243129),
    surfaceContainerLow = Color(0xff202c25),
    surfaceContainerHigh = Color(0xff29372d),
    surfaceContainerHighest = Color(0xff2e3c31),
    outline = Color(0xff7c8975),
    outlineVariant = Color(0xff354236),
    error = Color(0xffe2a38e),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xff526f3c),
    onPrimary = Color.White,
    primaryContainer = Color(0xffd5eabf),
    onPrimaryContainer = Color(0xff263d20),
    secondary = Color(0xff708065),
    secondaryContainer = Color(0xffd5eabf),
    onSecondaryContainer = Color(0xff263d20),
    background = Color(0xfff8f9f2),
    onBackground = Color(0xff253125),
    surface = Color(0xfff8f9f2),
    onSurface = Color(0xff253125),
    surfaceVariant = Color(0xffe4e9dc),
    onSurfaceVariant = Color(0xff485746),
    surfaceContainer = Color(0xffeef1e7),
    surfaceContainerLow = Color(0xfff2f4ec),
    surfaceContainerHigh = Color(0xffe9ede1),
    surfaceContainerHighest = Color(0xffe4e9dc),
    outline = Color(0xff7c8975),
    outlineVariant = Color(0xffdce1d7),
    error = Color(0xffa65a47),
)

@Composable
fun FolioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

