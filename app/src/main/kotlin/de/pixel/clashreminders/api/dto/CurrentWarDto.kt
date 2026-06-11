package de.pixel.clashreminders.api.dto

import kotlinx.serialization.Serializable

/** Shape shared by /clans/{tag}/currentwar and /clanwarleagues/wars/{warTag}. */
@Serializable
data class CurrentWarDto(
    val state: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val attacksPerMember: Int? = null,
    val clan: WarClanDto? = null,
    val opponent: WarClanDto? = null,
) {
    companion object {
        const val STATE_NOT_IN_WAR = "notInWar"
        const val STATE_PREPARATION = "preparation"
        const val STATE_IN_WAR = "inWar"
        const val STATE_WAR_ENDED = "warEnded"
    }
}

@Serializable
data class WarClanDto(
    val tag: String? = null,
    val name: String? = null,
    val members: List<WarMemberDto> = emptyList(),
)

@Serializable
data class WarMemberDto(
    val tag: String,
    val name: String,
    val mapPosition: Int? = null,
    val attacks: List<WarAttackDto> = emptyList(),
)

@Serializable
data class WarAttackDto(
    val order: Int? = null,
)
