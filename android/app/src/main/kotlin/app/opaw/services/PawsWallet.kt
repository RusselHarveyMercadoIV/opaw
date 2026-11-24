package app.opaw.services

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import app.opaw.core.CareKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The spots in a pet's room that can hold a piece (16b). */
enum class RoomSlot(val title: String) { BED("Bed"), BOWL("Bowl"), FOOD("Food"), WALL("Wall art"), RUG("Rug"), PLANT("Plant"), TOY("Toy") }

/** The shop's tabs, each covering one or more spots. Treats have no spot: they're given, not placed. */
enum class ShopTab(val title: String, val slots: List<RoomSlot>) {
    BEDS("Beds", listOf(RoomSlot.BED)),
    BOWLS("Bowls", listOf(RoomSlot.BOWL)),
    FOOD("Food", listOf(RoomSlot.FOOD)),
    TREATS("Treats", emptyList()),
    WALLS("Walls", listOf(RoomSlot.WALL)),
    FLOORS("Floors", listOf(RoomSlot.RUG, RoomSlot.PLANT)),
    TOYS("Toys", listOf(RoomSlot.TOY)),
}

/** One piece for the room: [price] in Paws, unlocked at [level], or Premium only. */
class RoomItem(val id: String, val slot: RoomSlot, val title: String, val price: Int, val level: Int = 1, val premium: Boolean = false)

/** Everything the shop sells. The first piece of each spot is the starter, already in the room. */
object RoomCatalog {
    /** The pieces, named as in the design's art (see RoomArt). */
    val items = listOf(
        RoomItem("bed-donut", RoomSlot.BED, "Orange donut", 0),
        RoomItem("bed-cloud", RoomSlot.BED, "Cloud bed", 120),
        RoomItem("bed-teepee", RoomSlot.BED, "Teepee", 200, level = 6),
        RoomItem("bed-royal", RoomSlot.BED, "Royal bed", 0, premium = true),
        RoomItem("bowl-food", RoomSlot.BOWL, "Orange bowl", 0),
        RoomItem("bowl-bone", RoomSlot.BOWL, "Bone bowls", 90, level = 3),
        // Food (16e): a can in the shop, and that food in the bowl. Bought once and kept.
        RoomItem("food-kibble", RoomSlot.FOOD, "Kibble", 0),
        RoomItem("food-salmon", RoomSlot.FOOD, "Salmon bites", 60),
        RoomItem("food-chicken", RoomSlot.FOOD, "Chicken and rice", 80),
        RoomItem("food-stew", RoomSlot.FOOD, "Beef stew", 120, level = 4),
        RoomItem("food-steak", RoomSlot.FOOD, "Steak dinner", 0, premium = true),
        RoomItem("wall-none", RoomSlot.WALL, "Bare wall", 0),
        RoomItem("art-portrait", RoomSlot.WALL, "Paw portrait", 70),
        RoomItem("art-sunset", RoomSlot.WALL, "Sunset", 100, level = 3),
        RoomItem("rug-round", RoomSlot.RUG, "Round rug", 0),
        RoomItem("rug-paw", RoomSlot.RUG, "Paw rug", 80, level = 2),
        RoomItem("plant-pot", RoomSlot.PLANT, "Potted plant", 0),
        RoomItem("plant-cactus", RoomSlot.PLANT, "Cactus", 70, level = 2),
        RoomItem("toy-none", RoomSlot.TOY, "No toy", 0),
        RoomItem("toy-ball", RoomSlot.TOY, "Ball", 40),
        RoomItem("toy-rope", RoomSlot.TOY, "Rope toy", 60, level = 4),
    )

    fun item(id: String): RoomItem? = items.firstOrNull { it.id == id }
    fun starter(slot: RoomSlot): RoomItem = items.first { it.slot == slot }

    /** Care points at which each level starts: level 1 at 0, level 2 at 60, and so on. */
    val levels = listOf(0, 60, 150, 280, 420, 600, 820, 1100, 1450, 1900)
    val levelNames = listOf("New home", "Settling in", "Lived in", "Homely", "Cosy home", "Garden view", "Sunny den", "Dream room", "Palace", "Legend")

    fun level(points: Int): Int = levels.indexOfLast { points >= it } + 1

    /** Treats (16f): bought in packs of [TreatItem.pack], given one at a time. */
    val treats = listOf(
        TreatItem("treat-biscuit", "Bone biscuits", pack = 5, price = 30, card = "bag-biscuit"),
        TreatItem("treat-jerky", "Chicken jerky", pack = 5, price = 45, card = "bag-jerky"),
        TreatItem("treat-pupcup", "Pup cup", pack = 1, price = 25, level = 3),
        TreatItem("treat-cake", "Birthday cake", pack = 1, price = 0, premium = true),
    )

