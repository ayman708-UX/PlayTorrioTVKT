package com.playtorrio.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

@Immutable
data class PlayTorrioSurfaceColors(
    val background: Color,
    val raised: Color,
    val card: Color,
    val default: Color,
    val variant: Color,
    val panel: Color,
    val overlay: Color,
    val field: Color,
    val menu: Color,
    val modal: Color,
    val playerOverlay: Color,
    val divider: Color
)

@Immutable
data class PlayTorrioTextColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val disabled: Color,
    val inverse: Color,
    val onAccent: Color,
    val onOverlay: Color,
    val metadata: Color
)

@Immutable
data class PlayTorrioFocusColors(
    val ring: Color,
    val background: Color,
    val content: Color,
    val accent: Color,
    val scrim: Color
)

@Immutable
data class PlayTorrioSelectionColors(
    val background: Color,
    val foreground: Color,
    val border: Color,
    val mutedBackground: Color,
    val mutedForeground: Color
)

@Immutable
data class PlayTorrioMediaColors(
    val heroScrim: Color,
    val imageScrim: Color,
    val posterFallback: Color,
    val videoControlsScrim: Color,
    val glassPanelTop: Color,
    val glassPanelMiddle: Color,
    val glassPanelBottom: Color,
    val glow: Color
)

@Immutable
data class PlayTorrioStatusColors(
    val rating: Color,
    val error: Color,
    val warning: Color,
    val success: Color,
    val info: Color,
    val watched: Color,
    val unwatched: Color,
    val cached: Color,
    val torrent: Color,
    val premium: Color
)

@Immutable
data class PlayTorrioDisabledColors(
    val container: Color,
    val content: Color,
    val border: Color,
    val overlay: Color
)

@Immutable
data class PlayTorrioSourceColors(
    val trakt: Color,
    val tmdb: Color,
    val imdb: Color,
    val mdblist: Color
)

@Immutable
data class PlayTorrioContrastPair(
    val foreground: Color,
    val background: Color
)

class PlayTorrioColorScheme(
    palette: ThemeColorPalette,
    amoledMode: Boolean = false,
    amoledSurfacesMode: Boolean = false
) {
    private val pureBlack = PlayTorrioPrimitives.black
    private val pureBlackSurfaces = amoledMode && amoledSurfacesMode

    val Background = if (amoledMode) pureBlack else palette.background
    val BackgroundElevated = if (pureBlackSurfaces) pureBlack else palette.backgroundElevated
    val BackgroundCard = if (pureBlackSurfaces) pureBlack else palette.backgroundCard
    val Surface = if (pureBlackSurfaces) pureBlack else palette.surface
    val SurfaceVariant = if (pureBlackSurfaces) pureBlack else palette.surfaceVariant
    val Panel = if (pureBlackSurfaces) pureBlack else palette.panel
    val Overlay = palette.overlay
    val Field = if (pureBlackSurfaces) pureBlack else palette.field
    val Menu = if (pureBlackSurfaces) pureBlack else palette.menu
    val Modal = if (pureBlackSurfaces) pureBlack else palette.modal
    val PlayerOverlay = palette.playerOverlay
    val Divider = PlayTorrioPrimitives.neutral750

    val Primary = PlayTorrioPrimitives.neutral500
    val PrimaryVariant = PlayTorrioPrimitives.neutral650
    val OnPrimary = PlayTorrioPrimitives.white
    val Secondary = palette.secondary
    val SecondaryVariant = palette.secondaryVariant
    val OnSecondary = palette.onSecondary
    val OnSecondaryVariant = palette.onSecondaryVariant

    val TextPrimary = PlayTorrioPrimitives.white
    val TextSecondary = PlayTorrioPrimitives.neutral400
    val TextTertiary = PlayTorrioPrimitives.neutral600
    val TextDisabled = PlayTorrioPrimitives.neutral700
    val TextInverse = PlayTorrioPrimitives.neutral925

    val FocusRing = palette.focusRing
    val FocusBackground = palette.focusBackground
    val FocusContent = PlayTorrioPrimitives.white
    val FocusScrim = PlayTorrioPrimitives.black.copy(alpha = 0.32f)

    val Rating = PlayTorrioPrimitives.rating
    val Error = PlayTorrioPrimitives.error
    val Warning = PlayTorrioPrimitives.warning
    val Success = PlayTorrioPrimitives.success
    val Info = PlayTorrioPrimitives.info
    val Watched = PlayTorrioPrimitives.success
    val Unwatched = PlayTorrioPrimitives.neutral600
    val Cached = PlayTorrioPrimitives.blue300
    val Torrent = PlayTorrioPrimitives.torrent
    val Premium = PlayTorrioPrimitives.premium

    val Border = PlayTorrioPrimitives.neutral750
    val BorderFocused = FocusRing
    val BorderMuted = PlayTorrioPrimitives.neutral750.copy(alpha = 0.58f)

    val Scrim = PlayTorrioPrimitives.black.copy(alpha = 0.62f)
    val ImageScrim = PlayTorrioPrimitives.black.copy(alpha = 0.58f)
    val VideoControlsScrim = PlayTorrioPrimitives.black.copy(alpha = 0.72f)
    val PosterFallback = BackgroundCard

    val DisabledContainer = SurfaceVariant.copy(alpha = 0.42f)
    val DisabledContent = TextDisabled
    val DisabledBorder = Border.copy(alpha = 0.48f)
    val DisabledOverlay = PlayTorrioPrimitives.black.copy(alpha = 0.42f)

    val surfaces = PlayTorrioSurfaceColors(
        background = Background,
        raised = BackgroundElevated,
        card = BackgroundCard,
        default = Surface,
        variant = SurfaceVariant,
        panel = Panel,
        overlay = Overlay,
        field = Field,
        menu = Menu,
        modal = Modal,
        playerOverlay = PlayerOverlay,
        divider = Divider
    )

    val text = PlayTorrioTextColors(
        primary = TextPrimary,
        secondary = TextSecondary,
        tertiary = TextTertiary,
        disabled = TextDisabled,
        inverse = TextInverse,
        onAccent = OnSecondary,
        onOverlay = PlayTorrioPrimitives.white,
        metadata = TextSecondary
    )

    val focus = PlayTorrioFocusColors(
        ring = FocusRing,
        background = FocusBackground,
        content = FocusContent,
        accent = Secondary,
        scrim = FocusScrim
    )

    val selection = PlayTorrioSelectionColors(
        background = Secondary,
        foreground = OnSecondary,
        border = SecondaryVariant,
        mutedBackground = FocusBackground,
        mutedForeground = TextPrimary
    )

    val media = PlayTorrioMediaColors(
        heroScrim = Scrim,
        imageScrim = ImageScrim,
        posterFallback = PosterFallback,
        videoControlsScrim = VideoControlsScrim,
        glassPanelTop = Color(0xD61E3A5F),
        glassPanelMiddle = Color(0xCC162D4A),
        glassPanelBottom = Color(0xC611233B),
        glow = FocusRing.copy(alpha = 0.32f)
    )

    val status = PlayTorrioStatusColors(
        rating = Rating,
        error = Error,
        warning = Warning,
        success = Success,
        info = Info,
        watched = Watched,
        unwatched = Unwatched,
        cached = Cached,
        torrent = Torrent,
        premium = Premium
    )

    val disabled = PlayTorrioDisabledColors(
        container = DisabledContainer,
        content = DisabledContent,
        border = DisabledBorder,
        overlay = DisabledOverlay
    )

    val source = PlayTorrioSourceColors(
        trakt = PlayTorrioPrimitives.trakt,
        tmdb = PlayTorrioPrimitives.tmdb,
        imdb = PlayTorrioPrimitives.imdb,
        mdblist = PlayTorrioPrimitives.mdblist
    )

    val contrastPairs = listOf(
        PlayTorrioContrastPair(TextPrimary, Background),
        PlayTorrioContrastPair(TextPrimary, BackgroundCard),
        PlayTorrioContrastPair(TextSecondary, Background),
        PlayTorrioContrastPair(OnSecondary, Secondary),
        PlayTorrioContrastPair(FocusContent, FocusBackground),
        PlayTorrioContrastPair(PlayTorrioPrimitives.white, PlayerOverlay)
    )
}

