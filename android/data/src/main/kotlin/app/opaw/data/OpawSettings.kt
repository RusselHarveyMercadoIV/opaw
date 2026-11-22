package app.opaw.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.opaw.core.OpawFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/** Someone else who looks after the pets, invited with a share link. */
@Serializable
data class HouseholdMember(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: Role,
) {
    @Serializable
    enum class Role(val title: String, val detail: String) {
        @kotlinx.serialization.SerialName("family") FAMILY("Family", "can log everything"),
        @kotlinx.serialization.SerialName("walker") WALKER("Walker", "walks and poop only"),
    }
}

private val Context.opawPrefs: DataStore<Preferences> by preferencesDataStore(name = "opaw")

/** Settings shared between the app, the widget and the walk service. Mirrors `OpawDefaults` on iOS. */
class OpawSettings(context: Context) {
    private val store = context.applicationContext.opawPrefs

    private object Keys {
        val activeDog = stringPreferencesKey("activeDogID")
        /** The name shown when this phone logs care, such as "Sam". */
        val caretaker = stringPreferencesKey("caretakerName")
        val imperial = booleanPreferencesKey("usesImperialUnits")
        val soundsOn = booleanPreferencesKey("soundsOn")
        val hapticsOn = booleanPreferencesKey("hapticsOn")
        val household = stringPreferencesKey("householdMembers")
        val premiumOfferShown = booleanPreferencesKey("premiumOfferShown")
        val onboardingSkipped = booleanPreferencesKey("onboardingSkipped")
        val addingPet = booleanPreferencesKey("addingPet")
        val welcomeOfferEnds = longPreferencesKey("welcomeOfferEnds")
        val claimAfterSignIn = booleanPreferencesKey("claimAfterSignIn")
        val careFocus = stringSetPreferencesKey("careFocus")
        val careHelpers = stringPreferencesKey("careHelpers")
        val scheduledReminders = stringSetPreferencesKey("scheduledReminders")
        fun newestBadge(dogId: String) = stringPreferencesKey("newestBadge.$dogId")
    }

    suspend fun scheduledReminderIds(): Set<String> = store.data.first()[Keys.scheduledReminders].orEmpty()
    suspend fun setScheduledReminderIds(ids: List<String>) = store.edit { it[Keys.scheduledReminders] = ids.toSet() }

    /** The badge earned most recently by a dog. */
    fun newestBadge(dogId: String): Flow<String?> = store.data.map { it[Keys.newestBadge(dogId)] }
    suspend fun setNewestBadge(dogId: String, badge: String) = store.edit { it[Keys.newestBadge(dogId)] = badge }

    /** People invited to help look after the pets. */
    val household: Flow<List<HouseholdMember>> = store.data.map { prefs ->
        prefs[Keys.household]?.let { runCatching { json.decodeFromString(ListSerializer(HouseholdMember.serializer()), it) }.getOrNull() }.orEmpty()
    }.distinctUntilChanged()

    suspend fun setHousehold(members: List<HouseholdMember>) = store.edit {
        it[Keys.household] = json.encodeToString(ListSerializer(HouseholdMember.serializer()), members)
    }

    val premiumOfferShown: Flow<Boolean> = store.data.map { it[Keys.premiumOfferShown] ?: false }
    suspend fun setPremiumOfferShown(shown: Boolean) = store.edit { it[Keys.premiumOfferShown] = shown }

    /** The owner skipped adding a first pet during setup; the app opens on Home's "Add a pet" instead. */
    val onboardingSkipped: Flow<Boolean> = store.data.map { it[Keys.onboardingSkipped] ?: false }.distinctUntilChanged()
    suspend fun setOnboardingSkipped(skipped: Boolean) = store.edit { it[Keys.onboardingSkipped] = skipped }

    /** "Add your first pet" was tapped on Home's empty room: the photos and pet setup are showing (10c). */
    val addingPet: Flow<Boolean> = store.data.map { it[Keys.addingPet] ?: false }.distinctUntilChanged()
    suspend fun setAddingPet(adding: Boolean) = store.edit { it[Keys.addingPet] = adding }

    /** When the welcome chest's 50% off ends (14h2), in epoch milliseconds; 0 before it's opened. */
    val welcomeOfferEnds: Flow<Long> = store.data.map { it[Keys.welcomeOfferEnds] ?: 0L }.distinctUntilChanged()
    suspend fun setWelcomeOfferEnds(millis: Long) = store.edit { it[Keys.welcomeOfferEnds] = millis }

    /** "Claim 50% off" was tapped before signing in: open the store sheet once signed in (14j). */
    val claimAfterSignIn: Flow<Boolean> = store.data.map { it[Keys.claimAfterSignIn] ?: false }.distinctUntilChanged()
    suspend fun setClaimAfterSignIn(claim: Boolean) = store.edit { it[Keys.claimAfterSignIn] = claim }

    /** What Opaw should help with (14f), such as "meals" and "walks", and who looks after the pet (14g). */
    val careFocus: Flow<Set<String>> = store.data.map { it[Keys.careFocus].orEmpty() }.distinctUntilChanged()
    val careHelpers: Flow<String> = store.data.map { it[Keys.careHelpers].orEmpty() }.distinctUntilChanged()
    suspend fun setCareAnswers(focus: Set<String>, helpers: String) = store.edit {
        it[Keys.careFocus] = focus
        it[Keys.careHelpers] = helpers
    }

    /** The dog currently shown on Home and targeted by quick logs. */
    val activeDogId: Flow<String?> = store.data.map { it[Keys.activeDog]?.takeIf(String::isNotEmpty) }.distinctUntilChanged()
    val caretakerName: Flow<String> = store.data.map { it[Keys.caretaker].orEmpty() }.distinctUntilChanged()
    val usesImperial: Flow<Boolean> = store.data.map { it[Keys.imperial] ?: false }.distinctUntilChanged()
    val soundsOn: Flow<Boolean> = store.data.map { it[Keys.soundsOn] ?: true }.distinctUntilChanged()
    val hapticsOn: Flow<Boolean> = store.data.map { it[Keys.hapticsOn] ?: true }.distinctUntilChanged()

    suspend fun setActiveDog(id: String?) = store.edit { it[Keys.activeDog] = id.orEmpty() }
    suspend fun setCaretakerName(name: String) = store.edit { it[Keys.caretaker] = name }
    suspend fun setSoundsOn(on: Boolean) = store.edit { it[Keys.soundsOn] = on }
    suspend fun setHapticsOn(on: Boolean) = store.edit { it[Keys.hapticsOn] = on }

    suspend fun setUsesImperial(on: Boolean) {
        store.edit { it[Keys.imperial] = on }
        OpawFormat.usesImperial = on
    }

    /** Copies the Units setting into `OpawFormat` so formatting matches. Call once at launch. */
    suspend fun syncFormat() {
        OpawFormat.usesImperial = usesImperial.first()
    }

    suspend fun clear() = store.edit { it.clear() }
}
