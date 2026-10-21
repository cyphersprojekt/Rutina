@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ignacio.rutina.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ignacio.rutina.data.Category
import kotlinx.coroutines.launch

/** Chips para elegir la categoría de una tarea; tocar la elegida la deja sin categoría. */
@Composable
fun CategoryPicker(categories: List<Category>, selected: Long?, onSelect: (Long?) -> Unit, onCreate: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { category ->
            FilterChip(
                selected = category.id == selected,
                onClick = { onSelect(if (category.id == selected) null else category.id) },
                label = { Text(category.name) },
            )
        }
        AssistChip(
            onClick = { creating = true },
            label = { Text("Nueva") },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
    if (creating) {
        NameDialog(
            title = "Nueva categoría",
            initial = "",
            confirmLabel = "Crear",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                onCreate(name)
            },
        )
    }
}

/** Crear, renombrar y borrar categorías. */
@Composable
fun CategoriesSheet(categories: List<Category>, onDismiss: () -> Unit) {
    val repo = repository()
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text("Categorías", style = MaterialTheme.typography.titleLarge) },
                supportingContent = { Text("Tocá una para cambiarle el nombre.") },
            )
            HorizontalDivider()
            categories.forEach { category ->
                ListItem(
                    modifier = Modifier.clickable { renaming = category },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(category.name) },
                    trailingContent = {
                        IconButton(onClick = { deleting = category }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar ${category.name}")
                        }
                    },
                )
            }
            ListItem(
                modifier = Modifier.clickable { creating = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                headlineContent = { Text("Nueva categoría") },
            )
        }
    }

    if (creating) {
        NameDialog(
            title = "Nueva categoría",
            initial = "",
            confirmLabel = "Crear",
            onDismiss = { creating = false },
            onConfirm = { name ->
                creating = false
                scope.launch { repo.addCategory(name) }
            },
        )
    }
    renaming?.let { category ->
        NameDialog(
            title = "Cambiar nombre",
            initial = category.name,
            confirmLabel = "Guardar",
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                scope.launch { repo.renameCategory(category, name) }
            },
        )
    }
    deleting?.let { category ->
        ConfirmDialog(
            title = "¿Borrar \"${category.name}\"?",
            text = "Sus tareas no se borran: quedan sin categoría.",
            confirmLabel = "Borrar",
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                scope.launch { repo.deleteCategory(category) }
            },
        )
    }
}
