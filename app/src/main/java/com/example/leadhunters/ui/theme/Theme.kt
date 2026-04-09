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
    primary = PrimaryBlue,
    onPrimary = SurfaceLight,
    primaryContainer = SurfaceDark,
    onPrimaryContainer = SurfaceLight,
    secondary = SecondaryBlue,
    onSecondary = SurfaceLight,
    background = DarkBackground,
    onBackground = SurfaceLight,
    surface = SurfaceDark,
    onSurface = SurfaceLight,
    surfaceVariant = DarkDeepBlue,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorCoral
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = SurfaceLight,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = PrimaryBlue,
    secondary = SecondaryBlue,
    onSecondary = SurfaceLight,
    background = LightBackground,
    onBackground = DarkDeepBlue,
    surface = SurfaceLight,
    onSurface = DarkDeepBlue,
    surfaceVariant = Color(0xFFF1F4F9),
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
            window.statusBarColor = colorScheme.background.toArgb()
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