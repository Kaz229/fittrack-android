package com.kaz229.fittrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kaz229.fittrack.data.ThemeChoice

/**
 * Palette « bento » : un seul accent vif, trois teintes sourdes pour les macros.
 *
 * Les accents ne sont pas les mêmes en clair et en sombre. Un vert acide conçu pour le
 * noir tombe à 1,4:1 sur blanc : il est remplacé par sa version assombrie. Chaque
 * couleur de texte tient le contraste AA (4,5:1) sur son fond.
 */
object Bento {
    // Sombre
    private val DarkBg = Color(0xFF0A0A0A)
    private val DarkCard = Color(0xFF151515)
    private val DarkBorder = Color(0xFF282828)
    private val DarkMuted = Color(0xFF9A9A9A)
    private val DarkTrack = Color(0xFF2E2E2E)
    private val AcidGreen = Color(0xFFCCFF00)

    // Clair
    private val LightBg = Color(0xFFF2F2EE)
    private val LightCard = Color(0xFFFFFFFF)
    private val LightBorder = Color(0xFFE2E2DC)
    private val LightMuted = Color(0xFF666660)
    private val LightTrack = Color(0xFFEDEDE7)
    private val DeepGreen = Color(0xFF4D6B00)

    val darkScheme = darkColorScheme(
        primary = AcidGreen,
        onPrimary = DarkBg,
        background = DarkBg,
        onBackground = Color.White,
        surface = DarkCard,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1E1E1E),
        onSurfaceVariant = DarkMuted,
        outline = DarkBorder,
        outlineVariant = DarkTrack,
        error = Color(0xFFFF6B6B),
    )

    val lightScheme = lightColorScheme(
        primary = DeepGreen,
        onPrimary = Color.White,
        background = LightBg,
        onBackground = Color(0xFF121212),
        surface = LightCard,
        onSurface = Color(0xFF121212),
        surfaceVariant = Color(0xFFFBFBF9),
        onSurfaceVariant = LightMuted,
        outline = LightBorder,
        outlineVariant = LightTrack,
        error = Color(0xFFB3261E),
    )
}

/**
 * Teintes des macros, hors du schéma Material : trois couleurs distinctes mais
 * sourdes, pour qu'aucune ne vole la vedette à l'accent des calories.
 */
data class MacroColors(val protein: Color, val carbs: Color, val fat: Color)

private val DarkMacros = MacroColors(
    protein = Color(0xFF3DDC97),
    carbs = Color(0xFFFFB44D),
    fat = Color(0xFFB98AFF),
)

private val LightMacros = MacroColors(
    protein = Color(0xFF00765A),
    carbs = Color(0xFF9A5800),
    fat = Color(0xFF6B35C4),
)

val LocalMacroColors = staticCompositionLocalOf { DarkMacros }

/** Chiffres larges et serrés pour les valeurs qu'on lit de loin. */
private val BentoTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.5).sp,
        ),
        headlineLarge = base.headlineLarge.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun FitTrackTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (choice) {
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalMacroColors provides if (dark) DarkMacros else LightMacros) {
        MaterialTheme(
            colorScheme = if (dark) Bento.darkScheme else Bento.lightScheme,
            typography = BentoTypography,
            content = content,
        )
    }
}
