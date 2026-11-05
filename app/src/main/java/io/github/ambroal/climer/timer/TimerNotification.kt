package io.github.ambroal.climer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.github.ambroal.climer.MainActivity
import io.github.ambroal.climer.R
import io.github.ambroal.climer.domain.format.formatClock
import io.github.ambroal.climer.domain.format.formatStopwatch

const val TIMER_CHANNEL_ID = "timer"
const val TIMER_NOTIFICATION_ID = 1
const val EXTRA_OPEN_RUNNING = "io.github.ambroal.climer.OPEN_RUNNING"

fun createTimerChannel(context: Context) {
    val channel =
        NotificationChannel(TIMER_CHANNEL_ID, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
    channel.setShowBadge(false)
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

fun buildTimerNotification(context: Context, ui: RunningUi?): Notification {
    val open = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_OPEN_RUNNING, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    val builder = NotificationCompat.Builder(context, TIMER_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        .setContentIntent(open)
    if (ui == null) return builder.setContentTitle(context.getString(R.string.app_name)).build()

    val phase = ui.snapshot.phase
    val title = if (phase == null) context.getString(R.string.speech_finished) else context.getString(phaseNameRes(phase.type))
    val running = ui.snapshot.isRunning
    return builder
        .setContentTitle(
            if (ui.mode == RunMode.STOPWATCH) {
                "${context.getString(R.string.stopwatch)} · ${formatStopwatch(ui.elapsedMs).substringBefore('.')}"
            } else {
                "$title · ${formatClock(ui.snapshot.phaseRemainingMs)}"
            }
        )
        .setContentText(
            if (ui.mode == RunMode.STOPWATCH) {
                context.resources.getQuantityString(R.plurals.laps_count, ui.laps.size, ui.laps.size)
            } else {
                "${ui.title} · ${formatClock(ui.snapshot.totalRemainingMs)}"
            }
        )
        .addAction(
            0,
            context.getString(if (running) R.string.action_pause else R.string.action_resume),
            serviceIntent(context, TimerService.ACTION_TOGGLE, 1)
        )
        .addAction(0, context.getString(R.string.action_stop), serviceIntent(context, TimerService.ACTION_STOP, 2))
        .build()
}

private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
    context,
    requestCode,
    Intent(context, TimerService::class.java).setAction(action),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)
