package app.opaw.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.opaw.core.BadgeLevel
import app.opaw.core.CareKind
import app.opaw.core.DogSize
import app.opaw.core.DogTraits
import app.opaw.core.EnergyLevel
import app.opaw.core.GeoPoint
import app.opaw.core.PoopColor
import app.opaw.core.Temperament
import app.opaw.core.PoopConsistency
import app.opaw.core.WalkStats
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

// Mirrors Shared/Models.swift. Raw string values match the iOS app so data can move between them.
// Photos are stored as files under the app's files dir; entities keep their relative paths.

enum class AvatarStatus(val raw: String) {
    /** Only the on-device "living photo" is available. */
    LIVING_PHOTO("livingPhoto"),

    /** Motion clips were requested and are being generated. */
    GENERATING("generating"),

    /** Motion clips are downloaded and ready. */
    READY("ready"),
    FAILED("failed");

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw } ?: LIVING_PHOTO
    }
}

@Entity(tableName = "dogs")
data class Dog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val breed: String = "",
    val birthDate: Instant? = null,
    val weightKg: Double? = null,
    val sizeRaw: String = DogSize.MEDIUM.raw,
    val energyRaw: String = EnergyLevel.MEDIUM.raw,
    /** Usual meal times as hours of the day, for the mood engine. */
    val mealHours: List<Int> = listOf(8, 18),
    /** Usual meal times as minutes after midnight, such as 450 for 07:30. */
    val mealMinutesRaw: List<Int> = emptyList(),
    val dailyGoalMinutes: Int = 60,
    val photoPath: String? = null,
    val cutoutPath: String? = null,
    val avatarStatusRaw: String = AvatarStatus.LIVING_PHOTO.raw,
    val avatarJobId: String? = null,
    val createdAt: Instant = Instant.now(),
    val lastBadgeEarnedAt: Instant? = null,
    val earnedBadgesRaw: List<String> = emptyList(),
    /** The dog's family tree (parents, siblings). */
    val relatives: List<Relative> = emptyList(),
    /** "dog", "cat" or "bunny". */
    val species: String = "dog",
    /** "female", "male", or empty when not set. */
    val sex: String = "",
    val isNeutered: Boolean = false,
    /** Care kinds shown on the Care screen. Empty means all of them. Custom tracks appear as "custom:<id>". */
    val trackedKindsRaw: List<String> = emptyList(),
    /** The order of tracks on the Care screen, as the same keys. Empty means the default order. */
    val trackOrderRaw: List<String> = emptyList(),
    /** The day the pet came home ("gotcha day"). */
    val gotchaDate: Instant? = null,
    val microchip: String = "",
    /** The vet clinic's name. */
    val vetClinic: String = "",
    /** "playful", "calm", "curious", "shy" or "goofy"; empty until the owner picks one. */
    val temperamentRaw: String = "",
) {
    /** Meal times as minutes after midnight, sorted. */
    val mealTimes: List<Int>
        get() = if (mealMinutesRaw.isEmpty()) mealHours.sorted().map { it * 60 } else mealMinutesRaw.sorted()

    /** Returns a copy with new meal times; also updates [mealHours]. */
    fun withMealTimes(minutes: List<Int>): Dog {
        val sorted = minutes.sorted()
        return copy(mealMinutesRaw = sorted, mealHours = sorted.map { it / 60 })
    }

    /** Whether a care kind (or the "walks" key) is tracked; an empty list tracks everything. */
    fun tracks(key: String): Boolean = trackedKindsRaw.isEmpty() || key in trackedKindsRaw

    /** Meal times as hours: the daily meal reminders when there are any, otherwise the times picked in setup. */
    fun scheduledMealHours(reminders: List<Reminder>): List<Int> {
        val hours = reminders.filter { it.kind == CareKind.MEAL && it.repeats == ReminderRepeat.DAILY && it.isEnabled }.map { it.hour }
        return if (hours.isEmpty()) mealHours else hours.sorted()
    }

    val size: DogSize get() = DogSize.fromRaw(sizeRaw) ?: DogSize.MEDIUM
    val energy: EnergyLevel get() = EnergyLevel.fromRaw(energyRaw) ?: EnergyLevel.MEDIUM
    /** The owner's pick, else a guess from energy. */
    val temperament: Temperament get() = Temperament.fromRaw(temperamentRaw) ?: Temperament.guess(energy)
    val avatarStatus: AvatarStatus get() = AvatarStatus.fromRaw(avatarStatusRaw)
    /** Badge levels held (design 2f): `rambler.silver` and so on. */
    val earnedBadges: Set<BadgeLevel> get() = earnedBadgesRaw.mapNotNull(BadgeLevel::fromRaw).toSet()
    /** Holds only badges from before bronze, silver and gold, so the first refresh awards levels quietly. */
    val hasLegacyBadges: Boolean get() = earnedBadgesRaw.isNotEmpty() && earnedBadgesRaw.none { '.' in it }
    val ageYears: Double get() = age(birthDate)
    val traits: DogTraits get() = DogTraits(size, energy, ageYears)

    companion object {
        fun age(birthDate: Instant?): Double {
            birthDate ?: return 3.0
            return maxOf(0.0, Duration.between(birthDate, Instant.now()).seconds / (365.25 * 86_400))
        }

        fun create(
            name: String,
            breed: String = "",
            birthDate: Instant? = null,
            weightKg: Double? = null,
            size: DogSize = DogSize.MEDIUM,
            energy: EnergyLevel = EnergyLevel.MEDIUM,
        ) = Dog(
            name = name,
            breed = breed,
            birthDate = birthDate,
            weightKg = weightKg,
            sizeRaw = size.raw,
            energyRaw = energy.raw,
            dailyGoalMinutes = DogTraits(size, energy, age(birthDate)).recommendedDailyActivityMinutes,
        )
    }
}

