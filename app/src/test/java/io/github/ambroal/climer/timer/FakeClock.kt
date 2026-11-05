package io.github.ambroal.climer.timer

class FakeClock(var now: Long = 0, var boot: Int = 1) : Clock {
    override fun elapsedRealtimeMs(): Long = now

    override fun bootCount(): Int = boot
}
