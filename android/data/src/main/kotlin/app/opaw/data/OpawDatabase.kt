package app.opaw.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Dog::class, CareEvent::class, Walk::class, Memory::class, Reminder::class, CustomTrack::class],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class OpawDatabase : RoomDatabase() {
    abstract fun dogs(): DogDao
    abstract fun careEvents(): CareEventDao
    abstract fun walks(): WalkDao
    abstract fun memories(): MemoryDao
    abstract fun reminders(): ReminderDao
    abstract fun customTracks(): CustomTrackDao

    companion object {
        @Volatile private var instance: OpawDatabase? = null

        /** One database for the app, the widget and the walk service, like the iOS shared container. */
        fun get(context: Context): OpawDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OpawDatabase::class.java, "opaw.db")
                .build()
                .also { instance = it }
        }
    }
}
