package com.ignacio.rutina.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.ignacio.rutina.MainActivity
import com.ignacio.rutina.R
import com.ignacio.rutina.RutinaApp
import com.ignacio.rutina.domain.CounterItem
import com.ignacio.rutina.domain.quickIncrements
import com.ignacio.rutina.ui.title
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Widget con todos los contadores de hoy y sus botones para sumar sin abrir la app. */
class CountersWidget : GlanceAppWidget() {
    // El diseño de cada fila depende del ancho real del widget.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val counters = (context.applicationContext as RutinaApp).repository.countersOn(LocalDate.now())
        val initial = counters.first()
        Widgets.scheduleNewDay(context)
        // Mientras el widget está activo, lo que se suma (acá o en la app) aparece solo.
        provideContent {
            val items by counters.collectAsState(initial)
            GlanceTheme { Content(items) }
        }
    }
}

class CountersWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountersWidget()
}

@Composable
private fun Content(counters: List<CounterItem>) {
    val openApp = actionStartActivity<MainActivity>()
    Scaffold(
        titleBar = {
            TitleBar(
                startIcon = ImageProvider(R.drawable.ic_stat_check),
                title = "Contadores de hoy",
                modifier = GlanceModifier.clickable(openApp),
            )
        },
    ) {
        if (counters.isEmpty()) {
            Box(GlanceModifier.fillMaxSize().clickable(openApp), contentAlignment = Alignment.Center) {
                Text(
                    "Todavía no tenés contadores. Creá uno desde la app.",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(counters, itemId = { it.task.id }) { item -> CounterRow(item, openApp) }
            }
        }
    }
}

@Composable
private fun CounterRow(item: CounterItem, openApp: Action) {
    val task = item.task
    // Si los botones no entran al lado del nombre, van debajo.
    val sideBySide = LocalSize.current.width >= 120.dp + 56.dp * quickIncrements(task.step).size
    val nameStyle = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp)
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp).clickable(openApp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${item.total}",
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            modifier = GlanceModifier.width(52.dp),
        )
        if (sideBySide) {
            Text(
                task.title,
                maxLines = 2,
                style = nameStyle,
                modifier = GlanceModifier.defaultWeight().padding(horizontal = 8.dp),
            )
            AddButtons(task)
        } else {
            Column(GlanceModifier.defaultWeight().padding(start = 8.dp)) {
                Text(task.title, maxLines = 1, style = nameStyle)
                Spacer(GlanceModifier.height(4.dp))
                AddButtons(task)
            }
        }
    }
}