object PlayTorrioColors {
    val Background: Color
        @Composable
        @ReadOnlyComposable
        get() = PlayTorrioTheme.colors.Background

    val BackgroundElevated: Color
        @Composable
        @ReadOnlyComposable
        get() = PlayTorrioTheme.colors.BackgroundElevated

    val BackgroundCard: Color
        @Composable
        @ReadOnlyComposable
        get() = PlayTorrioTheme.colors.BackgroundCard

    val Surface: Color
        @Composable
        @ReadOnlyComposable
        get() = PlayTorrioTheme.colors.Surface

    val SurfaceVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = PlayTorrioTheme.colors.SurfaceVariant

    val Primary = PlayTorrioPrimitives.neutral500
    val PrimaryVariant = PlayTorrioPrimitives.neutral650
    val OnPrimary = PlayTorrioPrimitives.white
    val TextPrimary = PlayTorrioPrimitives.white
    val TextSecondary = PlayTorrioPrimitives.neutral400
    val TextTertiary = PlayTorrioPrimitives.neutral600
    val TextDisabled = PlayTorrioPrimitives.neutral700
    val Rating = PlayTorrioPrimitives.rating
    val Error = PlayTorrioPrimitives.error
    val Success = PlayTorrioPrimitives.success
    val Warning = PlayTorrioPrimitives.warning
    val Info = PlayTorrioPrimitives.info
    val Border = PlayTorrioPrimitives.neutral750

    val Secondary: Color
        @Composable
        @ReadOnlyComposable
        get() = PlayTorrioTheme.colors.Secondary

    val SecondaryVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = PlayTorrioTheme.colors.SecondaryVariant

    val OnSecondary: Color
        @Composable
        @ReadOnlyComposable
        get() = PlayTorrioTheme.colors.OnSecondary

    val OnSecondaryVariant: Color
        @Composable
        @ReadOnlyComposable
        get() = PlayTorrioTheme.colors.OnSecondaryVariant

    val FocusRing: Color
        @Composable
        @ReadOnlyComposable
        get() = PlayTorrioTheme.colors.FocusRing

    val FocusBackground: Color
        @Composable
        @ReadOnlyComposable
        get() = PlayTorrioTheme.colors.FocusBackground

    val BorderFocused: Color
        @Composable
        @ReadOnlyComposable
        get() = PlayTorrioTheme.colors.BorderFocused
}
