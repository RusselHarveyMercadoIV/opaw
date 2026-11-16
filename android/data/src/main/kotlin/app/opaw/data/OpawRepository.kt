package app.opaw.data

import androidx.room.withTransaction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** Reads and writes whole dogs, the way the iOS views use SwiftData's relationships. */
class OpawRepository(private val db: OpawDatabase) {
    /** Told after a pet, log or walk is saved or deleted here, so a shared pet can sync. */
    var onChange: (() -> Unit)? = null
    val dogs: Flow<List<Dog>> = db.dogs().observeAll()

    /** A dog and everything it owns, updating whenever any of it changes. */
    fun dogData(dogId: String): Flow<DogData?> = combine(
        db.dogs().observe(dogId),
        db.careEvents().observeForDog(dogId),
        db.walks().observeForDog(dogId),
        db.memories().observeForDog(dogId),
        combine(db.reminders().observeForDog(dogId), db.customTracks().observeForDog(dogId)) { r, t -> r to t },
    ) { dog, events, walks, memories, (reminders, tracks) ->
        dog?.let { DogData(it, events, walks, memories, reminders, tracks) }
    }

    /** Every dog with its data. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val allDogData: Flow<List<DogData>> = dogs.flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyList()) else combine(list.map { dogData(it.id) }) { array -> array.filterNotNull() }
    }

    /** A one-off read of a dog and its data. */
    suspend fun loadDogData(dogId: String): DogData? {
        val dog = db.dogs().get(dogId) ?: return null
        return DogData(
            dog,
            db.careEvents().allForDog(dogId),
            db.walks().allForDog(dogId),
            db.memories().allForDog(dogId),
            db.reminders().allForDog(dogId),
            db.customTracks().allForDog(dogId),
        )
    }

    suspend fun saveDog(dog: Dog) = db.dogs().upsert(dog).also { onChange?.invoke() }
    suspend fun saveEvent(event: CareEvent) = db.careEvents().upsert(event).also { onChange?.invoke() }
    suspend fun deleteEvent(event: CareEvent) = db.careEvents().delete(event).also { onChange?.invoke() }
    suspend fun event(id: String): CareEvent? = db.careEvents().get(id)
    suspend fun saveReminder(reminder: Reminder) = db.reminders().upsert(reminder)
    suspend fun deleteReminder(reminder: Reminder) = db.reminders().delete(reminder)
    suspend fun allReminders(): List<Reminder> = db.reminders().all()
    suspend fun saveTrack(track: CustomTrack) = db.customTracks().upsert(track)
    suspend fun deleteTrack(track: CustomTrack) = db.customTracks().delete(track)
    suspend fun walk(id: String): Walk? = db.walks().get(id)
    suspend fun saveWalk(walk: Walk) = db.walks().upsert(walk).also { onChange?.invoke() }
    suspend fun deleteWalk(walk: Walk) = db.walks().delete(walk).also { onChange?.invoke() }
    suspend fun memory(id: String): Memory? = db.memories().get(id)
    suspend fun saveMemory(memory: Memory) = db.memories().upsert(memory)
    suspend fun deleteMemory(memory: Memory) = db.memories().delete(memory)
    suspend fun allDogs(): List<Dog> = db.dogs().all()

    suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction(block)
}
