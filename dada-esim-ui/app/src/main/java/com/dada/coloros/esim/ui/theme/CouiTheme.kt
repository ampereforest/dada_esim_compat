package com.dada.coloros.esim.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Immutable
data class CouiColorScheme(
    val primary: Color,
    val onPrimary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val secondary: Color,
    val secondaryVariant: Color,
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val onSurface: Color,
    val onSurfaceSecondary: Color,
    val onSurfaceVariantSummary: Color,
    val onSurfaceVariantActions: Color,
    val dividerLine: Color,
    val error: Color,
    val errorContainer: Color,
    val windowDimming: Color,
    val success: Color,
    val successContainer: Color,
    val isDark: Boolean,
)

private val CouiLightColors = CouiColorScheme(
    primary = Color(0xFF0080FF),
    onPrimary = Color.White,
    tertiaryContainer = Color(0x1F0080FF),
    onTertiaryContainer = Color(0xFF0080FF),
    secondary = Color(0xFFD8DADC),
    secondaryVariant = Color(0x14000000),
    surface = Color(0xFFF7F8FA),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE6E6E6),
    onSurface = Color(0xE6000000),
    onSurfaceSecondary = Color(0x8A000000),
    onSurfaceVariantSummary = Color(0x66000000),
    onSurfaceVariantActions = Color(0x42000000),
    dividerLine = Color(0x1F000000),
    error = Color(0xFFDB382C),
    errorContainer = Color(0x26EB3B2F),
    windowDimming = Color(0x33000000),
    success = Color(0xFF1F9D55),
    successContainer = Color(0x1F1F9D55),
    isDark = false,
)

private val CouiDarkColors = CouiColorScheme(
    primary = Color(0xFF1A8CFF),
    onPrimary = Color.White,
    tertiaryContainer = Color(0x401A8CFF),
    onTertiaryContainer = Color(0xFF5C9DFF),
    secondary = Color(0xFF595959),
    secondaryVariant = Color(0x26FFFFFF),
    surface = Color.Black,
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    onSurface = Color(0xE6FFFFFF),
    onSurfaceSecondary = Color(0x8AFFFFFF),
    onSurfaceVariantSummary = Color(0x66FFFFFF),
    onSurfaceVariantActions = Color(0x4DFFFFFF),
    dividerLine = Color(0x33FFFFFF),
    error = Color(0xFFFF6C61),
    errorContainer = Color(0x40EB493D),
    windowDimming = Color(0x99000000),
    success = Color(0xFF4ADE80),
    successContainer = Color(0x334ADE80),
    isDark = true,
)

@Immutable
data class CouiTypography(
    val largeTitle: TextStyle = TextStyle(fontSize = 32.sp, lineHeight = 1.23f.em),
    val topBarTitle: TextStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 1.24f.em),
    val rowTitle: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 1.16f.em),
    val body: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 1.22f.em),
    val summary: TextStyle = TextStyle(fontSize = 13.5.sp, lineHeight = 1.24f.em),
    val sectionTitle: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 1.22f.em),
    val button: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 1.26f.em),
)

val LocalCouiColors = staticCompositionLocalOf { CouiLightColors }
val LocalCouiTypography = staticCompositionLocalOf { CouiTypography() }

object CouiTheme {
    val colors: CouiColorScheme
        @Composable get() = LocalCouiColors.current

    val typography: CouiTypography
        @Composable get() = LocalCouiTypography.current
}

@Composable
fun CouiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) CouiDarkColors else CouiLightColors
    CompositionLocalProvider(
        LocalCouiColors provides colors,
        LocalCouiTypography provides CouiTypography(),
        content = content,
    )
}
