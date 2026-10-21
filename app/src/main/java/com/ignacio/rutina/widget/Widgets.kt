package com.ignacio.rutina.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ignacio.rutina.RutinaApp
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.domain.quickIncrements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Los widgets muestran los contadores de hoy: se redibujan con cada cambio y a medianoche. */
object Widgets {
    suspend fun refresh(context: Context) {
        // `or` y no `||`: hay que actualizar los dos tipos aunque haya del primero.
        val placed = CountersWidget().updatePlaced(context, CountersWidgetReceiver::class.java) or
            SingleCounterWidget().updatePlaced(context, SingleCounterWidgetReceiver::class.java)
        // Sin widgets, la alarma de medianoche ya no se vuelve a programar.
        if (placed) scheduleNewDay(context)
    }

    /**
     * A medianoche los contadores vuelven a cero. La alarma no despierta al teléfono: si la pantalla
     * está apagada, alcanza con que se actualice al prenderla.
     */
    fun scheduleNewDay(context: Context) {
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, NewDayReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Una ventana corta no necesita permiso de alarma exacta; set() sola podría atrasarse horas.
        context.getSystemService(AlarmManager::class.java).setWindow(AlarmManager.RTC, midnight, 10 * 60_000L, intent)
    }

    /**
     * Como el updateAll de Glance, pero con los widgets que conoce Android: Glance no registra los
     * que todavía no recibieron ninguna actualización (pasa con los que se configuran al agregarlos).
     * Devuelve si había alguno.
     */
    private suspend fun GlanceAppWidget.updatePlaced(context: Context, receiver: Class<out GlanceAppWidgetReceiver>): Boolean {
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, receiver))
        val glance = GlanceAppWidgetManager(context)
        ids.forEach { update(context, glance.getGlanceIdBy(it)) }
        return ids.isNotEmpty()
    }
}

class NewDayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Widgets.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Suma la cantidad del botón al contador, a la hora actual. */
class AddCountAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TASK] ?: return
        val amount = parameters[AMOUNT] ?: return
        val repository = (context.applicationContext as RutinaApp).repository
        // El widget pudo haber quedado mostrando una tarea que ya se borró.
        if (repository.getTask(taskId) != null) repository.addCount(taskId, amount)
    }

    companion object {
        val TASK = ActionParameters.Key<Long>("task")
        val AMOUNT = ActionParameters.Key<Int>("amount")
    }
}

/** Los mismos botones para sumar que en Hoy. Con [fill] se reparten todo el ancho. */
@Composable
fun AddButtons(task: Task, modifier: GlanceModifier = GlanceModifier, fill: Boolean = false) {
    Row(modifier) {
        quickIncrements(task.step).forEachIndexed { index, amount ->
            if (index > 0) Spacer(GlanceModifier.width(6.dp))
            AddButton(task.id, amount, if (fill) GlanceModifier.defaultWeight() else GlanceModifier)
        }
    }
}

/** Más angosto que el botón de Glance, para que entren tres al lado del nombre. */
@Composable
private fun AddButton(taskId: Long, amount: Int, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .height(36.dp)
            .cornerRadius(18.dp)
            .background(GlanceTheme.colors.secondaryContainer)
            .clickable(
                actionRunCallback<AddCountAction>(
                    actionParametersOf(AddCountAction.TASK to taskId, AddCountAction.AMOUNT to amount),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "+$amount",
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSecondaryContainer,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.padding(horizontal = 10.dp),
        )
    }
}
