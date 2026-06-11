package de.pixel.clashreminders.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class RaidSeasonsDto(
    val items: List<RaidSeasonDto> = emptyList(),
)

@Serializable
data class RaidSeasonDto(
    val state: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val members: List<RaidMemberDto> = emptyList(),
) {
    companion object {
        const val STATE_ONGOING = "ongoing"
    }
}

@Serializable
data class RaidMemberDto(
    val tag: String,
    val name: String,
    val attacks: Int = 0,
    /** Defaults mirror lostmanager Clan.java: 6 regular + 0 bonus when absent. */
    val attackLimit: Int = 6,
    val bonusAttackLimit: Int = 0,
)
