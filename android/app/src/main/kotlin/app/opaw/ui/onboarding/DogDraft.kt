package app.opaw.ui.onboarding

import android.content.Context
import android.graphics.Bitmap
import app.opaw.OpawApplication
import app.opaw.core.BreedGuess
import app.opaw.core.CareKind
import app.opaw.core.DogSize
import app.opaw.core.EnergyLevel
import app.opaw.data.Dog
import app.opaw.data.Reminder
import app.opaw.services.PhotoStore
import app.opaw.ui.care.TrackOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything the photo step learned. */
data class CaptureResult(
    val original: Bitmap? = null,
    val cutout: Bitmap? = null,
    val guess: BreedGuess? = null,
)

/** Editable fields for a dog before it's saved. Mirrors `DogDraft` in OnboardingView.swift. */
data class DogDraft(
    val name: String = "",
    val breed: String = "",
    val hasBirthDate: Boolean = false,
    val birthDate: LocalDate = LocalDate.now().minusYears(2),
    val weightKg: Double? = null,
    val size: DogSize = DogSize.MEDIUM,
    val energy: EnergyLevel = EnergyLevel.MEDIUM,
    val species: String = "dog",
    val sex: String = "",
    val isNeutered: Boolean = false,
    /** Meal times as minutes after midnight, such as 450 for 07:30. */
    val mealTimes: List<Int> = listOf(450, 1080),
    /** Care kinds to show in Care; empty means all. */
    val trackedKinds: List<String> = defaultTracked,
) {
    val trimmedName: String get() = name.trim()
    val isValid: Boolean get() = trimmedName.isNotEmpty()

    /** The name, or "your dog" before one is typed. */
    val displayName: String get() = if (isValid) trimmedName else "your dog"

    val birthInstant: Instant? get() = if (hasBirthDate) birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant() else null

    fun makeDog(): Dog {
        var dog = Dog.create(trimmedName, breed.trim(), birthInstant, weightKg, size, energy).copy(
            species = species,
            sex = sex,
            isNeutered = isNeutered,
            trackedKindsRaw = trackedKinds,
        )
        if (mealTimes.isNotEmpty()) dog = dog.withMealTimes(mealTimes)
        return dog
    }

    /** Saves the dog with its photos and starter meal reminders, and makes it the active dog. */
    suspend fun save(context: Context, photo: Bitmap?, cutout: Bitmap?): Dog {
        val app = context.applicationContext as OpawApplication
        var dog = makeDog()
        dog = dog.copy(
            photoPath = photo?.let { PhotoStore.save(context, it, "${dog.id}-photo.jpg", png = false) },
            cutoutPath = cutout?.let { PhotoStore.save(context, it, "${dog.id}-cutout.png", png = true) },
        )
        app.database.dogs().upsert(dog)
        val tracksMeals = trackedKinds.isEmpty() || CareKind.MEAL.raw in trackedKinds
        if (tracksMeals) {
            for (time in mealTimes.sorted()) {
                app.database.reminders().upsert(
                    Reminder(dogId = dog.id, title = "${dog.name}'s meal", kindRaw = CareKind.MEAL.raw, hour = time / 60, minute = time % 60),
                )
            }
        }
        app.settings.setActiveDog(dog.id)
        app.opaw.services.NotificationService.reschedule(context)
        return dog
    }

    companion object {
        val defaultTracked: List<String> =
            listOf(CareKind.MEAL, CareKind.WATER, CareKind.PEE, CareKind.POOP, CareKind.WEIGHT).map { it.raw } + TrackOption.WALK_KEY
    }
}

/** "Up to about 10 kg. Lap certified." */
val DogSize.hint: String
    get() = when (this) {
        DogSize.SMALL -> "Up to about 10 kg. Lap certified."
        DogSize.MEDIUM -> "About 10 to 25 kg. Just right."
        DogSize.LARGE -> "About 25 to 45 kg. Big hugs."
        DogSize.GIANT -> "Over 45 kg. Mostly heart."
    }
