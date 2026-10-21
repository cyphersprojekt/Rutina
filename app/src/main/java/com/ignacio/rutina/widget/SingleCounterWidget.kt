package com.ignacio.rutina.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.ignacio.rutina.MainActivity
import com.ignacio.rutina.RutinaApp
import com.ignacio.rutina.domain.CounterItem
import com.ignacio.rutina.ui.title
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Widget de un solo contador, elegido en [SingleCounterConfigActivity] al agregarlo. */
class SingleCounterWidget : GlanceAppWidget() {
    // Con una fila de alto se pasa a un diseño horizontal.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val counters = (context.applicationContext as RutinaApp).repository.countersOn(LocalDate.now())
        val initial = counters.first()
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        Widgets.scheduleNewDay(context)
        provideContent {
            // Se lee acá adentro para que al elegir otro contador cambie sin esperar a otra sesión.
            val taskId = currentState(TASK)
            val items by counters.collectAsState(initial)
            GlanceTheme {
                Content(
                    item = items.find { it.task.id == taskId },
                    chosen = taskId != null,
                    choose = actionStartActivity<SingleCounterConfigActivity>(
                        actionParametersOf(ActionParameters.Key<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID) to appWidgetId),
                    ),
                )
            }
        }
    }

    companion object {
        private val TASK = longPreferencesKey("task")

        /** Hace que el widget [appWidgetId] muestre el contador [taskId]. */
        suspend fun assign(context: Context, appWidgetId: Int, taskId: Long) {
            val id = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, id) { it[TASK] = taskId }
            SingleCounterWidget().update(context, id)
        }
    }
}

class SingleCounterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleCounterWidget()
}

@Composable
private fun Content(item: CounterItem?, chosen: Boolean, choose: Action) {
    Scaffold {
        when {
            item == null -> Box(GlanceModifier.fillMaxSize().clickable(choose), contentAlignment = Alignment.Center) {
                Text(
                    if (chosen) "Este contador se borró. Tocá para elegir otro." else "Tocá para elegir un contador.",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            LocalSize.current.height >= 100.dp -> Tall(item)
            else -> Short(item)
        }
    }
}

/** Nombre, total y debajo los botones. */
@Composable
private fun Tall(item: CounterItem) {
    val openApp = actionStartActivity<MainActivity>()
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            item.task.title,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            modifier = GlanceModifier.clickable(openApp),
        )
        Text(
            "${item.total}",
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.clickable(openApp),
        )
        Spacer(GlanceModifier.height(6.dp))
        AddButtons(item.task, GlanceModifier.fillMaxWidth(), fill = true)
    }
}

/** Para una sola fila de alto: el total (con el ícono, si tiene) y los botones al lado. */
@Composable
private fun Short(item: CounterItem) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            listOf(item.task.icon, "${item.total}").filter { it.isNotEmpty() }.joinToString(" "),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.padding(end = 10.dp).clickable(actionStartActivity<MainActivity>()),
        )
        AddButtons(item.task, GlanceModifier.defaultWeight(), fill = true)
    }
}
