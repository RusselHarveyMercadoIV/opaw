package app.opaw.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opaw.OpawApplication
import app.opaw.data.Dog
import app.opaw.ui.theme.Opaw
import app.opaw.ui.theme.Space
import app.opaw.ui.theme.opaw
import app.opaw.ui.theme.opawDisplay
import kotlinx.coroutines.launch

/** A minimal "name your dog" step so the shell can be used. The full flow comes in Phase 2. */
@Composable
fun OnboardingPlaceholder(app: OpawApplication) {
    val colors = Opaw.colors
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m, Alignment.CenterVertically),
    ) {
        Text("What's your dog called?", style = opawDisplay(34.sp), color = colors.ink)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            textStyle = opaw(20.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        val enabled = name.isNotBlank()
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(if (enabled) colors.ink else colors.ghost, RoundedCornerShape(50))
                .clickable(enabled = enabled) {
                    scope.launch {
                        val dog = Dog.create(name.trim())
                        app.database.dogs().upsert(dog)
                        app.settings.setActiveDog(dog.id)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("Continue", style = opaw(17.sp, FontWeight.Bold), color = colors.onInk)
        }
    }
}
