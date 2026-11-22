package app.opaw.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Screens pushed over the tabs. Detail screens hide the floating tab bar, as in the design. */
sealed interface Route {
    data class Today(val dogId: String) : Route
    data class History(val dogId: String) : Route
    data class Schedule(val dogId: String) : Route
    data class WalkDetail(val walkId: String) : Route
    data class MemoryDetail(val memoryId: String) : Route
    data class Profile(val dogId: String) : Route
    /** The pet's profile (2j): age, weight, birthday and more. */
    data class PetProfile(val dogId: String) : Route
    data class Household(val dogId: String) : Route
    data class FamilyTree(val dogId: String) : Route
    data class Studio(val dogId: String) : Route
    /** Photos from four angles, to bring a pet to life in 3D (9a). */
    data class LifePhotos(val dogId: String) : Route
    /** A pet coming to life: the checkpoints while it works, then the 3D reveal. */
    data class Life(val dogId: String) : Route
    /** Decorate the pet's room: the shop (16b). */
    data class Decorate(val dogId: String) : Route
    data object Challenges : Route
    data object Units : Route
    data object Pack : Route
    data object Subscription : Route
    data object Paywall : Route
}

/** A push/pop stack, the counterpart of the NavigationStack in each iOS tab. */
class Navigator {
    val stack = mutableStateListOf<Route>()

    fun push(route: Route) {
        stack += route
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun popToRoot() = stack.clear()
}

val LocalNavigator = staticCompositionLocalOf { Navigator() }
