package app.opaw.services

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.opaw.OpawApplication
import app.opaw.data.CareEvent
import app.opaw.data.Converters
import app.opaw.data.Dog
import app.opaw.data.Walk
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.net.URLEncoder
import java.security.SecureRandom
import java.time.Instant

/**
 * Households: a pet shared between its owner, family and walkers, so everyone logs to the same pet
 * and meals never get doubled. The phone keeps its own copy of each shared pet, and this keeps that
 * copy and the server's (supabase/migrations/20261117000000_shared_pets.sql) in step both ways.
 *
 * Each sync compares what's on the phone with what it last sent or received (a small ledger per
 * pet), so changes made anywhere (a log, an edit, setup, the widget) go up without each screen
 * having to say so, and what others logged comes down. Writes coming down go straight to the
 * database, so they aren't sent back up. Where both sides changed, the server's copy wins.
 */
class HouseholdSync(private val app: OpawApplication) {
    private val pack get() = app.pack
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val converters = Converters()
    private val prefs = app.getSharedPreferences("household", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var soon: Job? = null

    /** Pets invited for during setup, before they're saved here: not to be brought down as new. */
    private val settingUp = mutableSetOf<String>()

    /** True once this sign-in has looked for pets saved to the account (after a reinstall, say). */
    var hasChecked by mutableStateOf(false)
        private set

    /** Each shared pet's id and this user's role for it: "owner", "family" or "walker". */
    var roles by mutableStateOf(loadRoles())
        private set

    /** An invite code from a link or typed in, joined as soon as there's a signed-in user. */
    var pendingCode by mutableStateOf(prefs.getString("pendingCode", null))
        private set

    var isJoining by mutableStateOf(false)
        private set

    /** What happened with the last invite, to show once: "You're now helping look after Hela." */
    var joinResult by mutableStateOf<JoinResult?>(null)

    data class JoinResult(val title: String, val message: String)

    @Serializable
    data class Member(
        @kotlinx.serialization.SerialName("user_id") val userId: String,
        val role: String,
        @kotlinx.serialization.SerialName("display_name") val displayName: String = "",
    )

    @Serializable
    data class Invite(
        val code: String,
        val role: String,
        @kotlinx.serialization.SerialName("invitee_name") val inviteeName: String = "",
    )

    fun role(petId: String): String? = roles[petId]

    /** Can this user log [kind] for the pet? Walkers log walks, pee and poop only. */
    fun canLog(petId: String, kind: String): Boolean = roles[petId] != "walker" || kind in WALKER_KINDS

    // MARK: Inviting

    /**
     * Makes an invite for [pet] and returns its link. Shares the pet first if it isn't yet; [pet]
     * may not be saved on the phone yet (during setup), and goes up in full once it is.
     */
    suspend fun invite(pet: Dog, name: String, role: String): Pair<String, String> {
        if (app.database.dogs().get(pet.id) == null) settingUp += pet.id
        if (roles[pet.id] == null) {
            pack.householdRest("POST", "shared_pets", buildJsonObject {
                put("id", pet.id)
                put("profile", profileOf(pet))
            }, prefer = "resolution=merge-duplicates")
            setRole(pet.id, "owner")
            syncSoon()
        }
        val code = newCode()
        pack.householdRest("POST", "pet_invites", buildJsonObject {
            put("code", code)
            put("pet_id", pet.id)
            put("role", role)
            put("invitee_name", name.take(40))
        })
        return code to pack.inviteLink(code)
    }

    suspend fun members(petId: String): List<Member> =
        json.decodeFromString(pack.householdRest("GET", "pet_members", query = "?pet_id=eq.$petId&select=user_id,role,display_name&order=joined_at"))

    /** Invites not yet opened. Only the owner sees them. */
    suspend fun invites(petId: String): List<Invite> =
        json.decodeFromString(pack.householdRest("GET", "pet_invites", query = "?pet_id=eq.$petId&used_by=is.null&select=code,role,invitee_name&order=created_at"))

    suspend fun removeMember(petId: String, userId: String) {
        pack.householdRest("DELETE", "pet_members", query = "?pet_id=eq.$petId&user_id=eq.$userId")
    }

    suspend fun cancelInvite(code: String) {
        pack.householdRest("DELETE", "pet_invites", query = "?code=eq.$code")
    }

    /** Stops helping with someone else's pet. Its copy stays on this phone, no longer shared. */
    suspend fun leave(petId: String) {
        val me = pack.userId ?: return
        pack.householdRest("DELETE", "pet_members", query = "?pet_id=eq.$petId&user_id=eq.$me")
        forget(petId)
    }

    // MARK: Joining

    /** Keeps [code] until it can be joined; the root screen joins it once signed in. */
    fun offer(code: String) {
        val clean = code.uppercase().filter { it.isLetterOrDigit() }.take(32)
        if (clean.length < 6) return
        pendingCode = clean
        prefs.edit().putString("pendingCode", clean).apply()
    }

    /** Joins the pending invite: brings the pet and its logs onto this phone and selects it. */
    suspend fun joinPending() {
        val code = pendingCode ?: return
        if (isJoining || !pack.isSignedIn) return
        isJoining = true
        try {
            val name = app.settings.caretakerName.first().ifBlank { pack.email?.substringBefore('@').orEmpty() }
            val result = json.parseToJsonElement(pack.householdRpc("accept_pet_invite", buildJsonObject {
                put("invite_code", code)
                put("name", name.take(40))
            })).jsonObject
            val petId = result.str("pet_id") ?: error("The invite didn't say which pet.")
            val role = result.str("role") ?: "family"
            setRole(petId, role)
            mutex.withLock { sync(petId) }
            val dog = app.database.dogs().get(petId)
            if (dog != null) app.settings.setActiveDog(petId)
            val pet = dog?.name ?: "their pet"
            joinResult = JoinResult(
                "You're in!",
                if (role == "walker") "You're now walking $pet. Log walks, pee and poop, and the family sees them right away."
                else "You're now helping look after $pet. Everyone logs to the same pet, so meals never get doubled.",
            )
        } catch (error: Throwable) {
            joinResult = JoinResult("Couldn't join", readable(error))
        } finally {
            pendingCode = null
            prefs.edit().remove("pendingCode").apply()
            isJoining = false
        }
    }

    // MARK: Syncing

    /** Syncs a moment from now, once, however many changes come in meanwhile. */
    fun syncSoon() {
        if (!pack.isSignedIn) return
        soon?.cancel()
        soon = app.scope.launch {
            delay(1_500)
            syncAll()
        }
    }

    /** Finds every pet shared with this user, then syncs each. Quiet on failure: it tries again later. */
    suspend fun syncAll() {
        if (!pack.isSignedIn || !pack.isConfigured) return
        mutex.withLock {
            val found = runCatching { discover() }.isSuccess
            runCatching { backUp() }
            for (petId in roles.keys.toList()) runCatching { sync(petId) }
            if (found) hasChecked = true
        }
    }

    /**
     * Before setup on a fresh install: brings back the pets saved to this account, giving up after
     * [timeoutMs] so a bad connection doesn't keep the app from opening.
     */
    suspend fun restore(timeoutMs: Long = 20_000) {
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { syncAll() }
        hasChecked = true
    }

    /**
     * Saves every pet owned on this phone to the account (as a household of one until someone is
     * invited), so its logs and walks come back after a reinstall or on a new phone.
     */
    private suspend fun backUp() {
        val skip = prefs.getStringSet("notMine", emptySet()).orEmpty()
        for (dog in app.database.dogs().all()) {
            if (roles[dog.id] != null || dog.id in skip) continue
            try {
                pack.householdRest("POST", "shared_pets", buildJsonObject {
                    put("id", dog.id)
                    put("profile", profileOf(dog))
                }, prefer = "resolution=merge-duplicates")
                setRole(dog.id, "owner")
            } catch (error: PackException) {
                // Someone else's pet this phone helped with before: it isn't this user's to save.
                prefs.edit().putStringSet("notMine", skip + dog.id).apply()
            }
        }
    }

    /** Pets this user owns or helps with, including ones shared from another phone. */
    private suspend fun discover() {
        val me = pack.userId ?: return
        val owned = json.parseToJsonElement(pack.householdRest("GET", "shared_pets", query = "?owner_id=eq.$me&select=id")).jsonArray
            .mapNotNull { it.jsonObject.str("id") }
        val memberships = json.parseToJsonElement(pack.householdRest("GET", "pet_members", query = "?user_id=eq.$me&select=pet_id,role")).jsonArray
            .mapNotNull { row -> row.jsonObject.let { o -> o.str("pet_id")?.let { it to (o.str("role") ?: "family") } } }
        val found = owned.associateWith { "owner" } + memberships
        // Dropped from a household (or it stopped being shared): keep the pet here, unshared.
        for (petId in roles.keys - found.keys) forget(petId)
        for ((petId, role) in found) if (roles[petId] != role) setRole(petId, role)
    }

    private suspend fun sync(petId: String) {
        val role = roles[petId] ?: return
        val me = pack.userId ?: return
        val ledger = loadLedger(petId)
        val db = app.database

        // The pet itself.
        val row = json.parseToJsonElement(pack.householdRest("GET", "shared_pets", query = "?id=eq.$petId&select=profile,photo_path,cutout_path,updated_at"))
            .jsonArray.firstOrNull()?.jsonObject
        if (row == null) {
            if (role != "owner") forget(petId)
            return
        }
        var dog = db.dogs().get(petId)
        if (dog == null && petId in settingUp) return // Still being set up on this phone.
        val remoteProfile = row["profile"]?.let { it as? JsonObject } ?: JsonObject(emptyMap())
        val remoteHash = remoteProfile.toString().hashCode()
        if (dog == null) {
            dog = applyProfile(Dog(id = petId), remoteProfile).let {
                // A walker's Care shows only what they can log.
                if (role == "walker") it.copy(trackedKindsRaw = WALKER_KINDS.toList()) else it
            }
            db.dogs().upsert(dog)
            ledger.profile = remoteHash
            ledger.profileSeen = remoteHash
            // The owner's own pet coming back: its 3D self comes back too.
            if (role == "owner") app.petModels.restore(petId)
        } else if (remoteHash != ledger.profileSeen) {
            // Someone else changed the pet; theirs wins over any edit here not yet sent.
            dog = applyProfile(dog, remoteProfile)
            db.dogs().upsert(dog)
            ledger.profileSeen = remoteHash
            ledger.profile = profileOf(dog).toString().hashCode()
        }
        dog = pullPhotos(dog, row, ledger)

        // Up: the pet's profile and pictures, from its owner and family.
        if (role == "owner" || role == "family") {
            val profile = profileOf(dog)
            val hash = profile.toString().hashCode()
            if (hash != ledger.profile) {
                pack.householdRest("PATCH", "shared_pets", buildJsonObject { put("profile", profile) }, query = "?id=eq.$petId")
                ledger.profile = hash
                ledger.profileSeen = hash
            }
            pushPhoto(dog, dog.photoPath, "photo", "jpg", "image/jpeg", ledger)
            pushPhoto(dog, dog.cutoutPath, "cutout", "png", "image/png", ledger)
        }

        // Down: what others logged since last time.
        var cursor = ledger.eventsCursor
        while (true) {
            val rows = json.parseToJsonElement(pack.householdRest("GET", "pet_events",
                query = "?pet_id=eq.$petId&select=id,data,deleted,author_id,updated_at&order=updated_at.asc&limit=500" +
                    (cursor?.let { "&updated_at=gt.${enc(it)}" } ?: ""))).jsonArray
            for (item in rows) {
                val o = item.jsonObject
                val id = o.str("id") ?: continue
                if (o["deleted"]?.jsonPrimitive?.booleanOrNull == true) {
                    db.careEvents().get(id)?.let { db.careEvents().delete(it) }
                    ledger.events.remove(id)
                } else {
                    val event = eventFrom(id, petId, o["data"]?.jsonObject ?: continue, mine = o.str("author_id") == me)
                    db.careEvents().upsert(event)
                    ledger.events[id] = event.hashCode()
                }
                cursor = o.str("updated_at") ?: cursor
            }
            if (rows.size < 500) break
        }
        ledger.eventsCursor = cursor

        cursor = ledger.walksCursor
        while (true) {
            val rows = json.parseToJsonElement(pack.householdRest("GET", "pet_walks",
                query = "?pet_id=eq.$petId&select=id,data,deleted,updated_at&order=updated_at.asc&limit=200" +
                    (cursor?.let { "&updated_at=gt.${enc(it)}" } ?: ""))).jsonArray
            for (item in rows) {
                val o = item.jsonObject
                val id = o.str("id") ?: continue
                if (o["deleted"]?.jsonPrimitive?.booleanOrNull == true) {
                    db.walks().get(id)?.let { db.walks().delete(it) }
                    ledger.walks.remove(id)
                } else {
                    val walk = walkFrom(id, petId, o["data"]?.jsonObject ?: continue) ?: continue
                    db.walks().upsert(walk)
                    ledger.walks[id] = walk.hashCode()
                }
                cursor = o.str("updated_at") ?: cursor
            }
            if (rows.size < 200) break
        }
        ledger.walksCursor = cursor

        // Up: what was logged or changed here, and what was deleted here.
        val myName = app.settings.caretakerName.first().ifBlank { pack.email?.substringBefore('@').orEmpty() }
        val events = db.careEvents().allForDog(petId)
        val changed = events.filter { ledger.events[it.id] != it.hashCode() }
        val allowed = changed.filter { role != "walker" || it.kindRaw in WALKER_KINDS }
        for (batch in allowed.chunked(200)) {
            pack.householdRest("POST", "pet_events", JsonArray(batch.map { event ->
                buildJsonObject {
                    put("id", event.id)
                    put("pet_id", petId)
                    put("kind", event.kindRaw)
                    put("data", eventData(event, myName))
                    put("deleted", false)
                }
            }), prefer = "resolution=merge-duplicates")
            for (event in batch) ledger.events[event.id] = event.hashCode()
        }
        // A walker's other logs stay on their phone; don't try them again.
        for (event in changed - allowed.toSet()) ledger.events[event.id] = event.hashCode()
        val gone = ledger.events.keys - events.map { it.id }.toSet()
        for (batch in gone.chunked(100)) {
            pack.householdRest("PATCH", "pet_events", buildJsonObject { put("deleted", true) }, query = "?id=in.(${batch.joinToString(",")})")
            batch.forEach { ledger.events.remove(it) }
        }

        val walks = db.walks().allForDog(petId)
        for (batch in walks.filter { ledger.walks[it.id] != it.hashCode() }.chunked(20)) {
            pack.householdRest("POST", "pet_walks", JsonArray(batch.map { walk ->
                buildJsonObject {
                    put("id", walk.id)
                    put("pet_id", petId)
                    put("data", walkData(walk))
                    put("deleted", false)
                }
            }), prefer = "resolution=merge-duplicates")
            for (walk in batch) ledger.walks[walk.id] = walk.hashCode()
        }
        val goneWalks = ledger.walks.keys - walks.map { it.id }.toSet()
        for (batch in goneWalks.chunked(100)) {
            pack.householdRest("PATCH", "pet_walks", buildJsonObject { put("deleted", true) }, query = "?id=in.(${batch.joinToString(",")})")
            batch.forEach { ledger.walks.remove(it) }
        }

        saveLedger(petId, ledger)
    }

    private suspend fun pullPhotos(dog: Dog, row: JsonObject, ledger: Ledger): Dog {
        var current = dog
        for ((field, kind) in listOf("photo_path" to "photo", "cutout_path" to "cutout")) {
            val remote = row.str(field) ?: continue
            if (remote == ledger.photos[kind]) continue
            val bytes = runCatching { pack.householdDownload(remote) }.getOrNull() ?: continue
            if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null) continue
            val name = "${dog.id}-$kind.${remote.substringAfterLast('.', "jpg")}"
            File(app.filesDir, name).writeBytes(bytes)
            current = if (kind == "photo") current.copy(photoPath = name) else current.copy(cutoutPath = name)
            ledger.photos[kind] = remote
            ledger.photos["$kind-local"] = "$name:${bytes.size}"
        }
        if (current != dog) app.database.dogs().upsert(current)
        return current
    }

