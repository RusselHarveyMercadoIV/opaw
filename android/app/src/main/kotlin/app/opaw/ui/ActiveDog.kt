package app.opaw.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.opaw.OpawApplication
import app.opaw.data.Dog
import app.opaw.data.DogData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@Composable
fun rememberApp(): OpawApplication = LocalContext.current.applicationContext as OpawApplication

/** All dogs, and the active one with its data: the dog chosen in a pet chip, or the first. */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun rememberActiveDog(): Pair<List<Dog>, DogData?> {
    val app = rememberApp()
    val flow = remember {
        combine(app.repository.dogs, app.settings.activeDogId) { dogs, active -> dogs to (dogs.firstOrNull { it.id == active } ?: dogs.firstOrNull()) }
            .flatMapLatest { (dogs, dog) ->
                if (dog == null) flowOf(dogs to null) else app.repository.dogData(dog.id).let { data -> combine(flowOf(dogs), data) { d, x -> d to x } }
            }
    }
    val state by flow.collectAsStateWithLifecycle(emptyList<Dog>() to null)
    return state
}

/** One dog's data, live. */
@Composable
fun rememberDogData(dogId: String): DogData? {
    val app = rememberApp()
    val flow = remember(dogId) { app.repository.dogData(dogId) }
    val state by flow.collectAsStateWithLifecycle(null)
    return state
}
