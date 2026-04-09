package com.example.leadhunters.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryRed,
    onPrimary = SurfaceLight,
    primaryContainer = SurfaceDark,
    onPrimaryContainer = SurfaceLight,
    secondary = DarkRed,
    onSecondary = SurfaceLight,
    background = DarkBackground,
    onBackground = SurfaceLight,
    surface = SurfaceDark,
    onSurface = SurfaceLight,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorCoral
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryRed,
    onPrimary = SurfaceLight,
    primaryContainer = SoftRed,
    onPrimaryContainer = DarkRed,
    secondary = DarkRed,
    onSecondary = SurfaceLight,
    background = LightBackground,
    onBackground = OnSurfacePrimaryLight,
    surface = SurfaceLight,
    onSurface = OnSurfacePrimaryLight,
    surfaceVariant = Color(0xFFF9FAFB),
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorCoral
)

@Composable
fun LeadHuntersTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled to enforce consistent branding
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}