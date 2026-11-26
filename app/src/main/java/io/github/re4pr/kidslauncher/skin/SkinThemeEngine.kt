package io.github.re4pr.kidslauncher.skin

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.re4pr.kidslauncher.model.LauncherMode
import io.github.re4pr.kidslauncher.model.ParticleStyle
import io.github.re4pr.kidslauncher.model.SkinDefinition

data class ResolvedSkinPalette(
    val id: String,
    val displayName: String,
    val tagline: String,
    val skyTop: Color,
    val skyMid: Color,
    val skyBottom: Color,
    val surfaceCard: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val badgeHighlight: Color,
    val textPrimary: Color,
    val textOnSky: Color,
    val particleStyle: ParticleStyle,
    val cardCornerDp: Int,
)

object SkinThemeEngine {
    fun resolvePalette(
        skin: SkinDefinition,
        mode: LauncherMode,
        customLabels: Map<String, String>,
    ): ResolvedSkinPalette {
        val baseTop = parseHexColor(skin.skyTopHex, Color(0xFF0F3866))
        val baseMid = parseHexColor(skin.skyMidHex, Color(0xFF2374AB))
        val baseBottom = parseHexColor(skin.skyBottomHex, Color(0xFFA8E6FF))
        val baseCard = parseHexColor(skin.surfaceCardHex, Color(0xFFF5FBFF))
        val basePrimary = parseHexColor(skin.accentPrimaryHex, Color(0xFF0096D6))
        val baseSecondary = parseHexColor(skin.accentSecondaryHex, Color(0xFF7CE0FF))
        val baseBadge = parseHexColor(skin.badgeHighlightHex, Color(0xFF52B7FF))
        val baseText = parseHexColor(skin.textPrimaryHex, Color(0xFF0A2742))
        val baseOnSky = parseHexColor(skin.textOnSkyHex, Color.White)

        val label = customLabels[skin.id]?.takeIf { it.isNotBlank() } ?: skin.name

        return when (mode) {
            LauncherMode.FREE_PLAY -> ResolvedSkinPalette(
                id = skin.id,
                displayName = label,
                tagline = skin.tagline,
                skyTop = baseTop,
                skyMid = baseMid,
                skyBottom = baseBottom,
                surfaceCard = baseCard,
                accentPrimary = basePrimary,
                accentSecondary = baseSecondary,
                badgeHighlight = baseBadge,
                textPrimary = baseText,
                textOnSky = baseOnSky,
                particleStyle = skin.particleStyle,
                cardCornerDp = skin.cardCornerDp,
            )
            LauncherMode.SCHOOL_TIME -> ResolvedSkinPalette(
                id = skin.id,
                displayName = label,
                tagline = "Focused learning atmosphere",
                skyTop = lerp(baseTop, Color(0xFF1E4D6B), 0.55f),
                skyMid = lerp(baseMid, Color(0xFF3E8E9E), 0.55f),
                skyBottom = lerp(baseBottom, Color(0xFFD9F4EE), 0.55f),
                surfaceCard = Color(0xFFF7FCFA),
                accentPrimary = lerp(basePrimary, Color(0xFF1B7F79), 0.5f),
                accentSecondary = Color(0xFFFFC857),
                badgeHighlight = Color(0xFF2EC4B6),
                textPrimary = Color(0xFF103742),
                textOnSky = Color.White,
                particleStyle = skin.particleStyle,
                cardCornerDp = skin.cardCornerDp,
            )
            LauncherMode.TIDY_UP -> ResolvedSkinPalette(
                id = skin.id,
                displayName = label,
                tagline = "Sparkle & tidy-up boost!",
                skyTop = Color(0xFF9C3D14),
                skyMid = Color(0xFFE76F51),
                skyBottom = Color(0xFFFFE5B4),
                surfaceCard = Color(0xFFFFFBF2),
                accentPrimary = Color(0xFFE65100),
                accentSecondary = Color(0xFF2A9D8F),
                badgeHighlight = Color(0xFFFFB703),
                textPrimary = Color(0xFF432818),
                textOnSky = Color.White,
                particleStyle = ParticleStyle.RAINBOW_SPARKLES,
                cardCornerDp = skin.cardCornerDp,
            )
            LauncherMode.BEDTIME, LauncherMode.LOCKED -> ResolvedSkinPalette(
                id = skin.id,
                displayName = label,
                tagline = "Starlight bedtime sky",
                skyTop = Color(0xFF090D24),
                skyMid = Color(0xFF18224B),
                skyBottom = Color(0xFF2D3970),
                surfaceCard = Color(0xFF1E2749),
                accentPrimary = Color(0xFF8ECAE6),
                accentSecondary = Color(0xFFFFD166),
                badgeHighlight = Color(0xFF9B89FF),
                textPrimary = Color(0xFFF1F5FF),
                textOnSky = Color(0xFFF8FAFF),
                particleStyle = ParticleStyle.STARLIGHT,
                cardCornerDp = skin.cardCornerDp,
            )
        }
    }

    fun parseHexColor(hex: String, fallback: Color): Color = runCatching {
        val cleaned = hex.trim().removePrefix("#")
        val argb = when (cleaned.length) {
            6 -> ("FF$cleaned").toLong(16)
            8 -> cleaned.toLong(16)
            else -> return fallback
        }
        Color(argb)
    }.getOrDefault(fallback)
}
