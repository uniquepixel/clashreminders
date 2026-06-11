package de.pixel.clashreminders.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.pixel.clashreminders.data.db.entity.ClanEntity
import de.pixel.clashreminders.data.db.entity.ClanGamesSnapshotEntity
import de.pixel.clashreminders.data.db.entity.FiredEventEntity
import de.pixel.clashreminders.data.db.entity.ReminderEntity
import de.pixel.clashreminders.data.db.entity.ScheduledAlarmEntity

@Database(
    entities = [
        ClanEntity::class,
        ReminderEntity::class,
        FiredEventEntity::class,
        ClanGamesSnapshotEntity::class,
        ScheduledAlarmEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun clanDao(): ClanDao
    abstract fun reminderDao(): ReminderDao
    abstract fun firedEventDao(): FiredEventDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun scheduledAlarmDao(): ScheduledAlarmDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "clashreminders.db",
                ).build().also { instance = it }
            }
    }
}
