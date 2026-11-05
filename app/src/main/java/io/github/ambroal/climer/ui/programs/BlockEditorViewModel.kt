package io.github.ambroal.climer.ui.programs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.ambroal.climer.data.db.entity.BlockEntity
import io.github.ambroal.climer.data.db.entity.MeasureTypeEntity
import io.github.ambroal.climer.data.db.entity.SavedTimerEntity
import io.github.ambroal.climer.data.repo.BlockLoads
import io.github.ambroal.climer.data.repo.MeasureRepository
import io.github.ambroal.climer.data.repo.ProgramRepository
import io.github.ambroal.climer.data.repo.TimerRepository
import io.github.ambroal.climer.data.repo.computeLoads
import io.github.ambroal.climer.data.settings.SettingsRepository
import io.github.ambroal.climer.domain.format.formatKg
import io.github.ambroal.climer.domain.format.parseDecimalOrNull
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.interval.NewIntervalConfig
import io.github.ambroal.climer.domain.load.LoadSpec
import io.github.ambroal.climer.domain.load.SideMode
import io.github.ambroal.climer.timer.totalDurationMs
import io.github.ambroal.climer.ui.timers.sanitized
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LoadKind { NONE, FIXED, FIXED_PER_SIDE, PERCENT }

data class BlockForm(
    val id: Long = 0,
    val sessionId: Long = 0,
    val position: Int = 0,
    val name: String = "",
    val exercise: String = "",
    val equipment: String = "",
    val notes: String = "",
    val sideMode: SideMode = SideMode.NONE,
    val loadKind: LoadKind = LoadKind.NONE,
    val fixedKg: String = "",
    val fixedRight: String = "",
    val fixedLeft: String = "",
    val percent: String = "",
    val measureTypeId: Long? = null,
    val config: IntervalConfig = NewIntervalConfig,
    val loaded: Boolean = false,
    val saved: Boolean = false
) {
    private fun valid(text: String) = parseDecimalOrNull(text) != null
    private fun blankOrValid(text: String) = text.isBlank() || valid(text)

    val fixedKgError get() = !blankOrValid(fixedKg)
    val fixedRightError get() = !blankOrValid(fixedRight)
    val fixedLeftError get() = !blankOrValid(fixedLeft)
    val percentError get() = !blankOrValid(percent) || (parseDecimalOrNull(percent) ?: 1.0) <= 0.0

    /** Null se i campi del carico non sono validi. */
    val loadSpec: LoadSpec?
        get() = when (loadKind) {
            LoadKind.NONE -> LoadSpec.None
            LoadKind.FIXED -> parseDecimalOrNull(fixedKg)?.let { LoadSpec.Fixed(it) }
            LoadKind.FIXED_PER_SIDE -> if ((fixedRight.isBlank() && fixedLeft.isBlank()) || fixedRightError || fixedLeftError) {
                null
            } else {
                LoadSpec.FixedPerSide(parseDecimalOrNull(fixedRight), parseDecimalOrNull(fixedLeft))
            }
            LoadKind.PERCENT -> {
                val pct = parseDecimalOrNull(percent)
                val type = measureTypeId
                if (pct == null || pct <= 0.0 || type == null) null else LoadSpec.PercentOfMax(type, pct)
            }
        }
}

data class BlockEditorUiState(
    val form: BlockForm = BlockForm(),
    val measureTypes: List<MeasureTypeEntity> = emptyList(),
    val savedTimers: List<SavedTimerEntity> = emptyList(),
    val preview: BlockLoads? = null
) {
    val loaded get() = form.loaded
    val saved get() = form.saved
    val canSave get() = form.name.isNotBlank() && form.loadSpec != null
    val totalMs get() = form.config.totalDurationMs()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BlockEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val programs: ProgramRepository,
    measures: MeasureRepository,
    timers: TimerRepository,
    settings: SettingsRepository
) : ViewModel() {
    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L
    private val blockId: Long = savedStateHandle.get<Long>("blockId") ?: 0L
    private val form = MutableStateFlow(BlockForm(sessionId = sessionId, loaded = blockId == 0L))

    private val preview = form
        .map { it.sideMode to it.loadSpec }
        .distinctUntilChanged()
        .flatMapLatest { (side, spec) ->
            when (spec) {
                null -> flowOf(null)
                is LoadSpec.PercentOfMax -> combine(measures.latestMax(spec.measureTypeId), settings.settings) { max, s ->
                    computeLoads(side, spec, max, s.roundingStepKg)
                }
                else -> settings.settings.map { s -> computeLoads(side, spec, null, s.roundingStepKg) }
            }
        }

    val state: StateFlow<BlockEditorUiState> = combine(form, measures.observeTypes(), timers.observeAll(), preview) { f, types, t, p ->
        BlockEditorUiState(f, types, t, p)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BlockEditorUiState(form.value))

    init {
        if (blockId != 0L) {
            viewModelScope.launch {
                val block = programs.getBlock(blockId)
                form.value = block?.toForm() ?: form.value.copy(loaded = true)
            }
        }
    }

    fun onName(v: String) = form.update { it.copy(name = v) }

    fun onExercise(v: String) = form.update { it.copy(exercise = v) }

    fun onEquipment(v: String) = form.update { it.copy(equipment = v) }

    fun onNotes(v: String) = form.update { it.copy(notes = v) }

    fun onSideMode(v: SideMode) = form.update { it.copy(sideMode = v) }

    fun onLoadKind(v: LoadKind) = form.update { it.copy(loadKind = v) }

    fun onFixedKg(v: String) = form.update { it.copy(fixedKg = v) }

    fun onFixedRight(v: String) = form.update { it.copy(fixedRight = v) }

    fun onFixedLeft(v: String) = form.update { it.copy(fixedLeft = v) }

    fun onPercent(v: String) = form.update { it.copy(percent = v) }

    fun onMeasureType(id: Long) = form.update { it.copy(measureTypeId = id) }

    fun onConfig(config: IntervalConfig) = form.update { it.copy(config = config.sanitized()) }

    fun copyFromTimer(timer: SavedTimerEntity) = onConfig(timer.config)

    fun save() {
        val f = form.value
        val spec = f.loadSpec ?: return
        if (f.name.isBlank()) return
        viewModelScope.launch {
            programs.saveBlock(
                BlockEntity(
                    id = f.id,
                    sessionId = sessionId,
                    position = f.position,
                    name = f.name.trim(),
                    exercise = f.exercise.trim(),
                    equipment = f.equipment.trim(),
                    notes = f.notes.trim(),
                    sideMode = f.sideMode,
                    load = spec,
                    config = f.config
                )
            )
            form.update { it.copy(saved = true) }
        }
    }
}

private fun Double?.text() = this?.let(::formatKg).orEmpty()

private fun BlockEntity.toForm(): BlockForm {
    val base = BlockForm(
        id = id,
        sessionId = sessionId,
        position = position,
        name = name,
        exercise = exercise,
        equipment = equipment,
        notes = notes,
        sideMode = sideMode,
        config = config,
        loaded = true
    )
    return when (val l = load) {
        LoadSpec.None -> base
        is LoadSpec.Fixed -> base.copy(loadKind = LoadKind.FIXED, fixedKg = l.kg.text())
        is LoadSpec.FixedPerSide -> base.copy(
            loadKind = LoadKind.FIXED_PER_SIDE,
            fixedRight = l.rightKg.text(),
            fixedLeft = l.leftKg.text()
        )
        is LoadSpec.PercentOfMax -> base.copy(loadKind = LoadKind.PERCENT, percent = l.percent.text(), measureTypeId = l.measureTypeId)
    }
}
