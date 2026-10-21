@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.ignacio.rutina.ui.components

import android.icu.text.BreakIterator
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Sugerencias pensadas para tareas de la casa y hábitos; con el teclado se puede usar cualquier otro. */
private val suggestions = listOf(
    "🍽️", "🧽", "🧹", "🧺", "👕", "🛏️", "🍳", "🛒",
    "🗑️", "♻️", "🪴", "🐶", "🐱", "💊", "🦷", "🚿",
    "💧", "💪", "🏃", "🚶", "🧘", "😴", "⏰", "☕",
    "🥗", "🍎", "📚", "✍️", "🧠", "💻", "📧", "📞",
    "💰", "🚗", "🎸", "🔧", "🧴", "📝", "❤️", "⭐",
)

/** Muestra el ícono de una tarea, o una carita gris para elegir uno si todavía no tiene. */
@Composable
fun EmojiButton(emoji: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .size(56.dp)
            .semantics { contentDescription = if (emoji.isEmpty()) "Elegir ícono" else "Ícono $emoji. Cambiar" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (emoji.isEmpty()) {
                Icon(Icons.Default.Face, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(emoji, fontSize = 28.sp)
            }
        }
    }
}

/** [onPick] recibe el emoji elegido, o "" para quitarlo. */
@Composable
fun EmojiPickerSheet(selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    // Abre entera para que el campo "Otro" no quede cortado abajo.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Ícono", style = MaterialTheme.typography.titleLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                suggestions.forEach { emoji ->
                    Surface(
                        onClick = { onPick(emoji) },
                        shape = CircleShape,
                        color = if (emoji == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) }
                    }
                }
            }
            OutlinedTextField(
                value = custom,
                onValueChange = { value ->
                    custom = value
                    lastGrapheme(value)?.let(onPick)
                },
                label = { Text("Otro") },
                placeholder = { Text("Elegilo del teclado de emoji") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (selected.isNotEmpty()) {
                TextButton(onClick = { onPick("") }) { Text("Quitar el ícono") }
            }
        }
    }
}

/** Último carácter visible del texto: un emoji compuesto (👨‍🍳, 🏃‍♀️) cuenta como uno solo. */
private fun lastGrapheme(text: String): String? {
    if (text.isBlank()) return null
    val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
    val end = iterator.last()
    val start = iterator.previous()
    return text.substring(start, end).takeIf { it.isNotBlank() }
}
