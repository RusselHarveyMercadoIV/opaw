package app.opaw.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.opaw.BuildConfig
import app.opaw.core.Badge
import app.opaw.core.LocationPrivacy
import app.opaw.data.Dog
import app.opaw.data.Memory
import app.opaw.data.Walk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

// Wire types, matching supabase/migrations and PackService.swift.

@Serializable
data class PackDog(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    val breed: String = "",
    val size: String = "",
    @SerialName("avatar_path") val avatarPath: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("distance_m") val distanceM: Double? = null,
)

@Serializable
data class PackPost(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("dog_id") val dogId: String,
    val kind: String,
    val caption: String = "",
    @SerialName("distance_m") val distanceM: Double? = null,
    @SerialName("duration_s") val durationS: Double? = null,
    val route: List<List<Double>>? = null,
    @SerialName("image_path") val imagePath: String? = null,
    val badge: String? = null,
    @SerialName("created_at") val createdAtRaw: String,
    @SerialName("dogs") val dog: DogSummary? = null,
    val likes: List<Like>? = null,
) {
    val createdAt: Instant get() = runCatching { java.time.OffsetDateTime.parse(createdAtRaw).toInstant() }.getOrDefault(Instant.EPOCH)

    @Serializable
    data class DogSummary(val name: String, val breed: String = "", @SerialName("avatar_path") val avatarPath: String? = null)

    @Serializable
    data class Like(@SerialName("user_id") val userId: String)
}

@Serializable
data class AvatarJob(val id: String, val status: String, val outputs: Map<String, String> = emptyMap(), val error: String? = null)

@Serializable
data class PetModelJob(
    val id: String,
    val stage: String,
    val progress: Int = 0,
    val outputs: Map<String, String> = emptyMap(),
    val error: String? = null,
    /** While reviewing: the reference sheet's path in the pet-models bucket. */
    val sheet: String? = null,
    @SerialName("draws_left") val drawsLeft: Int = 0,
)

@Serializable
private data class Session(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val user: User,
) {
    @Serializable
    data class User(
        val id: String,
        val email: String? = null,
        @SerialName("is_anonymous") val isAnonymous: Boolean = false,
    )
}

@Serializable
private data class FollowRow(@SerialName("follower_id") val followerId: String, @SerialName("dog_id") val dogId: String)

class PackException(message: String) : IOException(message)

/**
 * Talks to Supabase for the social "Pack" features and the avatar clip queue, over its REST APIs.
 * Mirrors PackService.swift. Set SUPABASE_HOST and SUPABASE_ANON_KEY at build time to turn it on.
 */
class PackService(private val context: Context) {
    private val host = BuildConfig.SUPABASE_HOST.trim()
    private val key = BuildConfig.SUPABASE_ANON_KEY.trim()
    private val base = "https://$host"
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val prefs = context.getSharedPreferences("pack", Context.MODE_PRIVATE)

    // Anonymous sessions from older builds don't count: everyone signs in with Google now.
    private var session: Session? = prefs.getString("session", null)
        ?.let { runCatching { json.decodeFromString(Session.serializer(), it) }.getOrNull() }
        ?.takeUnless { it.user.isAnonymous }

    var userId by mutableStateOf(session?.user?.id)
        private set
    var email by mutableStateOf(session?.user?.email)
        private set
    var hasAcceptedRules by mutableStateOf(prefs.getBoolean("acceptedRules", false))
        private set

    init {
        // Drop a leftover anonymous session and the Pack state that went with it.
        if (session == null && prefs.contains("session")) {
            prefs.edit().remove("session").putBoolean("acceptedRules", false).apply()
            hasAcceptedRules = false
        }
    }

    val isConfigured: Boolean get() = host.isNotEmpty() && key.isNotEmpty()
    val isSignedIn: Boolean get() = userId != null

