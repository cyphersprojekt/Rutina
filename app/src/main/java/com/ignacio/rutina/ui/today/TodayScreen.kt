@file:OptIn(ExperimentalMaterial3Api::class)

package com.ignacio.rutina.ui.today

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ignacio.rutina.domain.AgendaItem
import com.ignacio.rutina.domain.CategoryProgress
import com.ignacio.rutina.domain.CounterItem
import com.ignacio.rutina.domain.SlotOutcome
import com.ignacio.rutina.domain.quickIncrements
import com.ignacio.rutina.domain.toLocalDateTime
import com.ignacio.rutina.ui.components.AmountDialog
import com.ignacio.rutina.ui.components.EmptyHint
import com.ignacio.rutina.ui.components.SectionHeader
import com.ignacio.rutina.ui.components.TimeDialog
import com.ignacio.rutina.ui.components.repository
import com.ignacio.rutina.ui.dayTitle
import com.ignacio.rutina.ui.formatDelay
import com.ignacio.rutina.ui.hhmm
import com.ignacio.rutina.ui.long
import com.ignacio.rutina.ui.short
import com.ignacio.rutina.ui.theme.late
import com.ignacio.rutina.ui.title
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun TodayScreen(onOpenTask: (Long) -> Unit, onNewTask: () -> Unit, bottomBar: @Composable () -> Unit) {
    val repo = repository()
    val viewModel = viewModel { TodayViewModel(repo) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberNow()
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshToday()
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // Se guarda la clave y no el ítem, para que la hoja muestre siempre el estado actual.
    var agendaKey by remember { mutableStateOf<Pair<Long, LocalTime>?>(null) }
    var counterKey by remember { mutableStateOf<Long?>(null) }
    var timeDialogKey by remember { mutableStateOf<Pair<Long, LocalTime>?>(null) }
    var amountDialogKey by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            val day = state
            CenterAlignedTopAppBar(
                title = {
                    if (day != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dayTitle(day.date, day.today))
                            Text(
                                day.date.long(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.shiftDay(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Día anterior")
                    }
                },
                actions = {
                    if (day != null && day.date != day.today) {
                        TextButton(onClick = viewModel::goToToday) { Text("Hoy") }
                    }
                    IconButton(onClick = { viewModel.shiftDay(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Día siguiente")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewTask) { Icon(Icons.Default.Add, contentDescription = "Nueva tarea") }
        },
        bottomBar = bottomBar,
    ) { padding ->
        val day = state ?: return@Scaffold
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            if (!notificationsEnabled) {
                item {
                    NotificationsOffBanner(
                        onEnable = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        },
                    )
                }
            }
            if (!day.hasTasks) {
                item { EmptyHint("Todavía no tenés tareas.\nTocá + para crear la primera.", Modifier.padding(top = 48.dp)) }
                return@LazyColumn
            }

            if (day.categories.isNotEmpty()) {
                item { CategoryFilters(day, onSelect = viewModel::selectFilter) }
            }
            val focus = day.filter?.let { filter -> day.categories.find { it.id == filter } } ?: day.overall
            if (focus.isComplete) {
                item { AllDoneLine(day, focus.category?.name?.takeIf { day.filter != null }) }
            }

            item { SectionHeader("Recordatorios") }
            if (day.agenda.isEmpty()) {
                item {
                    EmptyHint(
                        if (day.filter == null) {
                            "No hay nada programado para este día."
                        } else {
                            "No hay recordatorios de esta categoría para este día."
                        },
                    )
                }
            }
            // Si ninguna tarea del día tiene ícono no se reserva el lugar.
            val showIcons = day.agenda.any { it.task.icon.isNotEmpty() }
            items(day.agenda, key = { "a-${it.task.id}-${it.time}" }) { item ->
                AgendaRow(
                    item = item,
                    showIcon = showIcons,
                    now = now,
                    zone = repo.zone,
                    canMark = item.entry != null || !day.isFuture,
                    onToggle = { viewModel.toggle(item) },
                    onClick = { agendaKey = item.task.id to item.time },
                )
            }

            if (day.counters.isNotEmpty()) {
                item { SectionHeader("Contadores") }
                items(day.counters, key = { "c-${it.task.id}" }) { item ->
                    CounterRow(
                        item = item,
                        zone = repo.zone,
                        canAdd = !day.isFuture,
                        onAdd = { amount -> viewModel.addCount(item.task, amount, day.date) },
                        onClick = { counterKey = item.task.id },
                    )
                }
            }
            // Espacio para que el botón flotante no tape la última fila.
            item { Spacer(Modifier.height(88.dp)) }
        }

        val sheetItem = agendaKey?.let { key -> day.agenda.find { it.task.id == key.first && it.time == key.second } }
        if (sheetItem != null) {
            val close = { agendaKey = null }
            AgendaSheet(
                item = sheetItem,
                zone = repo.zone,
                canMarkNow = !day.isFuture,
                onDismiss = close,
                onDoneNow = { viewModel.markDoneNow(sheetItem); close() },
                onPickTime = { timeDialogKey = agendaKey; close() },
                onUnmark = { viewModel.unmark(sheetItem); close() },
                onOpenTask = { close(); onOpenTask(sheetItem.task.id) },
            )
        }

        val counterItem = counterKey?.let { id -> day.counters.find { it.task.id == id } }
        if (counterItem != null) {
            val close = { counterKey = null }
            CounterSheet(
                item = counterItem,
                zone = repo.zone,
                canAdd = !day.isFuture,
                onDismiss = close,
                onAddCustom = { amountDialogKey = counterKey; close() },
                onUndoLast = { counterItem.entries.lastOrNull()?.let(viewModel::deleteEntry); close() },
                onOpenTask = { close(); onOpenTask(counterItem.task.id) },
            )
        }

        val timeItem = timeDialogKey?.let { key -> day.agenda.find { it.task.id == key.first && it.time == key.second } }
        if (timeItem != null) {
            TimeDialog(
                title = "¿A qué hora la hiciste?",
                initial = timeItem.entry?.doneAt?.toLocalDateTime(repo.zone)?.toLocalTime() ?: timeItem.time,
                onDismiss = { timeDialogKey = null },
                onConfirm = { time ->
                    viewModel.markDoneAt(timeItem, time)
                    timeDialogKey = null
                },
            )
        }

        val amountItem = amountDialogKey?.let { id -> day.counters.find { it.task.id == id } }
        if (amountItem != null) {
            AmountDialog(
                title = amountItem.task.title,
                unit = amountItem.task.unit,
                onDismiss = { amountDialogKey = null },
                onConfirm = { amount ->
                    viewModel.addCount(amountItem.task, amount, day.date)
                    amountDialogKey = null
                },
            )
        }
    }
}

/** La hora actual, refrescada cada 30 segundos para que "Pendiente" pase a "Atrasada" sola. */
@Composable
private fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        delay(30_000)
        value = LocalDateTime.now()
    }
}

@Composable
private fun AgendaRow(
    item: AgendaItem,
    showIcon: Boolean,
    now: LocalDateTime,
    zone: ZoneId,
    canMark: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val done = item.entry != null
    val (status, statusColor) = agendaStatus(item, now, zone)
    val dimmed = MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.time.hhmm(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (done) dimmed else MaterialTheme.colorScheme.onSurface,
                )
                if (showIcon) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(start = 12.dp).width(32.dp)) {
                        Text(item.task.icon, fontSize = 22.sp, modifier = Modifier.alpha(if (done) 0.5f else 1f))
                    }
                }
            }
        },
        headlineContent = {
            Text(
                item.task.name,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done) dimmed else Color.Unspecified,
            )
        },
        supportingContent = { Text(status, color = statusColor) },
        trailingContent = {
            Checkbox(checked = done, onCheckedChange = { onToggle() }, enabled = canMark)
        },
    )
}

