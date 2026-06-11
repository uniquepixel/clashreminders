package de.pixel.clashreminders.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class LeagueGroupDto(
    val state: String? = null,
    val season: String? = null,
    val rounds: List<RoundDto> = emptyList(),
)

@Serializable
data class RoundDto(
    val warTags: List<String> = emptyList(),
)
