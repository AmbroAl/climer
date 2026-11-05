package io.github.ambroal.climer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    // Room può consegnare un'ultima emissione dal suo thread mentre si ripristina Main: si riprova invece di fallire.
    override fun finished(description: Description) {
        repeat(20) {
            if (runCatching { Dispatchers.resetMain() }.isSuccess) return
            Thread.sleep(10)
        }
        Dispatchers.resetMain()
    }
}