@Composable
private fun agendaStatus(item: AgendaItem, now: LocalDateTime, zone: ZoneId): Pair<String, Color> {
    val entry = item.entry
    if (entry != null) {
        val doneAt = entry.doneAt.toLocalDateTime(zone)
        val outcome = SlotOutcome(item.date, item.time, doneAt)
        val whenText = if (doneAt.toLocalDate() == item.date) {
            "a las ${doneAt.hhmm()}"
        } else {
            "el ${doneAt.toLocalDate().short()} a las ${doneAt.hhmm()}"
        }
        val color = if (outcome.isOnTime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.late
        return "Hecha $whenText · ${formatDelay(outcome.delayMinutes!!)}" to color
    }
    return when {
        item.scheduled.isAfter(now) -> "Pendiente" to MaterialTheme.colorScheme.onSurfaceVariant
        item.date == now.toLocalDate() -> "Atrasada" to MaterialTheme.colorScheme.error
        else -> "No hecha" to MaterialTheme.colorScheme.error
    }
}

@Composable
private fun CounterRow(item: CounterItem, zone: ZoneId, canAdd: Boolean, onAdd: (Int) -> Unit, onClick: () -> Unit) {
    val last = item.entries.lastOrNull()
    val detail = if (last == null) {
        "Nada este día"
    } else {
        listOfNotNull(
            item.task.unit.ifBlank { null },
            "última a las ${last.doneAt.toLocalDateTime(zone).hhmm()}",
        ).joinToString(" · ")
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Text(
                "${item.total}",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 40.dp),
            )
        },
        headlineContent = { Text(item.task.title) },
        supportingContent = { Text(detail) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                quickIncrements(item.task.step).forEach { amount ->
                    AddButton(amount, enabled = canAdd, onClick = { onAdd(amount) })
                }
            }
        },
    )
}

