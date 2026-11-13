package io.github.ambroal.climer.timer

import io.github.ambroal.climer.di.ApplicationScope
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.BlockRunContext
import io.github.ambroal.climer.domain.load.SideLoad
import io.github.ambroal.climer.domain.load.loadForCycle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RunningUi(
    val title: String,
    val phases: List<Phase>,
    val snapshot: TimerSnapshot,
    val current: SideLoad?,
    val next: SideLoad?,
    val context: BlockRunContext?,
    val savedTimerId: Long?,
    val workoutId: Long?,
    val blockNumber: Int = 0,
    val blockCount: Int = 0,
    val nextBlockName: String? = null,
    val config: IntervalConfig = IntervalConfig(),
    val blockId: Long? = null,
    val blockLogIds: List<Long> = emptyList(),
    val mode: RunMode = RunMode.TIMER,
    val elapsedMs: Long = 0,
    val laps: List<Long> = emptyList(),
    /** Cronometro: avvisi già scattati (serve al cambio colore). */
    val alertCount: Int = 0
)

fun interface ServiceLauncher {
    fun ensureRunning()
}

fun interface BlockCompletionSink {
    suspend fun markCompleted(blockLogIds: List<Long>)
}

/** Prepara il blocco [blockId] nella seduta registrata [workoutId]; null se non esiste più. */
fun interface NextBlockStarter {
    suspend fun startBlock(workoutId: Long, blockId: Long): ActiveRun?
}

fun interface CueSink {
    fun play(cues: List<Cue>, ui: RunningUi)

    /** Il timer ha iniziato o smesso di scorrere: serve a tenere pronta l'uscita audio solo quando serve. */
    fun onRunningChanged(running: Boolean) = Unit
}

/**
 * Unica fonte di verità del timer in esecuzione. Il loop legge solo snapshot calcolati da timestamp,
 * quindi un ritardo del loop (Doze, CPU lenta) non fa derivare il tempo.
 */