    private suspend fun pushPhoto(dog: Dog, local: String?, kind: String, extension: String, type: String, ledger: Ledger) {
        val file = local?.let { File(app.filesDir, it) }?.takeIf { it.exists() } ?: return
        val stamp = "$local:${file.length()}"
        if (ledger.photos["$kind-local"] == stamp) return
        val path = "${dog.id.lowercase()}/$kind-${Instant.now().epochSecond}.$extension"
        pack.householdUpload(path, file.readBytes(), type)
        pack.householdRest("PATCH", "shared_pets", buildJsonObject { put("${kind}_path", path) }, query = "?id=eq.${dog.id}")
        ledger.photos[kind] = path
        ledger.photos["$kind-local"] = stamp
    }

    // MARK: Wire formats

    private fun profileOf(dog: Dog): JsonObject = buildJsonObject {
        put("name", dog.name)
        put("breed", dog.breed)
        dog.birthDate?.let { put("birthDate", it.toEpochMilli()) }
        dog.weightKg?.let { put("weightKg", it) }
        put("size", dog.sizeRaw)
        put("energy", dog.energyRaw)
        put("mealMinutes", JsonArray(dog.mealTimes.map { JsonPrimitive(it) }))
        put("dailyGoalMinutes", dog.dailyGoalMinutes)
        put("species", dog.species)
        put("sex", dog.sex)
        put("isNeutered", dog.isNeutered)
        dog.gotchaDate?.let { put("gotchaDate", it.toEpochMilli()) }
        put("microchip", dog.microchip)
        put("vetClinic", dog.vetClinic)
        put("temperament", dog.temperamentRaw)
    }

