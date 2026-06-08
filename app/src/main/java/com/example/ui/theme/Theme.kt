package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val AmoledDarkColorScheme = darkColorScheme(
    primary = Color(0xFF69F0AE), // cool terminal green
    secondary = Color(0xFF78909C),
    tertiary = Color(0xFF7C4DFF), // test demo colour
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceCard,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary
)

private val StandardLightColorScheme = lightColorScheme(
    primary = Color(0xFF00796B), // dark teal
    secondary = Color(0xFF455A64),
    tertiary = Color(0xFF5E35B1),
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceCard,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextSecondary
)

@Composable
fun FinTraceTheme(
    mode: String = "AMOLED", // "Light", "AMOLED", "System"
    content: @Composable () -> Unit
) {
    val darkTheme = when (mode) {
        "Light" -> false
        "AMOLED" -> true
        else -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    val colorScheme = when {
        // Use custom AMOLED dark theme if mode is AMOLED
        mode == "AMOLED" -> AmoledDarkColorScheme
        mode == "Light" -> StandardLightColorScheme
        
        // Otherwise handle System settings with dynamic coloring if requested and on S+ Android
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> AmoledDarkColorScheme
        else -> StandardLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
