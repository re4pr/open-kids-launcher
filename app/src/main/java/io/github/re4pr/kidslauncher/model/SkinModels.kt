package io.github.re4pr.kidslauncher.model

import kotlinx.serialization.Serializable

@Serializable
enum class ParticleStyle {
    SNOWFLAKES,
    RAINBOW_SPARKLES,
    CROWNS_AND_HEARTS,
    OCEAN_BUBBLES,
    STARLIGHT
}

@Serializable
data class SkinDefinition(
    val id: String,
    val name: String,
    val tagline: String = "",
    val skyTopHex: String,
    val skyMidHex: String,
    val skyBottomHex: String,
    val surfaceCardHex: String,
    val accentPrimaryHex: String,
    val accentSecondaryHex: String,
    val badgeHighlightHex: String,
    val textPrimaryHex: String,
    val textOnSkyHex: String = "#FFFFFF",
    val particleStyle: ParticleStyle = ParticleStyle.RAINBOW_SPARKLES,
    val cardCornerDp: Int = 28,
    val isBuiltIn: Boolean = true,
)