    private fun applyProfile(dog: Dog, p: JsonObject): Dog {
        val meals = p["mealMinutes"]?.let { runCatching { it.jsonArray.mapNotNull { m -> m.jsonPrimitive.intOrNull } }.getOrNull() }
        val updated = dog.copy(
            name = p.str("name") ?: dog.name,
            breed = p.str("breed") ?: dog.breed,
            birthDate = p.long("birthDate")?.let(Instant::ofEpochMilli) ?: dog.birthDate,
            weightKg = p["weightKg"]?.jsonPrimitive?.doubleOrNull ?: dog.weightKg,
            sizeRaw = p.str("size") ?: dog.sizeRaw,
            energyRaw = p.str("energy") ?: dog.energyRaw,
            dailyGoalMinutes = p["dailyGoalMinutes"]?.jsonPrimitive?.intOrNull ?: dog.dailyGoalMinutes,
            species = p.str("species") ?: dog.species,
            sex = p.str("sex") ?: dog.sex,
            isNeutered = p["isNeutered"]?.jsonPrimitive?.booleanOrNull ?: dog.isNeutered,
            gotchaDate = p.long("gotchaDate")?.let(Instant::ofEpochMilli) ?: dog.gotchaDate,
            microchip = p.str("microchip") ?: dog.microchip,
            vetClinic = p.str("vetClinic") ?: dog.vetClinic,
            temperamentRaw = p.str("temperament") ?: dog.temperamentRaw,
        )
        return if (meals.isNullOrEmpty()) updated else updated.withMealTimes(meals)
    }