    // MARK: HTTP

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        bytes: ByteArray? = null,
        contentType: String = "application/json",
        headers: Map<String, String> = emptyMap(),
        auth: Boolean = true,
        readTimeoutMs: Int = 30_000,
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!isConfigured) throw PackException("The Pack server isn't set up yet. See docs/SETUP.md.")
        val connection = URL(base + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("apikey", key)
        connection.setRequestProperty("Authorization", "Bearer ${if (auth) session?.accessToken ?: key else key}")
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        val payload = bytes ?: body?.toByteArray()
        if (payload != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            connection.outputStream.use { it.write(payload) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val result = stream?.use { it.readBytes() } ?: ByteArray(0)
        if (code !in 200..299) throw PackException(String(result).ifEmpty { "Request failed ($code)" })
        result
    }

    private fun saveSession(value: Session?) {
        session = value
        userId = value?.user?.id
        email = value?.user?.email
        prefs.edit().apply { if (value == null) remove("session") else putString("session", json.encodeToString(Session.serializer(), value)) }.apply()
    }

    /** Refreshes an expired session. There is no anonymous fallback: without a session, sign in with Google. */
    private suspend fun ensureFreshSession(): String {
        val current = session ?: throw PackException("Sign in with Google first.")
        val expires = current.expiresAt ?: 0
        if (expires != 0L && expires - 60 < Instant.now().epochSecond) {
            val body = buildJsonObject { put("refresh_token", current.refreshToken) }.toString()
            val refreshed = try {
                json.decodeFromString(Session.serializer(), String(request("POST", "/auth/v1/token?grant_type=refresh_token", body, auth = false)))
            } catch (error: PackException) {
                // A revoked or reused refresh token can't recover; send the user back to sign in.
                if (Regex("refresh.?token", RegexOption.IGNORE_CASE).containsMatchIn(error.message.orEmpty())) saveSession(null)
                throw error
            }
            saveSession(refreshed)
        }
        return current.user.id
    }

    private fun requireUser(): String = userId ?: throw PackException("Join the Pack first.")

    private suspend fun rest(method: String, table: String, body: JsonElement? = null, query: String = "", prefer: String? = null): String {
        ensureFreshSession()
        val headers = prefer?.let { mapOf("Prefer" to it) } ?: emptyMap()
        return String(request(method, "/rest/v1/$table$query", body?.toString(), headers = headers))
    }

    private suspend fun upload(bucket: String, path: String, data: ByteArray, contentType: String) {
        ensureFreshSession()
        request("POST", "/storage/v1/object/$bucket/$path", bytes = data, contentType = contentType, headers = mapOf("x-upsert" to "true"))
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    // MARK: Account

    /**
     * Exchanges a Google ID token for a Supabase session. [rawNonce] is the unhashed nonce whose
     * SHA-256 was given to Google, so Supabase can check the token was made for this request.
     */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String) {
        val body = buildJsonObject {
            put("provider", "google")
            put("id_token", idToken)
            put("nonce", rawNonce)
        }.toString()
        val created = json.decodeFromString(Session.serializer(), String(request("POST", "/auth/v1/token?grant_type=id_token", body, auth = false)))
        saveSession(created)
    }

    /** Ends the session on this phone. Pets stay on the phone; the app asks to sign in again. */
    suspend fun signOut() {
        if (session != null) runCatching { request("POST", "/auth/v1/logout") }
        saveSession(null)
        hasAcceptedRules = false
        prefs.edit().putBoolean("acceptedRules", false).apply()
    }

    /** Records the user's Pack name and that the community rules were accepted. */
    suspend fun join(displayName: String) {
        val id = ensureFreshSession()
        val profile = buildJsonObject {
            put("id", id)
            put("display_name", displayName)
            put("accepted_rules_at", Instant.now().toString())
        }
        rest("POST", "profiles", profile, prefer = "resolution=merge-duplicates")
        hasAcceptedRules = true
        prefs.edit().putBoolean("acceptedRules", true).apply()
    }

    /** Deletes the user's shared files, Pack data and account. Local data on the phone is kept. */
    suspend fun deleteAccount() {
        val user = requireUser()
        val folder = user.lowercase()
        for (bucket in listOf("post-images", "avatar-inputs")) {
            runCatching {
                val listed = String(request("POST", "/storage/v1/object/list/$bucket", buildJsonObject { put("prefix", folder) }.toString()))
                val names = json.parseToJsonElement(listed).let { it as? kotlinx.serialization.json.JsonArray }.orEmpty()
                    .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
                if (names.isNotEmpty()) {
                    val prefixes = kotlinx.serialization.json.JsonArray(names.map { kotlinx.serialization.json.JsonPrimitive("$folder/$it") })
                    request("DELETE", "/storage/v1/object/$bucket", buildJsonObject { put("prefixes", prefixes) }.toString())
                }
            }
        }
        rest("POST", "rpc/delete_my_account", buildJsonObject { })
        saveSession(null)
        hasAcceptedRules = false
        prefs.edit().putBoolean("acceptedRules", false).apply()
    }

    // MARK: Dogs

    /** Shares the dog with the Pack at a coarse location. */
    suspend fun shareDog(dog: Dog, latitude: Double?, longitude: Double?, discoverable: Boolean = true) {
        val owner = requireUser()
        var avatarPath: String? = null
        (dog.cutoutPath ?: dog.photoPath)?.let { local ->
            val file = File(context.filesDir, local)
            if (file.exists()) {
                val path = "${owner.lowercase()}/dog-${dog.id.lowercase()}.png"
                upload("post-images", path, file.readBytes(), "image/png")
                avatarPath = path
            }
        }
        val coarse = if (latitude != null && longitude != null) LocationPrivacy.coarsen(latitude, longitude) else null
        val row = buildJsonObject {
            put("id", dog.id)
            put("owner_id", owner)
            put("name", dog.name)
            put("breed", dog.breed)
            put("size", dog.sizeRaw)
            put("avatar_path", avatarPath)
            put("lat", coarse?.first)
            put("lon", coarse?.second)
            put("is_discoverable", discoverable)
        }
        rest("POST", "dogs", row, prefer = "resolution=merge-duplicates")
    }

    suspend fun nearbyDogs(latitude: Double, longitude: Double, radiusMeters: Double = 5000.0): List<PackDog> {
        val body = buildJsonObject { put("p_lat", latitude); put("p_lon", longitude); put("p_radius_m", radiusMeters) }
        return json.decodeFromString(rest("POST", "rpc/nearby_dogs", body))
    }

    fun publicUrl(path: String?): String? = if (path == null || !isConfigured) null else "$base/storage/v1/object/public/post-images/$path"

    // MARK: Feed

    suspend fun feed(limit: Int = 50): List<PackPost> =
        json.decodeFromString(rest("GET", "posts", query = "?select=${enc("*,dogs(name,breed,avatar_path),likes(user_id)")}&order=created_at.desc&limit=$limit"))

    private suspend fun post(dogId: String, kind: String, caption: String, distance: Double? = null, duration: Double? = null,
                             route: List<List<Double>>? = null, imagePath: String? = null, badge: String? = null) {
        val owner = requireUser()
        val row = buildJsonObject {
            put("owner_id", owner)
            put("dog_id", dogId)
            put("kind", kind)
            put("caption", caption)
            put("distance_m", distance)
            put("duration_s", duration)
            put("route", route?.let { r -> kotlinx.serialization.json.JsonArray(r.map { p -> kotlinx.serialization.json.JsonArray(p.map { kotlinx.serialization.json.JsonPrimitive(it) }) }) }
                ?: kotlinx.serialization.json.JsonNull)
            put("image_path", imagePath)
            put("badge", badge)
        }
        rest("POST", "posts", row)
    }

    suspend fun shareWalk(walk: Walk, caption: String) {
        // Trim the start and end so home isn't revealed, then thin the route.
        val trimmed = LocationPrivacy.trimRouteEnds(walk.route)
        val step = maxOf(1, trimmed.size / 200)
        val route = trimmed.indices.step(step).map { listOf(trimmed[it].latitude, trimmed[it].longitude) }
        post(walk.dogId, "walk", caption, walk.distanceMeters, walk.movingSeconds, route)
    }

    suspend fun shareMemory(memory: Memory) {
        val owner = requireUser()
        var imagePath: String? = null
        memory.photoPath?.let { local ->
            val bitmap = PhotoStore.load(context, local, 1600)
            if (bitmap != null) {
                val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
                val path = "${owner.lowercase()}/${memory.id.lowercase()}.jpg"
                upload("post-images", path, bytes, "image/jpeg")
                imagePath = path
            }
        }
        post(memory.dogId, "memory", memory.caption, imagePath = imagePath)
    }

    suspend fun shareBadge(badge: Badge, dog: Dog) = post(dog.id, "badge", "${dog.name} earned ${badge.title}!", badge = badge.raw)

    suspend fun setLiked(liked: Boolean, post: PackPost) {
        val user = requireUser()
        if (liked) {
            rest("POST", "likes", buildJsonObject { put("post_id", post.id); put("user_id", user) }, prefer = "resolution=merge-duplicates")
        } else {
            rest("DELETE", "likes", query = "?post_id=eq.${post.id}&user_id=eq.$user")
        }
    }

    suspend fun deletePost(post: PackPost) { rest("DELETE", "posts", query = "?id=eq.${post.id}") }

    // MARK: Friends

    suspend fun followedDogIds(): Set<String> {
        val user = requireUser()
        return json.decodeFromString<List<FollowRow>>(rest("GET", "follows", query = "?select=*&follower_id=eq.$user")).map { it.dogId }.toSet()
    }

    suspend fun setFollowing(following: Boolean, dogId: String) {
        val user = requireUser()
        if (following) {
            rest("POST", "follows", buildJsonObject { put("follower_id", user); put("dog_id", dogId) }, prefer = "resolution=merge-duplicates")
        } else {
            rest("DELETE", "follows", query = "?follower_id=eq.$user&dog_id=eq.$dogId")
        }
    }

    // MARK: Safety

    suspend fun report(postId: String? = null, dogId: String? = null, reason: String) {
        val user = requireUser()
        rest("POST", "reports", buildJsonObject { put("reporter_id", user); put("post_id", postId); put("dog_id", dogId); put("reason", reason) })
    }

    suspend fun block(blocked: String) {
        val user = requireUser()
        rest("POST", "blocks", buildJsonObject { put("blocker_id", user); put("blocked_id", blocked) }, prefer = "resolution=merge-duplicates")
    }

    // MARK: Avatar clips

    /** Uploads the dog's cutout to the avatar-inputs bucket and returns its storage path. */
    private suspend fun uploadAvatarInput(dog: Dog): Pair<String, String> {
        val owner = ensureFreshSession()
        val local = dog.cutoutPath ?: dog.photoPath ?: throw PackException("That photo couldn't be read.")
        val path = "${owner.lowercase()}/${dog.id.lowercase()}-${Instant.now().epochSecond}.png"
        upload("avatar-inputs", path, File(context.filesDir, local).readBytes(), "image/png")
        return owner to path
    }

    /** Uploads the dog's cutout and queues a job for the avatar worker. Returns the job id. */
    suspend fun requestAvatarClips(dog: Dog): String {
        val (owner, path) = uploadAvatarInput(dog)
        val row = buildJsonObject { put("owner_id", owner); put("dog_id", dog.id); put("breed", dog.breed); put("input_path", path) }
        val created = json.decodeFromString<List<AvatarJob>>(rest("POST", "avatar_jobs", row, query = "?select=id,status,outputs,error", prefer = "return=representation"))
        return created.first().id
    }

    suspend fun avatarJob(id: String): AvatarJob =
        json.decodeFromString<List<AvatarJob>>(rest("GET", "avatar_jobs", query = "?select=id,status,outputs,error&id=eq.$id")).first()

    /** Downloads finished clips. Returns the clip files when the job is done, null while it runs; throws on failure. */
    suspend fun downloadClips(jobId: String, into: File): Map<String, File>? {
        val job = avatarJob(jobId)
        return when (job.status) {
            "done" -> job.outputs.mapValues { (clip, path) ->
                File(into, "$clip.mp4").also { it.writeBytes(request("GET", "/storage/v1/object/authenticated/avatar-clips/$path")) }
            }
            "failed" -> throw PackException(job.error ?: "The avatar worker couldn't make clips.")
            else -> null
        }
    }

    // MARK: 3D pet models ("Bring to life", the pet-model Edge Function and Meshy)

    /**
     * Uploads the pet's photos (front, side, back) and starts a 3D job on the server.
     * [onUploaded] is told how many photos are up so far.
     */
    suspend fun startPetModel(dogId: String, photos: List<File>, heightMeters: Double, angles: List<String> = emptyList(), onUploaded: (Int) -> Unit = {}): PetModelJob {
        val owner = ensureFreshSession()
        val stamp = Instant.now().epochSecond
        val paths = photos.mapIndexed { index, file ->
            val path = "${owner.lowercase()}/${dogId.lowercase()}-$stamp-$index.png"
            upload("avatar-inputs", path, withContext(Dispatchers.IO) { file.readBytes() }, "image/png")
            onUploaded(index + 1)
            path
        }
        if (paths.isEmpty()) throw PackException("Those photos couldn't be read.")
        return petModelCall(buildJsonObject {
            put("action", "start")
            put("dog_id", dogId)
            put("input_paths", JsonArray(paths.map { JsonPrimitive(it) }))
            // Read by servers that haven't been updated to take several photos.
            put("input_path", paths.first())
            put("height_m", heightMeters)
            // Which view each photo is, so a skipped side shot doesn't turn the back into the side.
            if (angles.size == paths.size) put("angles", JsonArray(angles.map { JsonPrimitive(it) }))
            // This build shows the reference sheet for approval before the 3D model is paid for.
            put("review", true)
        })
    }

    /** Sends the approved sheet's front and side views and has the server build the 3D model from them. */
    suspend fun approvePetModel(id: String, dogId: String, front: android.graphics.Bitmap, left: android.graphics.Bitmap): PetModelJob {
        val owner = ensureFreshSession()
        val stamp = Instant.now().epochSecond
        val paths = mapOf("front" to front, "left" to left).mapValues { (view, image) ->
            val path = "${owner.lowercase()}/${dogId.lowercase()}-$stamp-clean-$view.png"
            val bytes = withContext(Dispatchers.Default) {
                java.io.ByteArrayOutputStream().also { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            }
            upload("avatar-inputs", path, bytes, "image/png")
            path
        }
        return petModelCall(buildJsonObject {
            put("action", "approve")
            put("id", id)
            put("views", buildJsonObject { paths.forEach { (view, path) -> put(view, path) } })
        })
    }

    /** Draws the reference sheet again from the same photos. */
    suspend fun redrawPetModel(id: String): PetModelJob = petModelCall(buildJsonObject { put("action", "redraw"); put("id", id) })

    /** Picks a failed job back up when its 3D model was already built; throws if it has to start over. */
    suspend fun petModelRetry(id: String): PetModelJob = petModelCall(buildJsonObject { put("action", "retry"); put("id", id) })

    /** Advances the job on the server and returns where it is. */
    suspend fun petModelStatus(id: String): PetModelJob = petModelCall(buildJsonObject { put("action", "status"); put("id", id) })

    suspend fun downloadPetModel(path: String): ByteArray = request("GET", "/storage/v1/object/authenticated/pet-models/$path", readTimeoutMs = 120_000)

    private suspend fun petModelCall(body: JsonElement): PetModelJob {
        ensureFreshSession()
        val text = try {
            // Copying the finished models can take a while, so give the function longer than other calls.
            String(request("POST", "/functions/v1/pet-model", body.toString(), readTimeoutMs = 120_000))
        } catch (e: PackException) {
            // The function answers errors as {"error": "..."}; show just the sentence.
            val message = runCatching { json.parseToJsonElement(e.message ?: "").jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
            throw PackException(message ?: e.message ?: "Bring to life failed.")
        }
        return json.decodeFromString(PetModelJob.serializer(), text)
    }
}

/** Loads Pack avatars and photos from their public URLs, with a small cache. */
object RemoteImages {
    private val cache = LruCache<String, Bitmap>(40)

    suspend fun load(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull()?.also { cache.put(url, it) }
        }
    }
}
