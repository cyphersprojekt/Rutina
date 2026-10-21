package com.ignacio.rutina.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ignacio.rutina.RutinaApp
import com.ignacio.rutina.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Las alarmas se pierden al reiniciar el teléfono o cambiar la hora: hay que volver a programarlas.
 * Con otra fecha, los widgets también tienen que mostrar los contadores del nuevo día.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repository = (context.applicationContext as RutinaApp).repository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.rescheduleAll()
                Widgets.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
