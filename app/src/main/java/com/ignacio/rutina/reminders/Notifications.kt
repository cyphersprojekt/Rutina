package com.ignacio.rutina.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ignacio.rutina.MainActivity
import com.ignacio.rutina.R
import com.ignacio.rutina.data.Task
import com.ignacio.rutina.domain.toEpochMillis
import com.ignacio.rutina.ui.hhmm
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Objects

object Notifications {
    private const val CHANNEL_ID = "recordatorios"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Recordatorios", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aviso a la hora de cada tarea"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Un id por ocurrencia, para que los avisos de distintos horarios no se pisen. */
    private fun id(taskId: Long, date: LocalDate, time: LocalTime): Int =
        Objects.hash(taskId, date.toEpochDay(), time.toSecondOfDay())

    fun show(context: Context, task: Task, date: LocalDate, time: LocalTime) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val id = id(task.id, date, time)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context,
            id,
            ReminderReceiver.intent(context, ReminderReceiver.ACTION_DONE, task.id, date, time),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_check)
            .setContentTitle(task.name)
            .setContentText("Programada para las ${time.hhmm()}")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(date.atTime(time).toEpochMillis(ZoneId.systemDefault()))
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_stat_check, "Hecha", done)
            .apply { if (task.icon.isNotEmpty()) setLargeIcon(emojiBitmap(context, task.icon)) }
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /**
     * El ícono chico de la barra de estado tiene que ser de un solo color, así que el emoji
     * va como imagen grande de la notificación.
     */
    private fun emojiBitmap(context: Context, emoji: String): Bitmap {
        val size = context.resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * 0.75f
            textAlign = Paint.Align.CENTER
        }
        val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(emoji, size / 2f, baseline, paint)
        return bitmap
    }

    fun cancel(context: Context, taskId: Long, date: LocalDate, time: LocalTime) {
        NotificationManagerCompat.from(context).cancel(id(taskId, date, time))
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
