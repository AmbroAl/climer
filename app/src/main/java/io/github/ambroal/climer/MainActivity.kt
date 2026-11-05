package io.github.ambroal.climer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.ambroal.climer.timer.EXTRA_OPEN_RUNNING
import io.github.ambroal.climer.ui.nav.AppNavHost
import io.github.ambroal.climer.ui.theme.TimerAppTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Conflated: la richiesta della notifica resta in attesa finché la UI non è pronta a riceverla.
    private val openRunning = Channel<Unit>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tema sempre scuro: icone di sistema chiare anche se il telefono è in modalità chiara.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        handle(intent)
        setContent {
            TimerAppTheme {
                AppNavHost(openRunningRequests = openRunning.receiveAsFlow())
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_RUNNING, false) == true) openRunning.trySend(Unit)
    }
}
