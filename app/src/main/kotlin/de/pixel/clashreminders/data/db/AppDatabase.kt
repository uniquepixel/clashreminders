package de.pixel.clashreminders.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import de.pixel.clashreminders.data.db.entity.AccountEntity
import de.pixel.clashreminders.data.db.entity.ClanGamesSnapshotEntity
import de.pixel.clashreminders.data.db.entity.FiredEventEntity
import de.pixel.clashreminders.data.db.entity.ReminderEntity
import de.pixel.clashreminders.data.db.entity.ScheduledAlarmEntity
import de.pixel.clashreminders.data.db.entity.TrackedClanEntity

@Database(
    entities = [
        AccountEntity::class,
        TrackedClanEntity::class,
        ReminderEntity::class,
        FiredEventEntity::class,
        ClanGamesSnapshotEntity::class,
        ScheduledAlarmEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun trackedClanDao(): TrackedClanDao
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
                )
                    // v1 -> v2 switched from clan-based to account-based tracking;
                    // the old clan/reminder rows have no meaning in the new model.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
