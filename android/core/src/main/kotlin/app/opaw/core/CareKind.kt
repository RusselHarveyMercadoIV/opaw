package app.opaw.core

/** Every kind of care event a dog owner can log. Raw values match the iOS app and Supabase. */
enum class CareKind(val raw: String, val title: String, val emoji: String) {
    MEAL("meal", "Meal", "🍖"),
    WATER("water", "Water", "💧"),
    PEE("pee", "Pee", "💛"),
    POOP("poop", "Poop", "💩"),
    BATH("bath", "Bath", "🛁"),
    BRUSH("brush", "Brush", "🪮"),
    NAILS("nails", "Nails", "✂️"),
    MEDS("meds", "Meds", "💊"),
    VET("vet", "Vet visit", "🩺"),
    WEIGHT("weight", "Weight", "⚖️"),
    PLAY("play", "Play", "🎾"),

    /** A track the owner made, such as "Brushing teeth". The event carries its name. */
    CUSTOM("custom", "Custom", "✨");

    /** Whether this kind usually happens several times a day. */
    val isDaily: Boolean
        get() = when (this) {
            MEAL, WATER, PEE, POOP, PLAY -> true
            else -> false
        }

    companion object {
        fun fromRaw(raw: String?): CareKind? = entries.firstOrNull { it.raw == raw }

        /** The kinds shown as one-tap buttons on Home and in the widget. */
        val quickLog: List<CareKind> = listOf(MEAL, WATER, PEE, POOP)

        /** Every kind that can be picked on its own; custom logs belong to a custom track. */
        val standard: List<CareKind> = entries.filter { it != CUSTOM }
    }
}

/** Optional detail for poop logs, useful for spotting health issues. */
enum class PoopConsistency(val raw: String, val title: String) {
    HARD("hard", "Hard"),
    FIRM("firm", "Firm"),
    NORMAL("normal", "Normal"),
    SOFT("soft", "Soft"),
    RUNNY("runny", "Runny"),
    BLOODY("bloody", "Blood visible");

    /** Whether this consistency deserves a gentle "keep an eye on it" hint. */
    val isConcerning: Boolean
        get() = when (this) {
            FIRM, NORMAL, SOFT -> false
            RUNNY, HARD, BLOODY -> true
        }

    companion object {
        fun fromRaw(raw: String?): PoopConsistency? = entries.firstOrNull { it.raw == raw }

        /** The choices shown when logging; blood is logged as a color instead. */
        val pickerCases: List<PoopConsistency> = listOf(HARD, FIRM, NORMAL, SOFT, RUNNY)
    }
}

enum class PoopColor(val raw: String, val title: String) {
    BROWN("brown", "Brown"),
    YELLOW("yellow", "Yellow"),
    GREEN("green", "Green"),
    BLACK("black", "Black"),
    RED_TINGE("redTinge", "Red tinge");

    /** Black or red stool can mean bleeding; worth a call to the vet. */
    val isConcerning: Boolean get() = this == BLACK || this == RED_TINGE

    companion object {
        fun fromRaw(raw: String?): PoopColor? = entries.firstOrNull { it.raw == raw }
    }
}
