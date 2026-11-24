package app.opaw.services

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Whether Opaw Premium is on. Set from Play Billing by [PremiumStore]. */
object Premium {
    var isPremium by mutableStateOf(false)
}
