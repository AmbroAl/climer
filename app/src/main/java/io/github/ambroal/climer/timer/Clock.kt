package io.github.ambroal.climer.timer

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

fun interface Clock {
    fun elapsedRealtimeMs(): Long

    /** elapsedRealtime riparte da zero a ogni avvio del telefono: il conteggio dei boot dice se un timestamp salvato è ancora valido. */
    fun bootCount(): Int = 0
}

/** Usa elapsedRealtime: continua a contare anche in deep sleep e non risente dei cambi d'ora. */
class SystemElapsedClock(private val context: Context) : Clock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
}
