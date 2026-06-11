package de.pixel.clashreminders.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClanDto(
    val tag: String,
    val name: String,
    val badgeUrls: BadgeUrlsDto? = null,
    val memberList: List<ClanMemberDto> = emptyList(),
)

@Serializable
data class BadgeUrlsDto(
    val small: String? = null,
    val medium: String? = null,
)

@Serializable
data class ClanMemberDto(
    val tag: String,
    val name: String,
)
