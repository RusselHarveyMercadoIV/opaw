package app.opaw.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface DogDao {
    @Query("SELECT * FROM dogs ORDER BY createdAt")
    fun observeAll(): Flow<List<Dog>>

    @Query("SELECT * FROM dogs WHERE id = :id")
    fun observe(id: String): Flow<Dog?>

    @Query("SELECT * FROM dogs WHERE id = :id")
    suspend fun get(id: String): Dog?

    @Upsert suspend fun upsert(dog: Dog)
    @Delete suspend fun delete(dog: Dog)

    @Query("DELETE FROM dogs")
    suspend fun deleteAll()
}

@Dao
interface CareEventDao {
    @Query("SELECT * FROM care_events WHERE dogId = :dogId ORDER BY date DESC")
    fun observeForDog(dogId: String): Flow<List<CareEvent>>

    @Query("SELECT * FROM care_events WHERE dogId = :dogId AND date >= :from AND date < :to ORDER BY date DESC")
    fun observeBetween(dogId: String, from: Instant, to: Instant): Flow<List<CareEvent>>

    @Query("SELECT * FROM care_events WHERE dogId = :dogId")
    suspend fun allForDog(dogId: String): List<CareEvent>

    @Upsert suspend fun upsert(event: CareEvent)
    @Delete suspend fun delete(event: CareEvent)
}

@Dao
interface WalkDao {
    @Query("SELECT * FROM walks WHERE dogId = :dogId ORDER BY startedAt DESC")
    fun observeForDog(dogId: String): Flow<List<Walk>>

    @Query("SELECT * FROM walks WHERE id = :id")
    suspend fun get(id: String): Walk?

    @Query("SELECT * FROM walks WHERE dogId = :dogId")
    suspend fun allForDog(dogId: String): List<Walk>

    @Upsert suspend fun upsert(walk: Walk)
    @Delete suspend fun delete(walk: Walk)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE dogId = :dogId ORDER BY date DESC")
    fun observeForDog(dogId: String): Flow<List<Memory>>

    @Query("SELECT COUNT(*) FROM memories WHERE dogId = :dogId")
    suspend fun countForDog(dogId: String): Int

    @Upsert suspend fun upsert(memory: Memory)
    @Delete suspend fun delete(memory: Memory)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE dogId = :dogId ORDER BY hour, minute")
    fun observeForDog(dogId: String): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<Reminder>

    @Upsert suspend fun upsert(reminder: Reminder)
    @Delete suspend fun delete(reminder: Reminder)
}

@Dao
interface CustomTrackDao {
    @Query("SELECT * FROM custom_tracks WHERE dogId = :dogId ORDER BY createdAt")
    fun observeForDog(dogId: String): Flow<List<CustomTrack>>

    @Upsert suspend fun upsert(track: CustomTrack)
    @Delete suspend fun delete(track: CustomTrack)
}
