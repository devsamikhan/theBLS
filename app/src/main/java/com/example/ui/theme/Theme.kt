package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * =====================================================================
 * MINIMALIST MATERIAL DESIGN 3 (M3) SHAPES SCALE
 * =====================================================================
 * Refined geometric radii that prevent clumsy visual bloat:
 * - extraSmall (6.dp): Status indicator dots, tags, and micro badges
 * - small (8.dp): Quick-action pill buttons, chip filters, text fields
 * - medium (12.dp): Transaction item rows, KPI metric tiles, popup items
 * - large (16.dp): Ledger cards, summary containers, top banners
 * - extraLarge (22.dp): Bottom sheets, modal dialogs, profile cards
 */
val BlsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

/**
 * =====================================================================
 * M3 LIGHT COLOR SCHEME
 * =====================================================================
 */
private val LightColorScheme = lightColorScheme(
    primary = M3PrimaryLight,
    onPrimary = M3OnPrimaryLight,
    primaryContainer = M3PrimaryContainerLight,
    onPrimaryContainer = M3OnPrimaryContainerLight,

    secondary = M3SecondaryLight,
    onSecondary = M3OnSecondaryLight,
    secondaryContainer = M3SecondaryContainerLight,
    onSecondaryContainer = M3OnSecondaryContainerLight,

    tertiary = M3TertiaryLight,
    onTertiary = M3OnTertiaryLight,
    tertiaryContainer = M3TertiaryContainerLight,
    onTertiaryContainer = M3OnTertiaryContainerLight,

    error = M3ErrorLight,
    onError = M3OnErrorLight,
    errorContainer = M3ErrorContainerLight,
    onErrorContainer = M3OnErrorContainerLight,

    background = M3BackgroundLight,
    onBackground = M3OnBackgroundLight,
    surface = M3SurfaceLight,
    onSurface = M3OnSurfaceLight,
    surfaceVariant = M3SurfaceVariantLight,
    onSurfaceVariant = M3OnSurfaceVariantLight,
    outline = M3OutlineLight,
    outlineVariant = M3OutlineVariantLight
)

/**
 * =====================================================================
 * M3 DARK COLOR SCHEME
 * =====================================================================
 */
private val DarkColorScheme = darkColorScheme(
    primary = M3PrimaryDark,
    onPrimary = M3OnPrimaryDark,
    primaryContainer = M3PrimaryContainerDark,
    onPrimaryContainer = M3OnPrimaryContainerDark,

    secondary = M3SecondaryDark,
    onSecondary = M3OnSecondaryDark,
    secondaryContainer = M3SecondaryContainerDark,
    onSecondaryContainer = M3OnSecondaryContainerDark,

    tertiary = M3TertiaryDark,
    onTertiary = M3OnTertiaryDark,
    tertiaryContainer = M3TertiaryContainerDark,
    onTertiaryContainer = M3OnTertiaryContainerDark,

    error = M3ErrorDark,
    onError = M3OnErrorDark,
    errorContainer = M3ErrorContainerDark,
    onErrorContainer = M3OnErrorContainerDark,

    background = M3BackgroundDark,
    onBackground = M3OnBackgroundDark,
    surface = M3SurfaceDark,
    onSurface = M3OnSurfaceDark,
    surfaceVariant = M3SurfaceVariantDark,
    onSurfaceVariant = M3OnSurfaceVariantDark,
    outline = M3OutlineDark,
    outlineVariant = M3OutlineVariantDark
)

/**
 * Main Institutional Application Theme for Bright Light School (BLS).
 * Integrates M3 Core with Custom Domain FinancialColorScheme and
 * edge-to-edge system bar controls.
 */
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val financialColors = if (darkTheme) DarkFinancialColorScheme else LightFinancialColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalFinancialColors provides financialColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = BlsShapes,
            content = content
        )
    }
}
