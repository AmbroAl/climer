package io.github.ambroal.climer.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.repo.TimerRepository
import io.github.ambroal.climer.data.settings.SettingsRepository
import io.github.ambroal.climer.timer.RunMode
import io.github.ambroal.climer.timer.TimerController
import io.github.ambroal.climer.ui.timers.startSavedTimer
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    private val controller: TimerController,
    private val settings: SettingsRepository,
    private val timers: TimerRepository
) : ViewModel() {
    private var launched = false

    /** Serve a mostrare "Timer in corso" ovunque, così uscire dalla schermata del timer non fa perdere mano e peso. */
    val timerActive: StateFlow<Boolean> = controller.state
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, controller.state.value != null)

    /** Timer o cronometro in corso: decide dove porta la barra "in corso" e la notifica. */
    val activeMode: StateFlow<RunMode?> = controller.state
        .map { it?.mode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, controller.state.value?.mode)

    /** All'avvio: riprende un timer interrotto, oppure apre l'ultimo timer se richiesto. True se c'è un timer da mostrare. */
    suspend fun onAppStart(): Boolean {
        if (launched) return false
        launched = true
        controller.restoreIfAny()
        if (controller.state.value != null) return true
        val s = settings.settings.first()
        val last = s.lastTimerId?.takeIf { s.openLastTimer }?.let { timers.get(it) } ?: return false
        startSavedTimer(controller, last, autoStart = false)
        return true
    }
}