    fun treat(id: String): TreatItem? = treats.firstOrNull { it.id == id }

    /** The most treats the room hands out in a day. */
    const val TREATS_A_DAY = 3
}

/** A treat: [pack] of them for [price] Paws. [card] is its shop picture; the treat itself is [id]. */
class TreatItem(val id: String, val title: String, val pack: Int, val price: Int, val level: Int = 1, val premium: Boolean = false, card: String? = null) {
    val card: String = card ?: id
}

/** One pet's room: their Paws, the care points that set the level, and the pieces they own and have out. */
@Serializable
data class PetRoomState(
    val paws: Int = 50,
    /** Every Paw ever earned; spending never lowers it. */
    val points: Int = 0,
    val owned: Set<String> = emptySet(),
    /** What's in each spot, by [RoomSlot] name. Missing means the starter piece. */
    val placed: Map<String, String> = emptyMap(),
    /** The day the "day's care done" bonus was last given, as an ISO date. */
    val dayBonus: String = "",
    /** The level already celebrated, so each level up shows once. */
    val seenLevel: Int = 1,
    /** Paws given for each recent log, so Undo can take them back. */
    val recent: Map<String, Int> = emptyMap(),
    /** The care logs that were paid, by log id, so the day's allowance and spacing can be checked. */
    val paid: Map<String, PaidLog> = emptyMap(),
    /** Treats on hand, by treat id. */
    val treats: Map<String, Int> = emptyMap(),
    /** The day treats were last given, as an ISO date, and how many that day. */
    val treatDay: String = "",
    val treatsGiven: Int = 0,
) {
    val level: Int get() = RoomCatalog.level(points)

    fun piece(slot: RoomSlot): String = placed[slot.name]?.takeIf { RoomCatalog.item(it) != null } ?: RoomCatalog.starter(slot).id
    fun owns(item: RoomItem): Boolean = item.price == 0 && !item.premium || item.id in owned

    /** Treats still allowed today. */
    fun treatsLeft(today: LocalDate = LocalDate.now()): Int =
        RoomCatalog.TREATS_A_DAY - if (treatDay == today.toString()) treatsGiven else 0
}

/** A care log that earned Paws: its track ("meal", or "custom:<track id>") and when it was logged. */
@Serializable
data class PaidLog(val track: String, val at: Long)

/**
 * Paws, the room's money (16a). Looking after the pet earns them, never buying: a meal, water or
 * any other log +5 (up to a few of each a day, half an hour apart), a walk +1 per 100 m, the day's care done +20. Paws buy pieces for the pet's
 * room; levels come from every Paw ever earned. Kept on this phone, per pet.
 */
