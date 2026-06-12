package de.pixel.clashreminders.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlayerDto(
    val tag: String? = null,
    val name: String? = null,
    val townHallLevel: Int? = null,
    val clan: PlayerClanDto? = null,
    val achievements: List<AchievementDto> = emptyList(),
) {
    /** "Games Champion" achievement value = lifetime clan games points. */
    fun clanGamesPoints(): Int? =
        achievements.firstOrNull { it.name == ACHIEVEMENT_GAMES_CHAMPION }?.value

    companion object {
        const val ACHIEVEMENT_GAMES_CHAMPION = "Games Champion"
    }
}

@Serializable
data class PlayerClanDto(
    val tag: String? = null,
    val name: String? = null,
    val badgeUrls: BadgeUrlsDto? = null,
)

@Serializable
data class AchievementDto(
    val name: String? = null,
    val value: Int = 0,
)
