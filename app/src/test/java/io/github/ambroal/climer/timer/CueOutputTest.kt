package io.github.ambroal.climer.timer

import io.github.ambroal.climer.data.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class CueOutputTest {
    @Test
    fun `i suoni non dipendono dalla suoneria`() =
        assertEquals(CueOutput(sound = true, voice = true, vibrate = false), cueOutputFor(AppSettings()))

    @Test
    fun `voce e vibrazione seguono le impostazioni`() =
        assertEquals(CueOutput(sound = true, voice = false, vibrate = true), cueOutputFor(AppSettings(voice = false, vibration = true)))
}