/** Más angosto que un botón estándar para que entren tres; el área tocable sigue siendo de 48dp. */
@Composable
private fun AddButton(amount: Int, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .height(36.dp)
            .widthIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.38f),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text("+$amount", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** "Hoy cumpliste con todo en Trabajo". */
@Composable
private fun AllDoneLine(day: DayState, categoryName: String?) {
    val prefix = if (day.date == day.today) "Hoy cumpliste" else "Cumpliste"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(
            if (categoryName == null) "$prefix con todo" else "$prefix con todo en $categoryName",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun CategoryFilters(day: DayState, onSelect: (Long?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        item { ProgressChip("Todas", day.overall, selected = day.filter == null, onClick = { onSelect(null) }) }
        items(day.categories, key = { it.id }) { progress ->
            ProgressChip(
                name = progress.category?.name ?: "Sin categoría",
                progress = progress,
                selected = day.filter == progress.id,
                onClick = { onSelect(progress.id) },
            )
        }
    }
}

/** "Trabajo 2/3"; con un tilde cuando están hechos todos los recordatorios del día. */
@Composable
private fun ProgressChip(name: String, progress: CategoryProgress, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(if (progress.total > 0) "$name ${progress.done}/${progress.total}" else name) },
        leadingIcon = if (progress.isComplete) {
            {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Todo hecho",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun AgendaSheet(
    item: AgendaItem,
    zone: ZoneId,
    canMarkNow: Boolean,
    onDismiss: () -> Unit,
    onDoneNow: () -> Unit,
    onPickTime: () -> Unit,
    onUnmark: () -> Unit,
    onOpenTask: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            SheetHeader(item.task.title, "Programada a las ${item.time.hhmm()} · ${item.date.long()}")
            val entry = item.entry
            if (entry == null) {
                if (canMarkNow) SheetAction(Icons.Default.Check, "Marcar hecha ahora", onDoneNow)
                SheetAction(Icons.Default.Edit, "La hice a otra hora…", onPickTime)
            } else {
                val doneAt = entry.doneAt.toLocalDateTime(zone)
                SheetAction(Icons.Default.Edit, "Cambiar la hora (hecha a las ${doneAt.hhmm()})…", onPickTime)
                SheetAction(Icons.Default.Close, "Desmarcar", onUnmark)
            }
            SheetAction(Icons.Default.Info, "Ver estadísticas", onOpenTask)
        }
    }
}

@Composable
private fun CounterSheet(
    item: CounterItem,
    zone: ZoneId,
    canAdd: Boolean,
    onDismiss: () -> Unit,
    onAddCustom: () -> Unit,
    onUndoLast: () -> Unit,
    onOpenTask: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            val unit = item.task.unit.ifBlank { "en total" }
            SheetHeader(item.task.title, "${item.total} $unit este día")
            if (canAdd) SheetAction(Icons.Default.Add, "Sumar otra cantidad…", onAddCustom)
            item.entries.lastOrNull()?.let { last ->
                SheetAction(
                    Icons.Default.Delete,
                    "Deshacer el último (+${last.amount} a las ${last.doneAt.toLocalDateTime(zone).hhmm()})",
                    onUndoLast,
                )
            }
            SheetAction(Icons.Default.Info, "Ver estadísticas", onOpenTask)
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleLarge) },
        supportingContent = { Text(subtitle) },
    )
    HorizontalDivider()
}

@Composable
private fun SheetAction(icon: ImageVector, text: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(text) },
    )
}

@Composable
private fun NotificationsOffBanner(onEnable: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text("Las notificaciones están desactivadas", style = MaterialTheme.typography.titleSmall)
            Text(
                "Sin ellas no te van a llegar los avisos de los recordatorios.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onEnable, modifier = Modifier.align(Alignment.End)) { Text("Activar") }
        }
    }
}
