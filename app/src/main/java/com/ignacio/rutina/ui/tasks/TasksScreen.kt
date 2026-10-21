@file:OptIn(ExperimentalMaterial3Api::class)

package com.ignacio.rutina.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ignacio.rutina.data.Category
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.data.TaskKind
import com.ignacio.rutina.ui.components.CategoriesSheet
import com.ignacio.rutina.ui.components.EmptyHint
import com.ignacio.rutina.ui.components.SectionHeader
import com.ignacio.rutina.ui.components.repository
import com.ignacio.rutina.ui.scheduleSummary

@Composable
fun TasksScreen(onOpenTask: (Long) -> Unit, onNewTask: () -> Unit, bottomBar: @Composable () -> Unit) {
    val repo = repository()
    val tasks by repo.tasks.collectAsStateWithLifecycle(initialValue = null)
    val categories by repo.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    var managingCategories by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tareas") },
                actions = {
                    TextButton(onClick = { managingCategories = true }) { Text("Categorías") }
                    BackupMenu(snackbar)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewTask) { Icon(Icons.Default.Add, contentDescription = "Nueva tarea") }
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val all = tasks ?: return@Scaffold
        val showIcons = all.any { it.icon.isNotEmpty() }
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            if (all.isEmpty()) {
                item { EmptyHint("Todavía no tenés tareas.\nTocá + para crear la primera.", Modifier.padding(top = 48.dp)) }
            }
            sections(all, categories).forEach { (title, sectionTasks) ->
                item(key = "s-$title") { SectionHeader(title) }
                items(sectionTasks, key = { it.id }) { TaskRow(it, showIcons, onOpenTask) }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (managingCategories) {
        CategoriesSheet(categories, onDismiss = { managingCategories = false })
    }
}

/**
 * Por categoría, en el orden de las categorías y con las tareas sin categoría al final.
 * Si ninguna tarea tiene categoría se separan en recordatorios y contadores, como antes.
 */
private fun sections(tasks: List<Task>, categories: List<Category>): List<Pair<String, List<Task>>> {
    if (tasks.none { it.categoryId != null }) {
        return listOf(
            "Recordatorios" to tasks.filter { it.kind == TaskKind.REMINDER },
            "Contadores" to tasks.filter { it.kind == TaskKind.COUNTER },
        ).filter { it.second.isNotEmpty() }
    }
    val byCategory = tasks.groupBy { task -> categories.find { it.id == task.categoryId } }
    return (categories.map { it.name to byCategory[it].orEmpty() } + ("Sin categoría" to byCategory[null].orEmpty()))
        .filter { it.second.isNotEmpty() }
}

@Composable
private fun TaskRow(task: Task, showIcon: Boolean, onOpenTask: (Long) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onOpenTask(task.id) },
        leadingContent = if (showIcon) {
            {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(task.icon, fontSize = 22.sp) }
                }
            }
        } else {
            null
        },
        headlineContent = { Text(task.name) },
        supportingContent = { Text(scheduleSummary(task)) },
    )
}
