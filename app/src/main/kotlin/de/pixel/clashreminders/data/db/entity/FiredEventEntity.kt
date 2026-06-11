package de.pixel.clashreminders.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Duplicate-firing guard, equivalent of the lostmanager fired-event tracking:
 * one row per reminder per concrete event occurrence (war end time, raid week, CG window).
 */
@Entity(
    tableName = "fired_events",
    indices = [Index(value = ["reminderId", "eventKey"], unique = true)],
)
data class FiredEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val eventKey: String,
    val firedAt: Long,
)
