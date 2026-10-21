package com.ignacio.rutina.data

import android.content.Context
import android.net.Uri
import com.ignacio.rutina.domain.CounterItem
import com.ignacio.rutina.domain.buildCounters
import com.ignacio.rutina.domain.timesOn
import com.ignacio.rutina.reminders.Notifications
import com.ignacio.rutina.reminders.ReminderScheduler
import com.ignacio.rutina.widget.Widgets
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class Repository(private val context: Context, db: RutinaDatabase) {
    private val taskDao = db.taskDao()
    private val entryDao = db.entryDao()
    private val categoryDao = db.categoryDao()
    private val scheduler = ReminderScheduler(context)
    private val backup = Backup(context, db)

    val zone: ZoneId get() = ZoneId.systemDefault()

    val tasks: Flow<List<Task>> = taskDao.observeAll()

    val categories: Flow<List<Category>> = categoryDao.observeAll()

    suspend fun addCategory(name: String): Long = categoryDao.insert(Category(name = name.trim()))

    suspend fun renameCategory(category: Category, name: String) = categoryDao.update(category.copy(name = name.trim()))

    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

    fun task(id: Long): Flow<Task?> = taskDao.observe(id)

    suspend fun getTask(id: Long): Task? = taskDao.get(id)

    fun entriesFor(taskId: Long): Flow<List<Entry>> = entryDao.observeForTask(taskId)

    fun slotEntriesOn(date: LocalDate): Flow<List<Entry>> = entryDao.observeSlotsOn(date)

    fun countEntriesOn(date: LocalDate): Flow<List<Entry>> {
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return entryDao.observeCountsBetween(from, to)
    }

    /** Los contadores con lo sumado en el día. */
    fun countersOn(date: LocalDate): Flow<List<CounterItem>> =
        combine(tasks, countEntriesOn(date), ::buildCounters)

    suspend fun saveTask(task: Task): Long {
        val id = if (task.id == 0L) taskDao.insert(task) else task.id.also { taskDao.update(task) }
        scheduler.schedule(task.copy(id = id))
        Widgets.refresh(context)
        return id
    }

    suspend fun deleteTask(task: Task) {
        scheduler.cancel(task.id)
        taskDao.delete(task)
        Widgets.refresh(context)
    }

    suspend fun markDone(taskId: Long, date: LocalDate, time: LocalTime, doneAt: Long = System.currentTimeMillis()) {
        // La tarea pudo haberse borrado con la notificación todavía visible.
        if (taskDao.get(taskId) != null) {
            entryDao.markSlot(Entry(taskId = taskId, slotDate = date, slotTime = time, doneAt = doneAt))
        }
        Notifications.cancel(context, taskId, date, time)
    }

    suspend fun unmark(taskId: Long, date: LocalDate, time: LocalTime) = entryDao.deleteSlot(taskId, date, time)

    suspend fun addCount(taskId: Long, amount: Int, at: Long = System.currentTimeMillis()) {
        entryDao.insert(Entry(taskId = taskId, doneAt = at, amount = amount))
        Widgets.refresh(context)
    }

    suspend fun deleteEntry(entry: Entry) {
        entryDao.delete(entry)
        Widgets.refresh(context)
    }

    /** Llamado por la alarma: avisa si la ocurrencia sigue pendiente y programa la siguiente. */
    suspend fun onAlarm(taskId: Long, date: LocalDate, time: LocalTime) {
        val task = taskDao.get(taskId) ?: return
        if (time in task.timesOn(date, zone) && !entryDao.isSlotDone(taskId, date, time)) {
            Notifications.show(context, task, date, time)
        }
        scheduler.schedule(task, after = maxOf(date.atTime(time), LocalDateTime.now()))
    }

    suspend fun rescheduleAll() = taskDao.getAll().forEach { scheduler.schedule(it) }

    /** Guarda una copia de todos los datos en [uri]. Devuelve si se pudo. */
    suspend fun exportBackup(uri: Uri): Boolean = backup.export(uri)

    /** Revisa una copia antes de importarla con [restoreBackup]. */
    suspend fun checkBackup(uri: Uri): BackupCheck = backup.check(uri)

    suspend fun discardBackup() = backup.discard()

    /** Reemplaza todos los datos por los de la copia revisada con [checkBackup]. Devuelve si se pudo. */
    suspend fun restoreBackup(): Boolean = withContext(NonCancellable) {
        // Aunque se salga de la pantalla, la importación termina: si no, quedarían alarmas sin programar.
        val before = taskDao.getAll()
        val restored = backup.restore()
        if (restored) {
            // Las alarmas y los avisos de antes apuntan a tareas que ya no existen, o a otras con el mismo id.
            before.forEach { scheduler.cancel(it.id) }
            Notifications.cancelAll(context)
            rescheduleAll()
            Widgets.refresh(context)
        }
        restored
    }
}