    private fun eventData(event: CareEvent, myName: String): JsonObject = buildJsonObject {
        put("kind", event.kindRaw)
        put("date", event.date.toEpochMilli())
        put("note", event.note)
        event.poopConsistencyRaw?.let { put("poopConsistency", it) }
        event.poopColorRaw?.let { put("poopColor", it) }
        event.weightKg?.let { put("weightKg", it) }
        // An empty name means "You" on the phone that logged it; others need the real name.
        put("loggedBy", event.loggedBy.ifBlank { myName })
        event.trackId?.let { put("trackId", it) }
        put("trackName", event.trackName)
        event.amount?.let { put("amount", it) }
    }

    private fun eventFrom(id: String, petId: String, d: JsonObject, mine: Boolean) = CareEvent(
        id = id,
        dogId = petId,
        kindRaw = d.str("kind") ?: "meal",
        date = d.long("date")?.let(Instant::ofEpochMilli) ?: Instant.now(),
        note = d.str("note").orEmpty(),
        poopConsistencyRaw = d.str("poopConsistency"),
        poopColorRaw = d.str("poopColor"),
        weightKg = d["weightKg"]?.jsonPrimitive?.doubleOrNull,
        // This user's own logs read as "You" again.
        loggedBy = if (mine) "" else d.str("loggedBy").orEmpty(),
        trackId = d.str("trackId"),
        trackName = d.str("trackName").orEmpty(),
        amount = d["amount"]?.jsonPrimitive?.doubleOrNull,
    )