@Entity(tableName = "care_events", foreignKeys = [ForeignKey(Dog::class, ["id"], ["dogId"], onDelete = ForeignKey.CASCADE)], indices = [Index("dogId"), Index("date")])
data class CareEvent(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val dogId: String,
    val kindRaw: String = CareKind.MEAL.raw,
    val date: Instant = Instant.now(),
    val note: String = "",
    val poopConsistencyRaw: String? = null,
    val poopColorRaw: String? = null,
    val weightKg: Double? = null,
    /** Who logged it, such as "You" or a walker's name. */
    val loggedBy: String = "",
    /** For a custom track's log: the track, and its name when logged. */
    val trackId: String? = null,
    val trackName: String = "",
    /** For a custom track logged as an amount. */
    val amount: Double? = null,
) {
    val kind: CareKind get() = CareKind.fromRaw(kindRaw) ?: CareKind.MEAL
    val poopConsistency: PoopConsistency? get() = PoopConsistency.fromRaw(poopConsistencyRaw)
    val poopColor: PoopColor? get() = PoopColor.fromRaw(poopColorRaw)
}

/** A pee or poop dropped on the map during a walk. */
@Serializable
data class WalkPin(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val latitude: Double,
    val longitude: Double,
    @Serializable(with = InstantSerializer::class) val date: Instant,
)

@Entity(tableName = "walks", foreignKeys = [ForeignKey(Dog::class, ["id"], ["dogId"], onDelete = ForeignKey.CASCADE)], indices = [Index("dogId"), Index("startedAt")])
data class Walk(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val dogId: String,
    val startedAt: Instant = Instant.now(),
    val endedAt: Instant = Instant.now(),
    val distanceMeters: Double = 0.0,
    val movingSeconds: Double = 0.0,
    val route: List<GeoPoint> = emptyList(),
    val pins: List<WalkPin> = emptyList(),
    val note: String = "",
    val sharedToPack: Boolean = false,
    /** "walk", "run" or "hike". */
    val activityRaw: String = "walk",
    val isFavorite: Boolean = false,
) {
    val elapsedSeconds: Double get() = Duration.between(startedAt, endedAt).toMillis() / 1000.0
    val stats: WalkStats get() = WalkStats(distanceMeters, movingSeconds, elapsedSeconds)

    companion object {
        fun create(dogId: String, startedAt: Instant, endedAt: Instant, route: List<GeoPoint>, pins: List<WalkPin>): Walk {
            val stats = app.opaw.core.WalkCalculator.stats(route)
            return Walk(
                dogId = dogId,
                startedAt = startedAt,
                endedAt = endedAt,
                distanceMeters = stats.distanceMeters,
                movingSeconds = stats.movingSeconds,
                route = route,
                pins = pins,
            )
        }
    }
}