@Singleton
class TimerController @Inject constructor(
    private val clock: Clock,
    private val store: ActiveRunStore,
    private val launcher: ServiceLauncher,
    private val completion: BlockCompletionSink,
    private val cues: CueSink,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val nextBlock: NextBlockStarter = NextBlockStarter { _, _ -> null }
) {
    private val _state = MutableStateFlow<RunningUi?>(null)
    val state: StateFlow<RunningUi?> = _state.asStateFlow()

    private var run: ActiveRun? = null
    private var engine: TimerEngine? = null
    private var phases: List<Phase> = emptyList()
    private var previous: TimerSnapshot? = null
    private var loop: Job? = null
    private var finishHandled = false

    // Basta essere partiti una volta: una pausa a 0 ms non rende "mai avviato" un timer.
    private var everStarted = false

    @Synchronized
    fun start(newRun: ActiveRun, autoStart: Boolean = true) {
        attach(newRun.copy(bootCount = clock.bootCount()))
        if (autoStart) {
            engine?.start()
            everStarted = true
        }
        previous = null
        afterChange()
    }

    @Synchronized
    fun togglePause() {
        val e = engine ?: return
        if (e.runState.runningSinceMs != null) {
            e.pause()
        } else {
            e.start()
            everStarted = true
        }
        afterChange()
    }

    @Synchronized
    fun skipTo(index: Int) {
        engine?.skipTo(index) ?: return
        afterChange()
    }

    /** Riporta il blocco a "Prepara" e lo fa ripartire, nella stessa seduta registrata. */
    @Synchronized
    fun restart() {
        val e = engine ?: return
        e.skipTo(0)
        e.start()
        everStarted = true
        finishHandled = false
        previous = null
        afterChange()
    }

    /** Applica una nuova configurazione al timer in corso, ripartendo dallo stesso tempo trascorso (limitato alla nuova durata). */
    @Synchronized
    fun updateConfig(config: IntervalConfig) {
        val e = engine ?: return
        val current = run ?: return
        val snap = e.snapshot()
        val elapsed = (phases.sumOf { it.durationMs } - snap.totalRemainingMs).coerceAtMost(config.totalDurationMs())
        val running = e.runState.runningSinceMs != null
        val wasStarted = everStarted
        attach(current.copy(config = config, runState = TimerRunState(elapsed, if (running) clock.elapsedRealtimeMs() else null)))
        everStarted = wasStarted
        // Nessun segnale per il solo fatto di aver modificato: si riparte silenziosi fino al prossimo evento.
        previous = engine?.snapshot()
        afterChange()
    }

    /** Cronometro: segna un giro al tempo attuale. */
    @Synchronized
    fun lap() {
        val e = engine ?: return
        val current = run ?: return
        run = current.copy(laps = current.laps + elapsedOf(e.snapshot()))
        afterChange()
    }

    /** Cronometro: cambia l'intervallo degli avvisi senza fermarlo. */
    @Synchronized
    fun setAlertEvery(seconds: Int) {
        val current = run ?: return
        run = current.copy(alertEverySec = seconds.coerceAtLeast(0))
        previous = engine?.snapshot()
        afterChange()
    }

    /** False finché il timer aperto non è mai stato avviato con play (o partito da solo). */
    @Synchronized
    fun hasStarted(): Boolean {
        val e = engine ?: return false
        return everStarted || e.runState.runningSinceMs != null || e.runState.elapsedBeforeMs > 0
    }

    @Synchronized
    fun stop() {
        loop?.cancel()
        cues.onRunningChanged(false)
        loop = null
        engine = null
        run = null
        previous = null
        _state.value = null
        scope.launch { store.save(null) }
    }

    suspend fun restoreIfAny() {
        if (engine != null) return
        val saved = store.load() ?: return
        synchronized(this) {
            if (engine != null) return
            val sameBoot = saved.bootCount == clock.bootCount()
            attach(
                if (sameBoot) saved else saved.copy(runState = TimerRunState(saved.runState.elapsedBeforeMs), bootCount = clock.bootCount())
            )
            // Niente annuncio della fase in corso al ripristino: riparte silenzioso fino al prossimo evento.
            previous = engine?.snapshot()
            afterChange()
        }
    }

    @Synchronized
    fun tick() {
        val e = engine ?: return
        val current = run ?: return
        val snap = e.snapshot()
        // In una seduta a fine blocco parte il successivo: il suo annuncio sostituisce "Fine".
        val emitted = if (current.mode == RunMode.STOPWATCH) {
            stopwatchCues(previous?.let(::elapsedOf), elapsedOf(snap), current.alertEverySec * 1_000L)
        } else {
            cuesBetween(previous, snap).filterNot { it == Cue.Finished && current.queue.isNotEmpty() }
        }
        previous = snap
        val ui = buildUi(current, snap)
        _state.value = ui
        if (emitted.isNotEmpty()) cues.play(emitted, ui)
        if (snap.isFinished && !finishHandled) {
            finishHandled = true
            loop?.cancel()
            loop = null
            cues.onRunningChanged(false)
            scope.launch {
                completion.markCompleted(current.blockLogIds)
                if (!startNextBlock(current)) store.save(null)
            }
        }
    }

    private suspend fun startNextBlock(finished: ActiveRun): Boolean {
        val workoutId = finished.workoutId ?: return false
        finished.queue.forEachIndexed { i, queued ->
            val next = nextBlock.startBlock(workoutId, queued.blockId) ?: return@forEachIndexed
            start(
                next.copy(
                    queue = finished.queue.drop(i + 1),
                    blockNumber = finished.blockNumber + i + 1,
                    blockCount = finished.blockCount
                )
            )
            return true
        }
        return false
    }

    private fun attach(newRun: ActiveRun) {
        everStarted = false
        loop?.cancel()
        phases = if (newRun.mode == RunMode.STOPWATCH) stopwatchPhases() else buildPhases(newRun.config)
        engine = TimerEngine(phases, clock, newRun.runState)
        run = newRun
        finishHandled = false
    }

    private fun afterChange() {
        val e = engine ?: return
        run = run?.copy(runState = e.runState)
        val toSave = run
        scope.launch { store.save(toSave) }
        tick()
        val running = e.runState.runningSinceMs != null && !e.snapshot().isFinished
        if (running) {
            // Android 12+ può rifiutare il foreground service (es. riavvio dal background): il timer continua, i segnali tornano alla riapertura.
            runCatching { launcher.ensureRunning() }
            cues.onRunningChanged(true)
            // Ripartire sempre: dopo un salto o una ripresa il prossimo risveglio va ricalcolato.
            loop?.cancel()
            loop = scope.launch {
                while (isActive) {
                    delay(delayToNextChangeMs() ?: break)
                    tick()
                }
            }
        } else {
            loop?.cancel()
            loop = null
            cues.onRunningChanged(false)
        }
    }

    /** Attesa fino al prossimo cambiamento rispetto all'ultimo stato pubblicato; null se non c'è più nulla da aspettare. */
    @Synchronized
    private fun delayToNextChangeMs(): Long? {
        val e = engine ?: return null
        val shown = previous ?: return null
        val wait = if (run?.mode == RunMode.STOPWATCH) {
            if (shown.isRunning) nextStopwatchDelayMs(elapsedOf(shown)) else return null
        } else {
            nextTickDelayMs(shown) ?: return null
        }
        return remainingWaitMs(wait, shown, e.snapshot())
    }

    private fun buildUi(current: ActiveRun, snap: TimerSnapshot): RunningUi {
        val ctx = current.context
        return RunningUi(
            title = current.title,
            phases = phases,
            snapshot = snap,
            current = loadFor(snap.phase, ctx),
            next = loadFor(phases.getOrNull(snap.phaseIndex + 1), ctx),
            context = ctx,
            savedTimerId = current.savedTimerId,
            workoutId = current.workoutId,
            blockNumber = current.blockNumber,
            blockCount = current.blockCount,
            nextBlockName = current.queue.firstOrNull()?.name,
            config = current.config,
            blockId = current.blockId,
            blockLogIds = current.blockLogIds,
            mode = current.mode,
            elapsedMs = elapsedOf(snap),
            laps = current.laps,
            alertCount = if (current.alertEverySec > 0) (elapsedOf(snap) / (current.alertEverySec * 1_000L)).toInt() else 0
        )
    }

    private fun elapsedOf(snap: TimerSnapshot): Long = phases.sumOf { it.durationMs } - snap.totalRemainingMs

    private fun loadFor(phase: Phase?, ctx: BlockRunContext?): SideLoad? {
        if (ctx == null || phase == null) return null
        return when (phase.type) {
            PhaseType.COOLDOWN -> null
            PhaseType.PREPARE -> ctx.loadForCycle(1)
            else -> ctx.loadForCycle(phase.cycle)
        }
    }
}
