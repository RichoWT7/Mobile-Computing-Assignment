package com.example.myapplication.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.material3.Typography  // ← Explicit import
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

private val FreshGreen = Color(0xFF4CAF50)
private val DarkGreen = Color(0xFF388E3C)
private val LightGreen = Color(0xFF81C784)
private val WarmOrange = Color(0xFFFF9800)
private val LightOrange = Color(0xFFFFB74D)
private val ErrorRed = Color(0xFFF44336)

private val LightColorScheme = lightColorScheme(
    primary = FreshGreen,
    onPrimary = Color.White,
    primaryContainer = LightGreen,
    onPrimaryContainer = DarkGreen,

    secondary = WarmOrange,
    onSecondary = Color.White,
    secondaryContainer = LightOrange,
    onSecondaryContainer = Color(0xFFE65100),

    tertiary = Color(0xFF03A9F4),
    onTertiary = Color.White,

    error = ErrorRed,
    onError = Color.White,

    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF212121),

    surface = Color.White,
    onSurface = Color(0xFF212121),

    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF616161),

    outline = Color(0xFFBDBDBD)
)

private val DarkColorScheme = darkColorScheme(
    primary = LightGreen,
    onPrimary = Color(0xFF1B5E20),
    primaryContainer = DarkGreen,
    onPrimaryContainer = LightGreen,

    secondary = LightOrange,
    onSecondary = Color(0xFFE65100),
    secondaryContainer = Color(0xFFF57C00),
    onSecondaryContainer = LightOrange,

    tertiary = Color(0xFF4FC3F7),
    onTertiary = Color(0xFF01579B),

    error = Color(0xFFEF5350),
    onError = Color(0xFFFFEBEE),

    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),

    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),

    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFBDBDBD),

    outline = Color(0xFF616161)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            window.statusBarColor = colorScheme.primary.toArgb()

            window.navigationBarColor = colorScheme.surface.toArgb()

            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}