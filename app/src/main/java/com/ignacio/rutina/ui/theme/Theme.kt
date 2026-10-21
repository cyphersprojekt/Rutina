package com.ignacio.rutina.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D6B),
    secondary = Color(0xFF4A635C),
    tertiary = Color(0xFF41627A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86D5BF),
    secondary = Color(0xFFB1CCC3),
    tertiary = Color(0xFFA9CBE6),
)

/** Lo que se hizo, pero tarde. Más oscuro sobre fondo claro para que el texto se lea. */
val ColorScheme.late: Color
    get() = if (background.luminance() < 0.5f) Color(0xFFF2B35B) else Color(0xFFA26500)

@Composable
fun RutinaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
