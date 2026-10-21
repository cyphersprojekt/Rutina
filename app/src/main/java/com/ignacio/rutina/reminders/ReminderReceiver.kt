package com.ignacio.rutina.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ignacio.rutina.RutinaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Recibe las alarmas de los recordatorios y el botón "Hecha" de la notificación. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK, 0)
        val date = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_DAY, 0))
        val time = LocalTime.ofSecondOfDay(intent.getIntExtra(EXTRA_MINUTE, 0) * 60L)
        val repository = (context.applicationContext as RutinaApp).repository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_ALARM -> repository.onAlarm(taskId, date, time)
                    ACTION_DONE -> repository.markDone(taskId, date, time)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "com.ignacio.rutina.ALARM"
        const val ACTION_DONE = "com.ignacio.rutina.DONE"
        private const val EXTRA_TASK = "task"
        private const val EXTRA_DAY = "day"
        private const val EXTRA_MINUTE = "minute"

        fun baseIntent(context: Context, action: String) =
            Intent(context, ReminderReceiver::class.java).setAction(action)

        fun intent(context: Context, action: String, taskId: Long, date: LocalDate, time: LocalTime): Intent =
            baseIntent(context, action)
                .putExtra(EXTRA_TASK, taskId)
                .putExtra(EXTRA_DAY, date.toEpochDay())
                .putExtra(EXTRA_MINUTE, time.hour * 60 + time.minute)
    }
}
