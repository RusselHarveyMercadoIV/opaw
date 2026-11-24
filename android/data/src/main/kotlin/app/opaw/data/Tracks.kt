package app.opaw.data

import app.opaw.core.CareKind

/**
 * One circle on the Care grid: a built-in kind or a custom track. Mirrors `CareTrack` in Tracks.swift.
 * [icon] is a design icon raw value (built-ins use their kind's icon).
 */
data class CareTrack(
    val key: String,
    val kind: CareKind,
    val trackId: String?,
    val title: String,
    val icon: String,
    val logStyle: TrackLogStyle,
) {
    val isCustom: Boolean get() = trackId != null
}

/** The built-in tracks in the design's order. */
val builtInTracks = listOf(
    CareKind.MEAL, CareKind.WATER, CareKind.PEE, CareKind.POOP, CareKind.MEDS,
    CareKind.BATH, CareKind.BRUSH, CareKind.NAILS, CareKind.WEIGHT, CareKind.VET,
)

/** The key for the walks option picked in setup, which isn't a circle on Care. */
const val WALKS_KEY = "walks"

/** Every track in the owner's order, shown or not. Tracks missing from the saved order go last. */
val DogData.allTracks: List<CareTrack>
    get() {
        val builtIns = builtInTracks.map { CareTrack(it.raw, it, null, it.shortTitle, it.raw, TrackLogStyle.COUNT) }
        val customs = customTracks.sortedBy { it.createdAt }.map { CareTrack(it.key, CareKind.CUSTOM, it.id, it.name, it.icon, it.logStyle) }
        val all = builtIns + customs
        val order = dog.trackOrderRaw
        if (order.isEmpty()) return all
        val position = HashMap<String, Int>()
        order.forEachIndexed { i, key -> position.putIfAbsent(key, i) }
        return all.withIndex().sortedBy { position[it.value.key] ?: (order.size + it.index) }.map { it.value }
    }

/** The tracks shown on the Care grid, in order. */
val DogData.shownTracks: List<CareTrack> get() = allTracks.filter { dog.tracks(it.key) }
