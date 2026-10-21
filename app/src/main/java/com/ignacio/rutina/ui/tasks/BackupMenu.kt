package com.ignacio.rutina.ui.tasks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ignacio.rutina.data.BackupCheck
import com.ignacio.rutina.ui.components.ConfirmDialog
import com.ignacio.rutina.ui.components.repository
import kotlinx.coroutines.launch
import java.time.LocalDate

/** El menú ⋮ de Tareas: exportar e importar todos los datos. Cómo salió se avisa en [snackbar]. */
@Composable
fun BackupMenu(snackbar: SnackbarHostState) {
    val repo = repository()
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var toRestore by remember { mutableStateOf<BackupCheck.Valid?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                snackbar.showSnackbar(if (repo.exportBackup(uri)) "Copia guardada" else "No se pudo guardar la copia")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val check = repo.checkBackup(uri)) {
                    is BackupCheck.Valid -> toRestore = check
                    is BackupCheck.Invalid -> snackbar.showSnackbar(check.reason)
                }
            }
        }
    }

    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Exportar datos") },
                onClick = {
                    expanded = false
                    exportLauncher.launch("rutina-${LocalDate.now()}.db")
                },
            )
            DropdownMenuItem(
                text = { Text("Importar datos") },
                onClick = {
                    expanded = false
                    // Cada app de archivos le pone un tipo distinto a un .db: se muestran todos.
                    importLauncher.launch(arrayOf("*/*"))
                },
            )
        }
    }

    toRestore?.let { check ->
        ConfirmDialog(
            title = "¿Reemplazar tus datos?",
            text = "La copia tiene ${count(check.tasks, "tarea", "tareas")} y ${count(check.entries, "registro", "registros")}. " +
                "Todo lo que hay ahora en la app se borra y queda lo de la copia.",
            confirmLabel = "Reemplazar",
            onDismiss = {
                toRestore = null
                scope.launch { repo.discardBackup() }
            },
            onConfirm = {
                toRestore = null
                scope.launch {
                    snackbar.showSnackbar(if (repo.restoreBackup()) "Datos importados" else "No se pudo importar la copia")
                }
            },
        )
    }
}

private fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