    private fun walkData(walk: Walk): JsonObject = buildJsonObject {
        put("startedAt", walk.startedAt.toEpochMilli())
        put("endedAt", walk.endedAt.toEpochMilli())
        put("distanceMeters", walk.distanceMeters)
        put("movingSeconds", walk.movingSeconds)
        put("route", converters.routeToString(walk.route))
        put("pins", converters.pinsToString(walk.pins))
        put("note", walk.note)
        put("activity", walk.activityRaw)
    }

    private fun walkFrom(id: String, petId: String, d: JsonObject): Walk? = runCatching {
        Walk(
            id = id,
            dogId = petId,
            startedAt = Instant.ofEpochMilli(d.long("startedAt")!!),
            endedAt = Instant.ofEpochMilli(d.long("endedAt")!!),
            distanceMeters = d["distanceMeters"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            movingSeconds = d["movingSeconds"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            route = d.str("route")?.let(converters::stringToRoute).orEmpty(),
            pins = d.str("pins")?.let(converters::stringToPins).orEmpty(),
            note = d.str("note").orEmpty(),
            activityRaw = d.str("activity") ?: "walk",
        )
    }.getOrNull()

    // MARK: Stored state

    /** What was last sent or received for one pet, to tell what changed since. */
    @Serializable
    private class Ledger(
        var profile: Int = 0,
        var profileSeen: Int = 0,
        val photos: MutableMap<String, String> = mutableMapOf(),
        val events: MutableMap<String, Int> = mutableMapOf(),
        val walks: MutableMap<String, Int> = mutableMapOf(),
        var eventsCursor: String? = null,
        var walksCursor: String? = null,
    )

    private fun ledgerFile(petId: String) = File(File(app.filesDir, "household").apply { mkdirs() }, "$petId.json")

    private fun loadLedger(petId: String): Ledger =
        runCatching { json.decodeFromString(Ledger.serializer(), ledgerFile(petId).readText()) }.getOrDefault(Ledger())

    private fun saveLedger(petId: String, ledger: Ledger) {
        runCatching { ledgerFile(petId).writeText(json.encodeToString(Ledger.serializer(), ledger)) }
    }

    private fun loadRoles(): Map<String, String> = runCatching {
        json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), prefs.getString("roles", null) ?: "{}")
    }.getOrDefault(emptyMap())

