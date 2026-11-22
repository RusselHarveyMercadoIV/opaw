package app.opaw.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.opaw.MainActivity
import app.opaw.OpawApplication
import app.opaw.R
import app.opaw.core.CareKind
import app.opaw.data.CareEvent
import app.opaw.data.DogData
import app.opaw.services.WidgetRefresher
import kotlinx.coroutines.flow.first
import java.time.Instant

private val ink = ColorProvider(Color(0xFF111111))
private val muted = ColorProvider(Color(0xFF666666))
private val ground = ColorProvider(Color(0xFFF4F4F5))
private val white = ColorProvider(Color.White)
private val kindKey = ActionParameters.Key<String>("kind")

/** The active dog, as the widget and shortcuts log to it. Mirrors `CareLogger` on iOS. */
suspend fun activeDogData(context: Context): DogData? {
    val app = context.applicationContext as OpawApplication
    val dogs = app.repository.allDogs()
    val id = app.settings.activeDogId.first()
    val dog = dogs.firstOrNull { it.id == id } ?: dogs.firstOrNull() ?: return null
    return app.repository.loadDogData(dog.id)
}

/** Logs a care event for the active dog. Returns the dog's name, or null when there's no dog. */
suspend fun logForActiveDog(context: Context, kind: CareKind): String? {
    val app = context.applicationContext as OpawApplication
    val data = activeDogData(context) ?: return null
    app.repository.saveEvent(CareEvent(dogId = data.dog.id, kindRaw = kind.raw, loggedBy = app.settings.caretakerName.first()))
    WidgetRefresher.refresh(context)
    return data.dog.name
}

fun CareKind.widgetIcon(): Int = when (this) {
    CareKind.MEAL -> R.drawable.ic_meal
    CareKind.WATER -> R.drawable.ic_water
    CareKind.PEE -> R.drawable.ic_pee
    else -> R.drawable.ic_poop
}

/** Home screen quick log: meals, water and potty breaks with one tap. Mirrors `QuickLogWidget`. */
class QuickLogWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(140.dp, 140.dp), DpSize(280.dp, 140.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = activeDogData(context)
        val now = Instant.now()
        val counts = CareKind.quickLog.associateWith { data?.count(it, now) ?: 0 }
        provideContent { GlanceTheme { Content(data?.dog?.name, counts) } }
    }

    @Composable
    private fun Content(name: String?, counts: Map<CareKind, Int>) {
        val wide = LocalSize.current.width >= 260.dp
        Column(GlanceModifier.fillMaxSize().background(ground).cornerRadius(24.dp).padding(12.dp)) {
            Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()), verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(R.drawable.ic_notification), null, GlanceModifier.size(16.dp))
                Spacer(GlanceModifier.width(6.dp))
                Text(name ?: "Opaw", style = TextStyle(color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            Spacer(GlanceModifier.height(8.dp))
            if (name == null) {
                Text("Open Opaw to add your dog.", style = TextStyle(color = muted, fontSize = 13.sp))
            } else {
                val rows = if (wide) listOf(CareKind.quickLog) else CareKind.quickLog.chunked(2)
                rows.forEachIndexed { index, row ->
                    if (index > 0) Spacer(GlanceModifier.height(6.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        row.forEachIndexed { i, kind ->
                            if (i > 0) Spacer(GlanceModifier.width(6.dp))
                            Column(
                                GlanceModifier.defaultWeight().height(48.dp).background(white).cornerRadius(16.dp)
                                    .clickable(actionRunCallback<LogCareAction>(actionParametersOf(kindKey to kind.raw))),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Image(ImageProvider(kind.widgetIcon()), "Log ${kind.title}", GlanceModifier.size(19.dp))
                                Text("${counts[kind] ?: 0}", style = TextStyle(color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A widget button: logs one kind for the active dog. */
class LogCareAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val kind = CareKind.fromRaw(parameters[kindKey]) ?: return
        logForActiveDog(context, kind)
    }
}

class QuickLogWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickLogWidget()
}

/** How your pet is doing today, out of 100. Mirrors `CareScoreWidget`. */
class CareScoreWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = activeDogData(context)
        val score = data?.careReport()?.score
        provideContent {
            Box(
                GlanceModifier.fillMaxSize().background(ColorProvider(Color(0xFFF2600C))).cornerRadius(40.dp).clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(score?.toString() ?: "–", style = TextStyle(color = white, fontSize = 26.sp, fontWeight = FontWeight.Bold))
                    Text(data?.dog?.name ?: "Opaw", style = TextStyle(color = ColorProvider(Color(0xFFFFE3D1)), fontSize = 11.sp), maxLines = 1)
                }
            }
        }
    }
}

class CareScoreWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CareScoreWidget()
}

/** Called after any change so the widgets show today's counts and score. */
suspend fun refreshWidgets(context: Context) {
    runCatching {
        QuickLogWidget().updateAll(context)
        CareScoreWidget().updateAll(context)
    }
}
