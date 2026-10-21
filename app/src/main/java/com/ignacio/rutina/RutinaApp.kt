package com.ignacio.rutina

import android.app.Application
import com.ignacio.rutina.data.Repository
import com.ignacio.rutina.data.RutinaDatabase
import com.ignacio.rutina.reminders.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RutinaApp : Application() {
    val repository by lazy { Repository(this, RutinaDatabase.build(this)) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        // Por si alguna alarma se perdió (por ejemplo, si el sistema cerró la app a la fuerza).
        scope.launch { repository.rescheduleAll() }
    }
}
