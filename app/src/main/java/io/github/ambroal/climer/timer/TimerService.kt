package io.github.ambroal.climer.timer

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1_000L

/** Tiene vivo il processo (foreground) e la CPU sveglia (wake lock) mentre il timer corre. */
@AndroidEntryPoint
class TimerService : LifecycleService() {
    @Inject
    lateinit var controller: TimerController

    private var wakeLock: PowerManager.WakeLock? = null
    private var observing = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        createTimerChannel(this)
        if (!startInForeground()) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_TOGGLE -> controller.togglePause()
            ACTION_STOP -> controller.stop()
        }
        if (!observing) {
            observing = true
            lifecycleScope.launch {
                // Riavvio dopo che il sistema ha chiuso il processo: START_STICKY ci riporta qui.
                controller.restoreIfAny()
                if (controller.state.value == null) shutdown()
                controller.state
                    .map { ui ->
                        ui?.let {
                            NotificationKey(
                                it.snapshot.phaseIndex,
                                (it.snapshot.phaseRemainingMs + 999) / 1_000,
                                it.snapshot.isRunning,
                                it.snapshot.isFinished
                            )
                        }
                    }
                    .distinctUntilChanged()
                    .collect { key -> onState(key) }
            }
        }
        return START_STICKY
    }

    private data class NotificationKey(val phase: Int, val second: Long, val running: Boolean, val finished: Boolean)

    private fun onState(key: NotificationKey?) {
        if (key == null || key.finished) {
            shutdown()
            return
        }
        if (key.running) acquireWakeLock() else releaseWakeLock()
        getSystemService(NotificationManager::class.java)
            .notify(TIMER_NOTIFICATION_ID, buildTimerNotification(this, controller.state.value))
    }

    /** False se Android rifiuta il foreground (riavvio dal background su Android 12+): il ripristino avverrà alla riapertura dell'app. */
    private fun startInForeground(): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        return runCatching {
            ServiceCompat.startForeground(this, TIMER_NOTIFICATION_ID, buildTimerNotification(this, controller.state.value), type)
        }.onFailure { stopSelf() }.isSuccess
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TimerApp:timer")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun shutdown() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE = "io.github.ambroal.climer.TOGGLE"
        const val ACTION_STOP = "io.github.ambroal.climer.STOP"
    }
}
