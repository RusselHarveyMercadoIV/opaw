package app.opaw.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Dog::class, CareEvent::class, Walk::class, Memory::class, Reminder::class, CustomTrack::class],
    version = 2,
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
        /** Version 2 adds the profile's gotcha day, microchip number and vet clinic. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE dogs ADD COLUMN gotchaDate INTEGER")
                db.execSQL("ALTER TABLE dogs ADD COLUMN microchip TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE dogs ADD COLUMN vetClinic TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile private var instance: OpawDatabase? = null

        /** One database for the app, the widget and the walk service, like the iOS shared container. */
        fun get(context: Context): OpawDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, OpawDatabase::class.java, "opaw.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
