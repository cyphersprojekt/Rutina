@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ignacio.rutina.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ignacio.rutina.data.ALL_DAYS
import com.ignacio.rutina.data.CounterGoal
import com.ignacio.rutina.data.TaskKind
import com.ignacio.rutina.data.bit
import com.ignacio.rutina.domain.quickIncrements
import com.ignacio.rutina.ui.components.CategoryPicker
import com.ignacio.rutina.ui.components.ChoiceRow
import com.ignacio.rutina.ui.components.ConfirmDialog
import com.ignacio.rutina.ui.components.DateDialog
import com.ignacio.rutina.ui.components.EmojiButton
import com.ignacio.rutina.ui.components.EmojiPickerSheet
import com.ignacio.rutina.ui.components.TimeDialog
import com.ignacio.rutina.ui.components.repository
import com.ignacio.rutina.ui.dayLetters
import com.ignacio.rutina.ui.fullName
import com.ignacio.rutina.ui.hhmm
import com.ignacio.rutina.ui.long
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.temporal.ChronoUnit

private const val NEW_TIME = -1

@Composable
fun EditTaskScreen(taskId: Long, onBack: () -> Unit, onSaved: () -> Unit, onDeleted: () -> Unit) {
    val repo = repository()
    val viewModel = viewModel { EditTaskViewModel(repo, taskId) }
    val form = viewModel.form
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel.result) {
        when (viewModel.result) {
            EditResult.SAVED -> onSaved()
            EditResult.DELETED -> onDeleted()
            null -> Unit
        }
    }
    var askDelete by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<Int?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingIcon by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "Nueva tarea" else "Editar tarea") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (!viewModel.isNew) {
                        IconButton(onClick = { askDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar tarea")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(16.dp),
            ) {
                form.problem?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                Button(onClick = viewModel::save, enabled = form.problem == null, modifier = Modifier.fillMaxWidth()) {
                    Text("Guardar")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                EmojiButton(emoji = form.icon, onClick = { pickingIcon = true })
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = form.name,
                    onValueChange = { value -> viewModel.edit { copy(name = value) } },
                    label = { Text("Nombre") },
                    placeholder = { Text("Lavar los platos") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("Categoría")
                CategoryPicker(
                    categories = categories,
                    selected = form.categoryId,
                    onSelect = { id -> viewModel.edit { copy(categoryId = id) } },
                    onCreate = viewModel::createCategory,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceRow(
                    options = listOf("Recordatorio", "Contador"),
                    selected = if (form.kind == TaskKind.REMINDER) 0 else 1,
                    onSelect = { index ->
                        viewModel.edit { copy(kind = if (index == 0) TaskKind.REMINDER else TaskKind.COUNTER) }
                    },
                    // Cambiar el tipo después rompería el historial que ya tiene.
                    enabled = viewModel.isNew,
                )
                Hint(
                    if (form.kind == TaskKind.REMINDER) {
                        "Te avisa a una hora y lo marcás como hecho."
                    } else {
                        "Sin horario: sumás cuántas veces lo hiciste, cuando quieras."
                    },
                )
            }

            if (form.kind == TaskKind.REMINDER) {
                Label("¿Qué días?")
                ChoiceRow(
                    options = listOf("Se repite", "Una sola vez"),
                    selected = if (form.once) 1 else 0,
                    onSelect = { index -> viewModel.edit { copy(once = index == 1) } },
                )
                if (form.once) {
                    OutlinedButton(onClick = { pickingDate = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(form.onceDate.long())
                    }
                } else {
                    DaysPicker(form.daysMask) { mask -> viewModel.edit { copy(daysMask = mask) } }
                }

                Label("Horarios")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    form.times.forEachIndexed { index, time ->
                        InputChip(
                            selected = false,
                            onClick = { editingTime = index },
                            label = { Text(time.hhmm()) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Quitar ${time.hhmm()}",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { viewModel.edit { copy(times = times - time) } },
                                )
                            },
                        )
                    }
                    AssistChip(
                        onClick = { editingTime = NEW_TIME },
                        label = { Text("Agregar horario") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                Hint("Si lo hacés varias veces por día, agregá un horario por cada vez.")
            } else {
                OutlinedTextField(
                    value = form.unit,
                    onValueChange = { value -> viewModel.edit { copy(unit = value) } },
                    label = { Text("Unidad (opcional)") },
                    placeholder = { Text("flexiones") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.step,
                    onValueChange = { value -> viewModel.edit { copy(step = value.filter(Char::isDigit).take(4)) } },
                    label = { Text("Botón más grande") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                val buttons = form.step.toIntOrNull()?.takeIf { it > 0 }?.let(::quickIncrements).orEmpty()
                Hint(
                    if (buttons.isEmpty()) {
                        "Los botones para sumar rápido salen de este número."
                    } else {
                        "En Hoy vas a tener ${buttons.joinToString(", ") { "+$it" }}. " +
                            "Para otra cantidad, tocá el contador."
                    },
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label("Objetivo")
                    ChoiceRow(
                        options = listOf("Hacer más", "Hacer menos"),
                        selected = if (form.goal == CounterGoal.MORE) 0 else 1,
                        onSelect = { index ->
                            viewModel.edit { copy(goal = if (index == 0) CounterGoal.MORE else CounterGoal.LESS) }
                        },
                    )
                    Hint(
                        if (form.goal == CounterGoal.MORE) {
                            "Cuanto más, mejor (ej. flexiones): en las estadísticas, que suba es bueno."
                        } else {
                            "Cuanto menos, mejor (ej. cigarrillos): en las estadísticas, que baje es bueno."
                        },
                    )
                }
            }
        }
    }

    editingTime?.let { index ->
        val initial = form.times.getOrNull(index)
            ?: form.times.lastOrNull()?.plusHours(1)
            ?: LocalTime.now().plusHours(1).truncatedTo(ChronoUnit.HOURS)
        TimeDialog(
            title = if (index == NEW_TIME) "Nuevo horario" else "Cambiar horario",
            initial = initial,
            onDismiss = { editingTime = null },
            onConfirm = { time ->
                viewModel.edit {
                    val updated = if (index == NEW_TIME) times + time else times.toMutableList().also { it[index] = time }
                    copy(times = updated.distinct().sorted())
                }
                editingTime = null
            },
        )
    }

    if (pickingDate) {
        DateDialog(
            initial = form.onceDate,
            onDismiss = { pickingDate = false },
            onConfirm = { date ->
                viewModel.edit { copy(onceDate = date) }
                pickingDate = false
            },
        )
    }

    if (pickingIcon) {
        EmojiPickerSheet(
            selected = form.icon,
            onPick = { emoji ->
                viewModel.edit { copy(icon = emoji) }
                pickingIcon = false
            },
            onDismiss = { pickingIcon = false },
        )
    }

    if (askDelete) {
        ConfirmDialog(
            title = "¿Borrar \"${form.name}\"?",
            text = "Se borra también todo su historial.",
            confirmLabel = "Borrar",
            onDismiss = { askDelete = false },
            onConfirm = {
                askDelete = false
                viewModel.delete()
            },
        )
    }
}

@Composable
private fun DaysPicker(mask: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            DayOfWeek.entries.forEach { day ->
                val selected = mask and day.bit() != 0
                Surface(
                    onClick = { onChange(mask xor day.bit()) },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(40.dp)
                        .semantics {
                            contentDescription = day.fullName()
                            this.selected = selected
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(dayLetters[day.value - 1], style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
        Row {
            TextButton(onClick = { onChange(ALL_DAYS) }) { Text("Todos los días") }
            TextButton(onClick = { onChange(0b0011111) }) { Text("Lunes a viernes") }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
