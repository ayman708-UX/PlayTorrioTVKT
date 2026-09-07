package com.playtorrio.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.playtorrio.tv.domain.model.AppFont
import com.playtorrio.tv.domain.model.AppTheme
import com.playtorrio.tv.domain.model.SettingsUiStyle

data class PlayTorrioExtendedColors(
    val backgroundElevated: Color,
    val backgroundCard: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val focusRing: Color,
    val focusBackground: Color,
    val rating: Color
)

val LocalPlayTorrioColors = staticCompositionLocalOf {
    PlayTorrioColorScheme(ThemeColors.Ocean)
}

val LocalPlayTorrioExtendedColors = staticCompositionLocalOf {
    PlayTorrioExtendedColors(
        backgroundElevated = Color(0xFF1A1A1A),
        backgroundCard = Color(0xFF242424),
        textSecondary = Color(0xFFB3B3B3),
        textTertiary = Color(0xFF808080),
        focusRing = ThemeColors.Ocean.focusRing,
        focusBackground = ThemeColors.Ocean.focusBackground,
        rating = Color(0xFFFFD700)
    )
}

val LocalPlayTorrioTextStyles = staticCompositionLocalOf { PlayTorrioTextStyles }

val LocalAppTheme = staticCompositionLocalOf { AppTheme.WHITE }

val LocalSettingsUiStyle = staticCompositionLocalOf { SettingsUiStyle.CLASSIC }

val LocalPlayTorrioFocusRingStyle = staticCompositionLocalOf {
    createFocusRingStyle(ThemeColors.Ocean)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayTorrioTheme(
    appTheme: AppTheme = AppTheme.WHITE,
    appFont: AppFont = AppFont.INTER,
    amoledMode: Boolean = false,
    amoledSurfacesMode: Boolean = false,
    settingsUiStyle: SettingsUiStyle = SettingsUiStyle.CLASSIC,
    content: @Composable () -> Unit
) {
    val palette = ThemeColors.getColorPalette(appTheme)
    val focusRingStyle = createFocusRingStyle(palette)
    val colorScheme = PlayTorrioColorScheme(
        palette = palette,
        amoledMode = amoledMode,
        amoledSurfacesMode = amoledSurfacesMode
    )
    val typography = buildPlayTorrioTypography(getFontFamily(appFont))
    val textStyles = buildPlayTorrioTextStyles(typography)

    val materialColorScheme = darkColorScheme(
        primary = colorScheme.Primary,
        onPrimary = colorScheme.OnPrimary,
        secondary = colorScheme.Secondary,
        onSecondary = colorScheme.OnSecondary,
        background = colorScheme.Background,
        surface = colorScheme.Surface,
        surfaceVariant = colorScheme.SurfaceVariant,
        onBackground = colorScheme.TextPrimary,
        onSurface = colorScheme.TextPrimary,
        onSurfaceVariant = colorScheme.TextSecondary,
        error = colorScheme.Error
    )

    val extendedColors = PlayTorrioExtendedColors(
        backgroundElevated = colorScheme.BackgroundElevated,
        backgroundCard = colorScheme.BackgroundCard,
        textSecondary = colorScheme.TextSecondary,
        textTertiary = colorScheme.TextTertiary,
        focusRing = colorScheme.FocusRing,
        focusBackground = colorScheme.FocusBackground,
        rating = colorScheme.Rating
    )

    CompositionLocalProvider(
        LocalPlayTorrioColors provides colorScheme,
        LocalPlayTorrioExtendedColors provides extendedColors,
        LocalPlayTorrioTextStyles provides textStyles,
        LocalAppTheme provides appTheme,
        LocalSettingsUiStyle provides settingsUiStyle,
        LocalPlayTorrioFocusRingStyle provides focusRingStyle
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = typography,
            content = content
        )
    }
}

object PlayTorrioTheme {
    val colors: PlayTorrioColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalPlayTorrioColors.current

    val extendedColors: PlayTorrioExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPlayTorrioExtendedColors.current

    val textStyles: PlayTorrioTextStyleTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalPlayTorrioTextStyles.current

    val spacing: PlayTorrioSpacingTokens
        get() = PlayTorrioSpacing.tokens

    val radii: PlayTorrioRadiusTokens
        get() = PlayTorrioRadii.tokens

    val shapes: PlayTorrioShapeTokens
        get() = PlayTorrioShapes.tokens

    val sizes: PlayTorrioSizeTokens
        get() = PlayTorrioSizes.tokens

    val strokes: PlayTorrioStrokeTokens
        get() = PlayTorrioStrokes.tokens

    val elevations: PlayTorrioElevationTokens
        get() = PlayTorrioElevations.tokens

    val effects: PlayTorrioEffectTokens
        get() = PlayTorrioEffects.tokens

    val motion: PlayTorrioMotionTokens
        get() = PlayTorrioMotion.tokens

    val focus: PlayTorrioFocusTokens
        get() = PlayTorrioFocus.tokens

    val focusRing: PlayTorrioFocusRingStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalPlayTorrioFocusRingStyle.current

    val layout: PlayTorrioLayoutTokens
        get() = PlayTorrioLayout.tokens

    val media: PlayTorrioMediaTokens
        get() = PlayTorrioMedia.tokens

    val components: PlayTorrioComponentTokens
        get() = PlayTorrioComponents.tokens

    val currentTheme: AppTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTheme.current

    val settingsUiStyle: SettingsUiStyle
        @Composable
        @ReadOnlyComposable
        get() = LocalSettingsUiStyle.current
}
