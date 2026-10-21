package com.ignacio.rutina.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.domain.nextSlot
import com.ignacio.rutina.domain.toEpochMillis
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Mantiene una alarma por tarea, apuntando a su próxima ocurrencia. Cuando suena,
 * [ReminderReceiver] muestra el aviso y vuelve a llamar a [schedule] para la siguiente.
 */
class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun schedule(task: Task, after: LocalDateTime = LocalDateTime.now()) {
        val zone = ZoneId.systemDefault()
        val next = task.nextSlot(after, zone)
        if (next == null) {
            cancel(task.id)
            return
        }
        val intent = ReminderReceiver.intent(context, ReminderReceiver.ACTION_ALARM, task.id, next.toLocalDate(), next.toLocalTime())
        val pending = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = next.toEpochMillis(zone)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(taskId: Long) {
        val intent = ReminderReceiver.baseIntent(context, ReminderReceiver.ACTION_ALARM)
        PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let(alarms::cancel)
    }
}
