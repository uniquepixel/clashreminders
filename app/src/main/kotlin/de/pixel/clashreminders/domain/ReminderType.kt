package de.pixel.clashreminders.domain

/**
 * Port of the lostmanager LISTENINGTYPE enum, reduced to the informational
 * reminder types that make sense as local notifications.
 */
enum class ReminderType {
    /** Fires once when a clan war becomes visible (notInWar/warEnded -> preparation/inWar). */
    WAR_START,

    /** Fires [offsetMinutes] before the war end with the list of open attacks. */
    WAR_END,

    /** Fires [offsetMinutes] before the end of the current CWL war day. */
    CWL_DAY_END,

    /** Fires at a fixed weekend day + local time during the raid weekend. */
    RAID,

    /** Fires [offsetMinutes] before the clan games window ends (28th 12:00 UTC). */
    CLAN_GAMES_END,
}
