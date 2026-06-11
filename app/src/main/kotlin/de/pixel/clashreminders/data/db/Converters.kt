package de.pixel.clashreminders.data.db

import androidx.room.TypeConverter
import de.pixel.clashreminders.domain.ReminderType

class Converters {

    @TypeConverter
    fun fromReminderType(value: ReminderType): String = value.name

    @TypeConverter
    fun toReminderType(value: String): ReminderType = ReminderType.valueOf(value)
}