    private fun setRole(petId: String, role: String) {
        roles = roles + (petId to role)
        prefs.edit().putString("roles", json.encodeToString(MapSerializer(String.serializer(), String.serializer()), roles)).apply()
    }

    private fun forget(petId: String) {
        val skip = prefs.getStringSet("notMine", emptySet()).orEmpty()
        prefs.edit().putStringSet("notMine", skip + petId).apply()
        roles = roles - petId
        prefs.edit().putString("roles", json.encodeToString(MapSerializer(String.serializer(), String.serializer()), roles)).apply()
        ledgerFile(petId).delete()
    }

    /** Signing out: this phone stops syncing until someone signs in again. */
    fun reset() {
        soon?.cancel()
        roles = emptyMap()
        prefs.edit().clear().apply()
        File(app.filesDir, "household").deleteRecursively()
        pendingCode = null
        hasChecked = false
    }

    private fun newCode(): String {
        val random = SecureRandom()
        return (1..8).map { CODE_LETTERS[random.nextInt(CODE_LETTERS.length)] }.joinToString("")
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    companion object {
        /** What a walker can log, besides walks. */
        val WALKER_KINDS = setOf("pee", "poop")

        // No 0/O or 1/I, so a code read aloud or typed can't go wrong.
        private const val CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** The message from a server error, without the JSON around it. */
        fun readable(error: Throwable): String {
            val text = error.message.orEmpty()
            return runCatching { Json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
                ?: text.ifBlank { "Something went wrong. Try again." }
        }
    }
}
