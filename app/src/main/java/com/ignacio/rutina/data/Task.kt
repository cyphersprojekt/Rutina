package com.ignacio.rutina.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class TaskKind {
    /** Tiene horarios: avisa con una notificación y se marca como hecha. */
    REMINDER,

    /** No tiene horario: se le suman cantidades cuando uno quiere (ej. flexiones). */
    COUNTER,
}

/** Hacia dónde conviene que vaya un contador. */
enum class CounterGoal {
    /** Cuanto más, mejor (ej. flexiones). */
    MORE,

    /** Cuanto menos, mejor (ej. cigarrillos). */
    LESS,
}

/** Todos los días de la semana (bit 0 = lunes … bit 6 = domingo). */
const val ALL_DAYS = 0b1111111

fun DayOfWeek.bit(): Int = 1 shl (value - 1)

/** Agrupa tareas (Trabajo, Vida cotidiana, Ejercicio…) para ver el avance de cada área. */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Emoji que identifica la tarea en la app y en la notificación ("" = sin ícono). */
    @ColumnInfo(defaultValue = "")
    val icon: String = "",
    /**
     * Sin clave foránea a propósito: agregarla obligaría a recrear la tabla en la migración, y los
     * registros se borran en cascada con la tarea. Al borrar una categoría se limpia a mano.
     */
    @ColumnInfo(index = true)
    val categoryId: Long? = null,
    val kind: TaskKind,
    /** Recordatorio de una sola vez: el día en que toca. Null = se repite según [daysMask]. */
    val onceDate: LocalDate? = null,
    /** Recordatorio que se repite: días de la semana en que toca. */
    val daysMask: Int = ALL_DAYS,
    /** Recordatorio: horarios del día en que toca (uno o varios). */
    val times: List<LocalTime> = emptyList(),
    /** Contador: unidad que se muestra junto al número ("flexiones"). */
    val unit: String = "",
    /** Contador: cuánto suma el botón rápido. */
    val step: Int = 1,
    /** Contador: si conviene hacer cada vez más o cada vez menos. */
    @ColumnInfo(defaultValue = "MORE")
    val goal: CounterGoal = CounterGoal.MORE,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Un registro de "lo hice".
 *
 * En los recordatorios identifica la ocurrencia completada ([slotDate] + [slotTime]) y guarda
 * cuándo se marcó realmente ([doneAt]), lo que permite medir la puntualidad.
 * En los contadores los slots quedan en null y [amount] es la cantidad sumada.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = Task::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("taskId"),
        Index("doneAt"),
        Index(value = ["taskId", "slotDate", "slotTime"], unique = true),
    ],
)
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val slotDate: LocalDate? = null,
    val slotTime: LocalTime? = null,
    /** Momento en que se marcó, en milisegundos epoch. */
    val doneAt: Long,
    val amount: Int = 1,
)
