package io.github.ambroal.climer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.settings.AppSettings
import io.github.ambroal.climer.data.settings.CueTone
import io.github.ambroal.climer.data.settings.DefaultPhaseColors
import io.github.ambroal.climer.data.settings.SettingsRepository
import io.github.ambroal.climer.timer.PhaseType
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val RoundingSteps = listOf(0.5, 1.0, 1.25, 2.5)

fun interface TonePreview {
    fun preview(tone: CueTone)
}

@HiltViewModel
class SettingsViewModel @Inject constructor(private val settings: SettingsRepository, private val tonePreview: TonePreview) : ViewModel() {
    val state: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    fun setRoundingStep(step: Double) {
        if (step <= 0.0) return
        update { it.copy(roundingStepKg = step) }
    }

    fun setPhaseColor(type: PhaseType, argb: Long) = update { it.copy(phaseColors = it.phaseColors + (type to argb)) }

    fun resetPhaseColors() = update { it.copy(phaseColors = DefaultPhaseColors) }

    fun setWorkTone(tone: CueTone) {
        update { it.copy(workTone = tone) }
        tonePreview.preview(tone)
    }

    fun setRestTone(tone: CueTone) {
        update { it.copy(restTone = tone) }
        tonePreview.preview(tone)
    }
}
