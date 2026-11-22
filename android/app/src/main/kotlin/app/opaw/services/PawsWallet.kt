package app.opaw.services

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import app.opaw.core.CareKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** The spots in a pet's room that can hold a piece (16b). */
enum class RoomSlot(val title: String) { BED("Bed"), BOWL("Bowl"), WALL("Wall art"), RUG("Rug"), PLANT("Plant"), TOY("Toy") }

/** The shop's tabs, each covering one or more spots. */
enum class ShopTab(val title: String, val slots: List<RoomSlot>) {
    BEDS("Beds", listOf(RoomSlot.BED)),
    BOWLS("Bowls", listOf(RoomSlot.BOWL)),
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
) {
    val level: Int get() = RoomCatalog.level(points)

    fun piece(slot: RoomSlot): String = placed[slot.name]?.takeIf { RoomCatalog.item(it) != null } ?: RoomCatalog.starter(slot).id
    fun owns(item: RoomItem): Boolean = item.price == 0 && !item.premium || item.id in owned
}

/**
 * Paws, the room's money (16a). Looking after the pet earns them, never buying: a meal, water or
 * any other log +5, a walk +1 per 100 m, the day's care done +20. Paws buy pieces for the pet's
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

    /** Paws for a log of [kind]. */
    fun pawsFor(kind: CareKind): Int = 5

    /** Gives [amount] Paws for a log, remembered under [eventId] so Undo can take them back. */
    fun earn(dogId: String, amount: Int, eventId: String? = null) {
        if (amount <= 0) return
        update(dogId) { s ->
            val recent = if (eventId == null) s.recent else (s.recent + (eventId to amount)).entries.toList().takeLast(20).associate { it.key to it.value }
            s.copy(paws = s.paws + amount, points = s.points + amount, recent = recent)
        }
    }

    /** Takes back what a log earned, when it's undone or deleted. */
    fun refund(dogId: String, eventId: String) {
        val amount = state(dogId).recent[eventId] ?: return
        update(dogId) { it.copy(paws = (it.paws - amount).coerceAtLeast(0), points = (it.points - amount).coerceAtLeast(0), recent = it.recent - eventId) }
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

    /** Forgets every pet's room and Paws, when all data is erased. */
    fun clear() {
        cache.clear()
        states.clear()
        prefs.edit().clear().apply()
    }

    /** Marks the current level as celebrated. */
    fun celebrated(dogId: String) = update(dogId) { it.copy(seenLevel = it.level) }
}
