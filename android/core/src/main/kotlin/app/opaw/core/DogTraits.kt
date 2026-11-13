package app.opaw.core

import kotlin.math.max
import kotlin.math.min

enum class DogSize(val raw: String) {
    SMALL("small"), MEDIUM("medium"), LARGE("large"), GIANT("giant");

    val title: String get() = raw.replaceFirstChar { it.uppercase() }

    companion object {
        fun fromRaw(raw: String?): DogSize? = entries.firstOrNull { it.raw == raw }
    }
}

enum class EnergyLevel(val raw: String) {
    LOW("low"), MEDIUM("medium"), HIGH("high");

    val title: String get() = raw.replaceFirstChar { it.uppercase() }

    companion object {
        fun fromRaw(raw: String?): EnergyLevel? = entries.firstOrNull { it.raw == raw }
    }
}

enum class LifeStage { PUPPY, ADULT, SENIOR }

/** The traits that personalize goals and tips for a dog. */
data class DogTraits(val size: DogSize, val energy: EnergyLevel, val ageYears: Double) {
    val lifeStage: LifeStage
        get() = when {
            ageYears < 1 -> LifeStage.PUPPY
            ageYears < 8 -> LifeStage.ADULT
            else -> LifeStage.SENIOR
        }

    /**
     * A sensible default daily activity goal in minutes.
     *
     * This is a starting point the owner can edit, not veterinary advice.
     */
    val recommendedDailyActivityMinutes: Int
        get() {
            var minutes = when (energy) {
                EnergyLevel.LOW -> 40.0
                EnergyLevel.MEDIUM -> 60.0
                EnergyLevel.HIGH -> 90.0
            }
            when (size) {
                DogSize.SMALL -> minutes *= 0.8
                DogSize.MEDIUM -> Unit
                DogSize.LARGE -> minutes *= 1.1
                DogSize.GIANT -> minutes *= 0.9
            }
            when (lifeStage) {
                LifeStage.PUPPY -> {
                    // Common rule of thumb: about 5 minutes per month of age, up to twice a day.
                    val months = max(2.0, ageYears * 12)
                    minutes = min(minutes, months * 5 * 2)
                }
                LifeStage.ADULT -> Unit
                LifeStage.SENIOR -> minutes *= 0.7
            }
            // Round to the nearest 5 minutes.
            return roundHalfAway(minutes / 5).toInt() * 5
        }

    /** Weekly distance goal in kilometres, derived from the daily minutes at a relaxed walking pace. */
    val recommendedWeeklyKm: Double
        get() {
            val kmPerMinute = 4.0 / 60.0
            val weekly = recommendedDailyActivityMinutes * 7 * kmPerMinute * 0.6
            return roundHalfAway(weekly * 2) / 2
        }
}

/** Swift's `.rounded()`: halves round away from zero. Kotlin's `round` rounds halves to even. */
internal fun roundHalfAway(x: Double): Double = if (x < 0) -kotlin.math.floor(-x + 0.5) else kotlin.math.floor(x + 0.5)