@Entity(tableName = "memories", foreignKeys = [ForeignKey(Dog::class, ["id"], ["dogId"], onDelete = ForeignKey.CASCADE)], indices = [Index("dogId"), Index("date")])
data class Memory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val dogId: String,
    val date: Instant = Instant.now(),
    val caption: String = "",
    val photoPath: String? = null,
    val walkId: String? = null,
    val isFavorite: Boolean = false,
)

enum class ReminderRepeat(val raw: String, val title: String) {
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
    EVERY_THREE_WEEKS("everyThreeWeeks", "Every 3 weeks"),
    MONTHLY("monthly", "Monthly");

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw } ?: DAILY
    }
}

@Entity(tableName = "reminders", foreignKeys = [ForeignKey(Dog::class, ["id"], ["dogId"], onDelete = ForeignKey.CASCADE)], indices = [Index("dogId")])
data class Reminder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val dogId: String,
    val title: String = "",
    val kindRaw: String = CareKind.MEAL.raw,
    val hour: Int = 8,
    val minute: Int = 0,
    val isEnabled: Boolean = true,
    /** A short detail, such as "with food". */
    val note: String = "",
    val repeatRaw: String = ReminderRepeat.DAILY.raw,
    /** The first day of a repeating reminder that isn't daily. */
    val startDate: Instant = Instant.now(),
    /** Set when the reminder belongs to a custom track. */
    val trackId: String? = null,
) {
    val kind: CareKind get() = CareKind.fromRaw(kindRaw) ?: CareKind.MEAL
    val repeats: ReminderRepeat get() = ReminderRepeat.fromRaw(repeatRaw)

    /** The next day on or after [day] this reminder falls on. */
    fun nextOccurrence(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val start = startDate.atZone(zone).toLocalDate()
        if (repeats == ReminderRepeat.DAILY || !day.isAfter(start)) return maxOf(start, day)
        return when (repeats) {
            ReminderRepeat.DAILY -> day
            ReminderRepeat.MONTHLY -> {
                var months = ChronoUnit.MONTHS.between(start, day)
                while (start.plusMonths(months).isBefore(day)) months += 1
                start.plusMonths(months)
            }
            ReminderRepeat.WEEKLY, ReminderRepeat.EVERY_THREE_WEEKS -> {
                val step = if (repeats == ReminderRepeat.WEEKLY) 7 else 21
                val days = ChronoUnit.DAYS.between(start, day)
                val periods = (days + step - 1) / step
                start.plusDays(periods * step)
            }
        }
    }
}

/** How a custom track is logged. */
enum class TrackLogStyle(val raw: String, val title: String) {
    /** Once a day, tap again to undo. */
    DONE("done", "Done or not"),

    /** Each tap adds one. */
    COUNT("count", "Count"),

    /** Each log asks for a number. */
    AMOUNT("amount", "Amount");

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw } ?: DONE
    }
}

/** A care track the owner made on the Care screen, such as "Brushing teeth". */
@Entity(tableName = "custom_tracks", foreignKeys = [ForeignKey(Dog::class, ["id"], ["dogId"], onDelete = ForeignKey.CASCADE)], indices = [Index("dogId")])
data class CustomTrack(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val dogId: String,
    val name: String = "",
    /** A design icon name. */
    val icon: String = "sparkle",
    val logStyleRaw: String = TrackLogStyle.DONE.raw,
    val createdAt: Instant = Instant.now(),
) {
    val logStyle: TrackLogStyle get() = TrackLogStyle.fromRaw(logStyleRaw)

    /** The key used in a dog's tracked and order lists. Upper case to match iOS `uuidString`. */
    val key: String get() = "custom:${id.uppercase()}"
}

/** A member of a dog's family tree. */
@Serializable
data class Relative(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: Role,
    /** Where they live, or "on the app". */
    val place: String = "",
    val photoPath: String? = null,
) {
    @Serializable
    enum class Role(val title: String) {
        @kotlinx.serialization.SerialName("mom") MOM("Mom"),
        @kotlinx.serialization.SerialName("dad") DAD("Dad"),
        @kotlinx.serialization.SerialName("brother") BROTHER("Brother"),
        @kotlinx.serialization.SerialName("sister") SISTER("Sister"),
    }
}
