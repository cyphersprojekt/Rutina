@file:OptIn(ExperimentalMaterial3Api::class)

package com.ignacio.rutina.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ignacio.rutina.data.Entry
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.domain.SlotOutcome
import com.ignacio.rutina.domain.toLocalDateTime
import com.ignacio.rutina.ui.components.ChoiceRow
import com.ignacio.rutina.ui.components.ConfirmDialog
import com.ignacio.rutina.ui.components.DailyBarChart
import com.ignacio.rutina.ui.components.EmptyHint
import com.ignacio.rutina.ui.components.PunctualityChart
import com.ignacio.rutina.ui.components.PunctualityLegend
import com.ignacio.rutina.ui.components.SectionHeader
import com.ignacio.rutina.ui.components.StatCard
import com.ignacio.rutina.ui.components.TrendSummary
import com.ignacio.rutina.ui.components.repository
import com.ignacio.rutina.ui.formatDelay
import com.ignacio.rutina.ui.hhmm
import com.ignacio.rutina.ui.oneDecimal
import com.ignacio.rutina.ui.percent
import com.ignacio.rutina.ui.scheduleSummary
import com.ignacio.rutina.ui.short
import com.ignacio.rutina.ui.theme.late
import com.ignacio.rutina.ui.title
import com.ignacio.rutina.ui.withWeekday
import java.time.ZoneId

@Composable
fun TaskDetailScreen(taskId: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val repo = repository()
    val viewModel = viewModel { TaskDetailViewModel(repo, taskId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val task = when (val current = state) {
        is DetailState.Reminder -> current.task
        is DetailState.Counter -> current.task
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(task?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (task != null) {
                        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                    }
                },
            )
        },
    ) { padding ->
        when (val current = state) {
            DetailState.Loading -> Unit
            DetailState.Missing -> EmptyHint("Esta tarea ya no existe.", Modifier.padding(padding))
            is DetailState.Reminder -> ReminderDetail(current, range, viewModel::setRange, padding)
            is DetailState.Counter -> CounterDetail(current, range, viewModel::setRange, viewModel::deleteEntry, repo.zone, padding)
        }
    }
}

private fun LazyListScope.header(task: Task, range: Int, onRange: (Int) -> Unit) {
    item {
        Text(
            scheduleSummary(task),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    item {
        ChoiceRow(
            options = RANGE_OPTIONS.map { "$it días" },
            selected = RANGE_OPTIONS.indexOf(range),
            onSelect = { onRange(RANGE_OPTIONS[it]) },
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun ReminderDetail(
    state: DetailState.Reminder,
    range: Int,
    onRange: (Int) -> Unit,
    padding: PaddingValues,
) {
    val stats = state.stats
    LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        header(state.task, range, onRange)
        if (stats.total == 0) {
            item {
                EmptyHint("Todavía no hay datos en este período. Aparecen cuando marcás la tarea o cuando pasa un día completo.")
            }
            return@LazyColumn
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(
                    label = "Cumplimiento",
                    value = stats.adherence?.let(::percent) ?: "—",
                    caption = "${stats.done} de ${stats.total}",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Demora típica",
                    value = stats.medianDelay?.let(::formatDelay) ?: "—",
                    caption = "mediana",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "A tiempo",
                    value = if (stats.done == 0) "—" else percent(stats.onTime.toFloat() / stats.done),
                    caption = "de las hechas",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (stats.bySlot.size > 1) {
            item { SectionHeader("Por horario") }
            items(stats.bySlot, key = { "slot-${it.time}" }) { slot ->
                ListItem(
                    headlineContent = { Text(slot.time.hhmm()) },
                    supportingContent = { Text("Hecha ${slot.done} de ${slot.total}") },
                    trailingContent = { slot.medianDelay?.let { Text(formatDelay(it)) } },
                )
            }
        }
        item { SectionHeader("¿A qué hora la marcás?") }
        item {
            PunctualityChart(
                outcomes = stats.outcomes,
                from = state.from,
                to = state.today,
                slotTimes = state.task.times,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item { PunctualityLegend(Modifier.padding(16.dp)) }
        item { SectionHeader("Historial") }
        items(stats.outcomes.asReversed(), key = { "o-${it.date}-${it.time}" }) { OutcomeRow(it) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun OutcomeRow(outcome: SlotOutcome) {
    val doneAt = outcome.doneAt
    ListItem(
        headlineContent = { Text("${outcome.date.withWeekday()} · ${outcome.time.hhmm()}") },
        supportingContent = {
            Text(
                when {
                    doneAt == null -> "No hecha"
                    doneAt.toLocalDate() == outcome.date -> "Hecha a las ${doneAt.hhmm()}"
                    else -> "Hecha el ${doneAt.toLocalDate().short()} a las ${doneAt.hhmm()}"
                },
            )
        },
        trailingContent = {
            val delay = outcome.delayMinutes
            if (delay == null) {
                Text("—", color = MaterialTheme.colorScheme.error)
            } else {
                Text(
                    formatDelay(delay),
                    color = if (outcome.isOnTime) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.late,
                )
            }
        },
    )
}

@Composable
private fun CounterDetail(
    state: DetailState.Counter,
    range: Int,
    onRange: (Int) -> Unit,
    onDeleteEntry: (Entry) -> Unit,
    zone: ZoneId,
    padding: PaddingValues,
) {
    val stats = state.stats
    val unit = state.task.unit
    var toDelete by remember { mutableStateOf<Entry?>(null) }

    LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        header(state.task, range, onRange)
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Hoy", "${stats.today}", unit.ifBlank { null }, Modifier.weight(1f))
                StatCard("Promedio", oneDecimal(stats.dailyAverage), "por día", Modifier.weight(1f))
                StatCard("Total", "${stats.total}", "en $range días", Modifier.weight(1f))
            }
        }
        stats.best?.let { (date, amount) ->
            item {
                Text(
                    "Mejor día: ${date.withWeekday()}, con $amount ${unit}".trimEnd(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
        }
        item { SectionHeader("Por día") }
        item { DailyBarChart(stats.daily, stats.trend, state.task.goal, Modifier.padding(horizontal = 16.dp)) }
        item { TrendSummary(stats.trend, state.task.goal, unit, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) }
        item { SectionHeader("Registros") }
        if (state.history.isEmpty()) {
            item { EmptyHint("No hay registros en este período.") }
        }
        items(state.history, key = { it.id }) { entry ->
            val at = entry.doneAt.toLocalDateTime(zone)
            ListItem(
                headlineContent = { Text("+${entry.amount} $unit".trimEnd()) },
                supportingContent = { Text("${at.toLocalDate().withWeekday()} · ${at.hhmm()}") },
                trailingContent = {
                    IconButton(onClick = { toDelete = entry }) {
                        Icon(Icons.Default.Delete, contentDescription = "Borrar registro")
                    }
                },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    toDelete?.let { entry ->
        ConfirmDialog(
            title = "¿Borrar este registro?",
            text = "+${entry.amount} $unit del ${entry.doneAt.toLocalDateTime(zone).toLocalDate().short()}".replace("  ", " "),
            confirmLabel = "Borrar",
            onDismiss = { toDelete = null },
            onConfirm = {
                onDeleteEntry(entry)
                toDelete = null
            },
        )
    }
}

