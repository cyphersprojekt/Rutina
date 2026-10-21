@file:OptIn(ExperimentalMaterial3Api::class)

package com.ignacio.rutina.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.ignacio.rutina.RutinaApp
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.data.TaskKind
import com.ignacio.rutina.ui.components.EmptyHint
import com.ignacio.rutina.ui.scheduleSummary
import com.ignacio.rutina.ui.theme.RutinaTheme
import com.ignacio.rutina.ui.title
import kotlinx.coroutines.launch

/**
 * Elige qué contador muestra un widget individual. Se abre al agregarlo, al reconfigurarlo
 * (mantenerlo apretado) o al tocarlo si su contador se borró.
 */
class SingleCounterConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Si se cierra sin elegir, el widget recién agregado se descarta.
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val repository = (application as RutinaApp).repository
        enableEdgeToEdge()
        setContent {
            RutinaTheme {
                val tasks by repository.tasks.collectAsStateWithLifecycle(null)
                ChooseCounterScreen(
                    counters = tasks?.filter { it.kind == TaskKind.COUNTER },
                    onPick = { task ->
                        lifecycleScope.launch {
                            SingleCounterWidget.assign(this@SingleCounterConfigActivity, appWidgetId, task.id)
                            setResult(RESULT_OK, result)
                            finish()
                        }
                    },
                    onClose = { finish() },
                )
            }
        }
    }
}

@Composable
private fun ChooseCounterScreen(counters: List<Task>?, onPick: (Task) -> Unit, onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("¿Qué contador?") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Cancelar") }
                },
            )
        },
    ) { padding ->
        when {
            counters == null -> Unit
            counters.isEmpty() -> EmptyHint(
                "Todavía no tenés contadores. Creá uno en la app y después agregá el widget.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
                items(counters, key = { it.id }) { task ->
                    ListItem(
                        modifier = Modifier.clickable { onPick(task) },
                        headlineContent = { Text(task.title) },
                        supportingContent = { Text(scheduleSummary(task)) },
                    )
                }
            }
        }
    }
}
