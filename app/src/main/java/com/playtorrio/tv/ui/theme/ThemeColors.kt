package com.playtorrio.tv.ui.theme

import androidx.compose.ui.graphics.Color
import com.playtorrio.tv.domain.model.AppTheme

data class ThemeColorPalette(
    val secondary: Color = Color(0xFF38BDF8),
    val secondaryVariant: Color = Color(0xFF0284C7),
    val onSecondary: Color = PlayTorrioPrimitives.white,
    val onSecondaryVariant: Color = PlayTorrioPrimitives.white,
    val accentGradient: List<Color> = listOf(secondary),
    val focusRing: Color = Color(0xFF38BDF8),
    val focusRingGradient: List<Color> = listOf(focusRing),
    val focusBackground: Color = Color(0xFF1E3A60),
    val background: Color = PlayTorrioPrimitives.neutral950,
    val backgroundElevated: Color = PlayTorrioPrimitives.neutral900,
    val backgroundCard: Color = PlayTorrioPrimitives.neutral825,
    val surface: Color = PlayTorrioPrimitives.neutral875,
    val surfaceVariant: Color = PlayTorrioPrimitives.neutral800,
    val panel: Color = PlayTorrioPrimitives.neutral900,
    val overlay: Color = Color(0xD90B1320),
    val field: Color = PlayTorrioPrimitives.neutral850,
    val menu: Color = PlayTorrioPrimitives.neutral875,
    val modal: Color = PlayTorrioPrimitives.neutral900,
    val playerOverlay: Color = Color(0xCC0B1320)
)

object ThemeColors {
    val Crimson = ThemeColorPalette(
        secondary = PlayTorrioPrimitives.red500,
        secondaryVariant = PlayTorrioPrimitives.red600,
        focusRing = PlayTorrioPrimitives.red300,
        focusBackground = Color(0xFF3D1A1A),
        backgroundCard = Color(0xFF241A1A)
    )

    val Ocean = ThemeColorPalette(
        secondary = Color(0xFF38BDF8),
        secondaryVariant = Color(0xFF0284C7),
        focusRing = Color(0xFF7DD3FC),
        focusBackground = Color(0xFF1E3A60),
        background = Color(0xFF0B1320),
        backgroundElevated = Color(0xFF14233D),
        backgroundCard = Color(0xFF1F375C)
    )

    val Violet = ThemeColorPalette(
        secondary = PlayTorrioPrimitives.violet500,
        secondaryVariant = PlayTorrioPrimitives.violet700,
        focusRing = PlayTorrioPrimitives.violet300,
        focusBackground = Color(0xFF2D1A3D),
        background = Color(0xFF0B1320),
        backgroundElevated = Color(0xFF14233D),
        backgroundCard = Color(0xFF1F1A35)
    )

    val Emerald = ThemeColorPalette(
        secondary = PlayTorrioPrimitives.green500,
        secondaryVariant = PlayTorrioPrimitives.green700,
        focusRing = PlayTorrioPrimitives.green300,
        focusBackground = Color(0xFF1A3D25),
        backgroundCard = Color(0xFF1A2B20)
    )

    val Amber = ThemeColorPalette(
        secondary = PlayTorrioPrimitives.amber500,
        secondaryVariant = PlayTorrioPrimitives.amber700,
        focusRing = PlayTorrioPrimitives.amber300,
        focusBackground = Color(0xFF3D2D1A),
        background = Color(0xFF0B1320),
        backgroundElevated = Color(0xFF14233D),
        backgroundCard = Color(0xFF24201A)
    )

    val Rose = ThemeColorPalette(
        secondary = PlayTorrioPrimitives.rose500,
        secondaryVariant = PlayTorrioPrimitives.rose700,
        focusRing = PlayTorrioPrimitives.rose300,
        focusBackground = Color(0xFF3D1A2D),
        backgroundCard = Color(0xFF241A1F)
    )

    val White = ThemeColorPalette(
        secondary = Color(0xFF38BDF8),
        secondaryVariant = Color(0xFF0284C7),
        onSecondary = PlayTorrioPrimitives.white,
        onSecondaryVariant = PlayTorrioPrimitives.white,
        focusRing = Color(0xFF38BDF8),
        focusBackground = Color(0xFF1E3A60),
        background = Color(0xFF0B1320),
        backgroundElevated = Color(0xFF14233D),
        backgroundCard = Color(0xFF1F375C),
        surface = Color(0xFF182A47),
        surfaceVariant = Color(0xFF243E66),
        panel = Color(0xFF14233D),
        field = Color(0xFF1C3152),
        menu = Color(0xFF182A47),
        modal = Color(0xFF14233D)
    )

    fun getColorPalette(theme: AppTheme): ThemeColorPalette {
        return when (theme) {
            AppTheme.GOLD -> SupporterThemeColors.Gold
            AppTheme.JADE -> SupporterThemeColors.Jade
            AppTheme.ROSE_GOLD -> SupporterThemeColors.RoseGold
            AppTheme.ARCTIC_BLUE -> SupporterThemeColors.ArcticBlue
            AppTheme.GRAPHITE -> SupporterThemeColors.Graphite
            AppTheme.CRIMSON -> Crimson
            AppTheme.OCEAN -> Ocean
            AppTheme.VIOLET -> Violet
            AppTheme.EMERALD -> Emerald
            AppTheme.AMBER -> Amber
            AppTheme.ROSE -> Rose
            AppTheme.WHITE -> White
        }
    }
}