class PawsWallet(context: Context) {
    private val prefs = context.getSharedPreferences("paws", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val states = mutableStateMapOf<String, PetRoomState>()
    /** Read from disk once, without writing state while the screen is being drawn. */
    private val cache = HashMap<String, PetRoomState>()

    fun state(dogId: String): PetRoomState = states[dogId] ?: cache.getOrPut(dogId) { load(dogId) }

    private fun load(dogId: String): PetRoomState =
        prefs.getString(dogId, null)?.let { runCatching { json.decodeFromString(PetRoomState.serializer(), it) }.getOrNull() } ?: PetRoomState()

    private fun save(dogId: String, state: PetRoomState) {
        cache[dogId] = state
        states[dogId] = state
        prefs.edit().putString(dogId, json.encodeToString(PetRoomState.serializer(), state)).apply()
    }

    private fun update(dogId: String, change: (PetRoomState) -> PetRoomState) = save(dogId, change(state(dogId)))

    /**
     * Pays a care log, unless it's past the day's allowance, so tapping the same button over and over
     * earns nothing. A log pays +5 when it's dated today, comes at least [GAP_MINUTES] after the last
     * paid log of the same track, and the track hasn't already been paid its [allowance] today
     * (what's usual for the kind, or more when more reminders are set). Returns what was given.
     */
    fun earnForLog(dogId: String, eventId: String, kind: CareKind, trackId: String?, at: Instant, reminders: Int, now: Instant = Instant.now()): Int {
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        if (at.atZone(zone).toLocalDate() != today || at.isAfter(now.plusSeconds(60))) return 0
        val track = if (kind == CareKind.CUSTOM) "custom:${trackId.orEmpty()}" else kind.raw
        val s = state(dogId)
        val todays = s.paid.values.filter { it.track == track && Instant.ofEpochSecond(it.at).atZone(zone).toLocalDate() == today }
        if (todays.size >= allowance(kind, reminders)) return 0
        if (todays.any { Duration.between(Instant.ofEpochSecond(it.at), at).abs().toMinutes() < GAP_MINUTES }) return 0
        val cutoff = now.minus(Duration.ofDays(7)).epochSecond
        update(dogId) { it.copy(paid = it.paid.filterValues { p -> p.at >= cutoff } + (eventId to PaidLog(track, at.epochSecond))) }
        earn(dogId, PAWS_PER_LOG, eventId)
        return PAWS_PER_LOG
    }

    /** How many logs of [kind] pay in a day: the kind's usual count, or one per reminder when there are more. */
    fun allowance(kind: CareKind, reminders: Int): Int = maxOf(reminders, when (kind) {
        CareKind.MEAL, CareKind.WATER, CareKind.POOP -> 3
        CareKind.PEE -> 4
        CareKind.PLAY -> 2
        else -> 1
    })

    /** Gives [amount] Paws for a log, remembered under [eventId] so Undo can take them back. */
    fun earn(dogId: String, amount: Int, eventId: String? = null) {
        if (amount <= 0) return
        update(dogId) { s ->
            val recent = if (eventId == null) s.recent else (s.recent + (eventId to amount)).entries.toList().takeLast(100).associate { it.key to it.value }
            s.copy(paws = s.paws + amount, points = s.points + amount, recent = recent)
        }
    }

    /** Takes back what a log earned, when it's undone or deleted. */
    fun refund(dogId: String, eventId: String) {
        val amount = state(dogId).recent[eventId] ?: return
        update(dogId) { it.copy(paws = (it.paws - amount).coerceAtLeast(0), points = (it.points - amount).coerceAtLeast(0), recent = it.recent - eventId, paid = it.paid - eventId) }
    }

    /** +20 once a day, when every reminder for today is done. Returns what was given. */
    fun dayDone(dogId: String, today: LocalDate = LocalDate.now()): Int {
        if (state(dogId).dayBonus == today.toString()) return 0
        update(dogId) { it.copy(dayBonus = today.toString()) }
        earn(dogId, 20)
        return 20
    }

    /** Buys a piece and puts it in the room. False when there aren't enough Paws or it's locked. */
    fun buy(dogId: String, item: RoomItem): Boolean {
        val s = state(dogId)
        if (s.owns(item)) return place(dogId, item).let { true }
        if (item.premium || s.level < item.level || s.paws < item.price) return false
        save(dogId, s.copy(paws = s.paws - item.price, owned = s.owned + item.id, placed = s.placed + (item.slot.name to item.id)))
        return true
    }

    /** Puts a piece the pet owns (or a Premium one, with Premium) in its spot. */
    fun place(dogId: String, item: RoomItem) = update(dogId) { it.copy(placed = it.placed + (item.slot.name to item.id)) }

    /**
     * Buys a pack of treats. A Premium treat is free with Premium, one at a time. False when it's
     * locked or there aren't enough Paws.
     */
    fun buyTreat(dogId: String, treat: TreatItem, premium: Boolean): Boolean {
        val s = state(dogId)
        if (s.level < treat.level) return false
        if (treat.premium && !premium) return false
        val price = if (treat.premium) 0 else treat.price
        if (s.paws < price) return false
        save(dogId, s.copy(paws = s.paws - price, treats = s.treats + (treat.id to (s.treats[treat.id] ?: 0) + treat.pack)))
        return true
    }

    /**
     * Gives one treat. Treats are for fun: they never earn Paws or count as a meal, and the room
     * hands out [RoomCatalog.TREATS_A_DAY] a day at most. False when there's none, or none left today.
     */
    fun giveTreat(dogId: String, treat: TreatItem, today: LocalDate = LocalDate.now()): Boolean {
        val s = state(dogId)
        val have = s.treats[treat.id] ?: 0
        if (have <= 0 || s.treatsLeft(today) <= 0) return false
        val given = if (s.treatDay == today.toString()) s.treatsGiven else 0
        save(dogId, s.copy(treats = s.treats + (treat.id to have - 1), treatDay = today.toString(), treatsGiven = given + 1))
        return true
    }

    /** Forgets every pet's room and Paws, when all data is erased. */
    fun clear() {
        cache.clear()
        states.clear()
        prefs.edit().clear().apply()
    }

    companion object {
        const val PAWS_PER_LOG = 5
        /** Logs of the same track closer together than this pay once. */
        const val GAP_MINUTES = 30L
    }

    /** Marks the current level as celebrated. */
    fun celebrated(dogId: String) = update(dogId) { it.copy(seenLevel = it.level) }
}
