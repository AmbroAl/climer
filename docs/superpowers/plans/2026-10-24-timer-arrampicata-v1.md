# Timer Arrampicata v1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** App Android personale che sostituisce Timer Plus (timer a intervalli) e aggiunge programmi/sedute/blocchi con mano e peso per blocco, massimali generici con storico e grafico, registrazione manuale dei risultati.

**Architecture:** App single-module. Logica pura (fasi, motore timer, segnali, calcolo carichi, parsing numeri) in `domain/` e `timer/` senza dipendenze Android, sviluppata in TDD. Room + DataStore in `data/`. Il timer gira in un Foreground Service guidato da un `TimerController` singleton che espone `StateFlow`; la UI Compose osserva i ViewModel (MVVM + UDF).

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, Navigation Compose (route type-safe), Hilt, Room (KSP), DataStore Preferences, kotlinx.serialization, Coroutines/Flow, Vico (grafici), JUnit4 + kotlinx-coroutines-test + Turbine + Robolectric (test DAO), ktlint Gradle plugin.

**Spec:** `docs/superpowers/specs/2026-10-24-timer-arrampicata-design.md`

## Global Constraints

- Package: `io.github.ambroal.climer`; nome app visibile: "Timer Arrampicata". Percorsi sotto `app/src/main/java/io/github/ambroal/climer/` (abbreviato `…/` sotto) e test sotto `app/src/test/java/io/github/ambroal/climer/`.
- Kotlin, Jetpack Compose, Material 3; MVVM + UDF: ogni ViewModel espone `StateFlow<XxxUiState>`; niente LiveData, niente RxJava.
- Room per i dati, DataStore per le preferenze, Hilt per DI.
- minSdk 26; targetSdk/compileSdk = ultimo stabile disponibile in `sdkmanager --list` al Task 1.
- Gradle Kotlin DSL + `gradle/libs.versions.toml`; versioni = ultime stabili al Task 1, compatibili tra loro (Kotlin ↔ KSP ↔ plugin Compose ↔ AGP).
- Timer: classe pura in `timer/`, tempo da `Clock` iniettabile basato su `SystemClock.elapsedRealtime()`; mai sommare tick.
- Timer in Foreground Service con notifica persistente, `foregroundServiceType="specialUse"`, wake lock parziale durante l'esecuzione; permesso `POST_NOTIFICATIONS` chiesto su Android 13+.
- Segnali: suono + vibrazione, rispettando la modalità silenziosa.
- Stringhe in `res/values/strings.xml`, solo italiano. Numeri mostrati in formato italiano ("17,5 kg").
- Niente `!!`. Composable piccoli e stateless, stato nei ViewModel. Commenti solo dove il perché non è ovvio.
- Commit Conventional Commits, uno per task (o per passo logico), con riga finale `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Prima di dichiarare un task finito: `./gradlew assembleDebug testDebugUnitTest lint ktlintCheck` verdi.
- Tutti i dati solo sul telefono. Tema scuro.

## Review Focus

1. **Schermo spento per 30+ minuti / Doze** — il timer deve essere esatto al risveglio e i segnali devono suonare: snapshot calcolato da timestamp anche dopo grandi salti di clock (test in Task 3) + wake lock e verifica su dispositivo (Task 8, Task 17).
2. **App uccisa dal sistema a metà blocco** — alla riapertura il timer riprende dal punto giusto con mano/peso giusti: `TimerRunState` + `ActiveRun` persistiti e ripristinati (test in Task 3 e Task 8).
3. **Numeri con la virgola** — "17,5", "17.5", " 17 " si accettano; "", "abc", "1,2,3", "-3" no; i kg si mostrano "17,5" e "47" (test in Task 5).
4. **Massimale mancante o tipo eliminato** — il peso diventa `null`, la UI mostra "—" e si può avviare comunque (test in Task 5 e Task 6).
5. **Configurazioni limite** — Prepara 0, Riposa 0, Round 1, Cicli 1, Rinfrescati 0, cicli dispari in "Alternato": nessuna fase da 0 ms, mano corretta per ciclo (test in Task 2 e Task 5).

---

### Task 1: Ambiente di sviluppo e scheletro del progetto

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `.gitignore`, `.editorconfig`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- Create: `…/TimerApp.kt` (`@HiltAndroidApp class TimerApp : Application()`), `…/MainActivity.kt` (`@AndroidEntryPoint`, `setContent { TimerAppTheme { Text(...) } }`), `…/ui/theme/Theme.kt`
- Create: `app/src/main/res/values/strings.xml` (`app_name` = "Timer Arrampicata"), icona launcher di default
- Test: `app/src/test/java/io/github/ambroal/climer/SmokeTest.kt`

**Interfaces:**
- Produces: progetto che compila; `TimerAppTheme(content: @Composable () -> Unit)`; catalogo con alias per tutte le librerie del Tech Stack (anche quelle usate più avanti, così i task successivi non toccano il catalogo).

- [ ] **Step 1: Installare Android Studio**
Run (PowerShell): `winget install --id Google.AndroidStudio -e --accept-package-agreements --accept-source-agreements`
Expected: `C:\Program Files\Android\Android Studio\jbr\bin\java.exe` esiste.
- [ ] **Step 2: Configurare JDK e SDK**
`setx JAVA_HOME "C:\Program Files\Android\Android Studio\jbr"`; scaricare i command-line tools Windows da developer.android.com/studio (sezione "Command line tools only") in `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest`; `setx ANDROID_HOME "%LOCALAPPDATA%\Android\Sdk"`; accettare le licenze (`sdkmanager --licenses`); installare `platform-tools`, `platforms;android-<N>` e `build-tools;<N>.x.x` con N = ultima API stabile in `sdkmanager --list`.
Expected: `sdkmanager --list_installed` mostra i tre pacchetti; `adb version` funziona.
- [ ] **Step 3: Scrivere i file Gradle e il version catalog**
Plugin: AGP application, kotlin-android, kotlin compose, kotlin serialization, KSP, Hilt, ktlint (`org.jlleitschuh.gradle.ktlint`). Librerie: compose BOM, material3, material-icons-extended, activity-compose, lifecycle-runtime-compose, lifecycle-viewmodel-compose, lifecycle-service, navigation-compose, hilt-android, hilt-compiler, hilt-navigation-compose, room-runtime, room-ktx, room-compiler, datastore-preferences, kotlinx-serialization-json, kotlinx-coroutines-android, vico compose-m3, junit, kotlinx-coroutines-test, turbine, robolectric, androidx-test-core, room-testing. `testOptions.unitTests.isIncludeAndroidResources = true`. `local.properties` con `sdk.dir`, ignorato da git.
- [ ] **Step 4: Generare il Gradle wrapper**
Scaricare la distribuzione Gradle richiesta dall'AGP scelto nella scratchpad, eseguire `gradle wrapper --gradle-version <ver>` nella root.
- [ ] **Step 5: Scrivere `SmokeTest` (`assertEquals(4, 2 + 2)`) e le classi minime**
- [ ] **Step 6: Verificare**
Run: `./gradlew assembleDebug testDebugUnitTest lint ktlintCheck`
Expected: `BUILD SUCCESSFUL`, APK in `app/build/outputs/apk/debug/`.
- [ ] **Step 7: Commit** — `chore: scheletro progetto Android con Compose, Hilt, Room`

---

### Task 2: Configurazione intervalli e sequenza delle fasi

**Files:**
- Create: `…/domain/interval/IntervalConfig.kt`, `…/timer/Phase.kt`, `…/timer/Phases.kt`
- Test: `…/timer/PhasesTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class IntervalConfig(val prepareSec: Int = 10, val workSec: Int = 7, val restSec: Int = 3, val rounds: Int = 1, val cycles: Int = 1, val restBetweenCyclesSec: Int = 0, val cooldownSec: Int = 0)`
  - `enum class PhaseType { PREPARE, WORK, REST, CYCLE_REST, COOLDOWN }`
  - `data class Phase(val type: PhaseType, val durationMs: Long, val round: Int, val cycle: Int)` — round/cycle 1-based; per PREPARE/COOLDOWN valgono 0; CYCLE_REST porta il ciclo appena finito.
  - `fun buildPhases(config: IntervalConfig): List<Phase>`
  - `fun IntervalConfig.totalDurationMs(): Long`

- [ ] **Step 1: Test che falliscono**
```kotlin
@Test fun `dita volume dura 30 minuti e 42`() =
    assertEquals(1_842_000L, IntervalConfig(10, 7, 3, 30, 6, 10, 0).totalDurationMs())
@Test fun `my rounds dura 5 minuti e 2`() =
    assertEquals(302_000L, IntervalConfig(5, 7, 3, 30, 1, 0, 0).totalDurationMs())
@Test fun `sequenza dita volume`() {
    val p = buildPhases(IntervalConfig(10, 7, 3, 30, 6, 10, 0))
    assertEquals(PhaseType.PREPARE, p.first().type)
    assertEquals(Phase(PhaseType.WORK, 7_000, 30, 1), p[59])
    assertEquals(Phase(PhaseType.CYCLE_REST, 10_000, 0, 1), p[60])
    assertEquals(Phase(PhaseType.WORK, 7_000, 30, 6), p.last())
    assertEquals(1 + 6 * 59 + 5, p.size)
}
@Test fun `fasi a durata zero vengono omesse`() {
    val p = buildPhases(IntervalConfig(0, 7, 0, 3, 1, 0, 0))
    assertEquals(listOf(PhaseType.WORK, PhaseType.WORK, PhaseType.WORK), p.map { it.type })
}
@Test fun `rinfrescati in coda`() =
    assertEquals(PhaseType.COOLDOWN, buildPhases(IntervalConfig(cooldownSec = 60)).last().type)
@Test fun `un round un ciclo`() =
    assertEquals(listOf(PhaseType.PREPARE, PhaseType.WORK), buildPhases(IntervalConfig()).map { it.type })
```
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "*PhasesTest"` → FAIL (simboli mancanti)
- [ ] **Step 3: Implementare** — ordine: PREPARE; per ogni ciclo, per ogni round WORK poi REST tranne dopo l'ultimo round del ciclo; CYCLE_REST tra i cicli (non dopo l'ultimo); COOLDOWN. Omettere fasi con durata 0. `totalDurationMs` = somma delle durate.
- [ ] **Step 4:** stesso comando → PASS
- [ ] **Step 5: Commit** — `feat(timer): fasi e durata degli intervalli`

---

### Task 3: Motore del timer

**Files:**
- Create: `…/timer/Clock.kt`, `…/timer/TimerEngine.kt`
- Test: `…/timer/TimerEngineTest.kt` (con `class FakeClock(var now: Long = 0) : Clock`)

**Interfaces:**
- Consumes: `Phase`, `buildPhases` (Task 2)
- Produces:
  - `fun interface Clock { fun elapsedRealtimeMs(): Long }`
  - `@Serializable data class TimerRunState(val elapsedBeforeMs: Long = 0, val runningSinceMs: Long? = null)`
  - `data class TimerSnapshot(val phaseIndex: Int, val phase: Phase?, val phaseRemainingMs: Long, val totalRemainingMs: Long, val isRunning: Boolean, val isFinished: Boolean)` — a fine timer `phase = null`, `phaseIndex = phases.size`.
  - `class TimerEngine(phases: List<Phase>, clock: Clock, initial: TimerRunState = TimerRunState())` con `val runState: TimerRunState`, `fun start()` (idempotente, vale anche come riprendi), `fun pause()`, `fun skipTo(phaseIndex: Int)` (mantiene lo stato running/pausa), `fun snapshot(): TimerSnapshot`.

- [ ] **Step 1: Test che falliscono**
```kotlin
// fasi: PREPARE 10s, WORK 7s, REST 3s, WORK 7s  (IntervalConfig(10,7,3,2,1,0,0))
@Test fun `fermo finche non parte`() { clock.now = 50_000; assertEquals(0, e.snapshot().phaseIndex); assertFalse(e.snapshot().isRunning) }
@Test fun `tempo da timestamp`() { e.start(); clock.now += 12_500; val s = e.snapshot()
    assertEquals(1, s.phaseIndex); assertEquals(4_500, s.phaseRemainingMs); assertEquals(14_500, s.totalRemainingMs) }
@Test fun `pausa congela il tempo`() { e.start(); clock.now += 5_000; e.pause(); clock.now += 60_000
    assertEquals(5_000, e.snapshot().phaseRemainingMs) ; e.start(); clock.now += 1_000; assertEquals(4_000, e.snapshot().phaseRemainingMs) }
@Test fun `salto grande di clock dopo schermo spento`() { e.start(); clock.now += 3_600_000
    val s = e.snapshot(); assertTrue(s.isFinished); assertNull(s.phase); assertEquals(0, s.totalRemainingMs) }
@Test fun `skipTo va all inizio della fase`() { e.start(); e.skipTo(2); val s = e.snapshot()
    assertEquals(2, s.phaseIndex); assertEquals(3_000, s.phaseRemainingMs); assertTrue(s.isRunning) }
@Test fun `ripristino da runState dopo process death`() { e.start(); clock.now += 12_500
    val restored = TimerEngine(phases, clock, e.runState); assertEquals(e.snapshot(), restored.snapshot()) }
```
- [ ] **Step 2:** `./gradlew testDebugUnitTest --tests "*TimerEngineTest"` → FAIL
- [ ] **Step 3: Implementare** — elapsed = `elapsedBeforeMs + (now - runningSinceMs)` se in corso; posizione via somme cumulative delle durate (precalcolate). `skipTo(i)`: `elapsedBeforeMs = start[i]`, se in corso `runningSinceMs = now`. A fine timer `isRunning = false`.
- [ ] **Step 4:** → PASS
- [ ] **Step 5: Commit** — `feat(timer): motore basato su timestamp con pausa, salto e ripristino`

---

### Task 4: Calcolo dei segnali (cue)

**Files:**
- Create: `…/timer/Cue.kt`, `…/timer/CueCalculator.kt`
- Test: `…/timer/CueCalculatorTest.kt`

**Interfaces:**
- Consumes: `TimerSnapshot`, `Phase` (Task 3)
- Produces:
  - `sealed interface Cue { data class PhaseStarted(val index: Int, val phase: Phase) : Cue; data class Countdown(val seconds: Int) : Cue; data object TenSecondsLeft : Cue; data object Halfway : Cue; data object Tick : Cue; data object Finished : Cue }`
  - `fun cuesBetween(prev: TimerSnapshot?, curr: TimerSnapshot): List<Cue>`

Regole: `PhaseStarted` quando cambia `phaseIndex` (o `prev == null` e in corso); se tra prev e curr si sono superate più fasi, solo l'ultimo `PhaseStarted` e nessun altro segnale. Nella stessa fase: `Countdown(n)` quando `phaseRemainingMs` scende sotto la soglia `n*1000` per n = 3,2,1; `TenSecondsLeft` quando scende sotto 10 000 e la fase dura più di 10 s; `Halfway` quando scende sotto metà durata e la fase dura almeno 20 s; `Tick` a ogni secondo intero attraversato. `Finished` quando `isFinished` passa a true.

- [ ] **Step 1: Test che falliscono** — uno per regola, più:
```kotlin
@Test fun `salto di molte fasi non spara una raffica`() =  // fasi DITA VOLUME: a 40 s inizia phases[7] (WORK, round 4)
    assertEquals(listOf(Cue.PhaseStarted(7, dita[7])), cuesBetween(ditaAt(1_000), ditaAt(40_000)))
@Test fun `fase da 7 secondi niente dieci secondi ne meta`() =
    assertTrue(cuesBetween(snapAt(10_000), snapAt(17_000)).none { it == Cue.TenSecondsLeft || it == Cue.Halfway })
@Test fun `3-2-1 in una fase da 10`() =
    assertEquals(listOf(3, 2, 1), (6_900L..9_900L step 100).zipWithNext().flatMap { (a, b) -> cuesBetween(snapAt(a), snapAt(b)) }.filterIsInstance<Cue.Countdown>().map { it.seconds })
```
(`snapAt(ms)` = snapshot del motore con FakeClock a `ms` dall'avvio sulle fasi di Task 3; `ditaAt(ms)` idem su `buildPhases(IntervalConfig(10, 7, 3, 30, 6, 10, 0))`. Una soglia è "attraversata" quando `prev.phaseRemainingMs > soglia >= curr.phaseRemainingMs`.)
- [ ] **Step 2:** `--tests "*CueCalculatorTest"` → FAIL
- [ ] **Step 3: Implementare** `cuesBetween`
- [ ] **Step 4:** → PASS
- [ ] **Step 5: Commit** — `feat(timer): calcolo dei segnali tra due snapshot`

---

### Task 5: Mano, carico, arrotondamento e numeri italiani

**Files:**
- Create: `…/domain/load/Side.kt`, `…/domain/load/LoadSpec.kt`, `…/domain/load/LoadCalculator.kt`, `…/domain/load/BlockRunContext.kt`, `…/domain/format/Numbers.kt`
- Test: `…/domain/load/LoadCalculatorTest.kt`, `…/domain/load/SideModeTest.kt`, `…/domain/format/NumbersTest.kt`

**Interfaces:**
- Produces:
  - `enum class Side { RIGHT, LEFT }`
  - `enum class SideMode { NONE, RIGHT, LEFT, BOTH, ALTERNATE_RIGHT_FIRST, ALTERNATE_LEFT_FIRST }`
  - `fun SideMode.sideForCycle(cycle: Int): Side?` (null per NONE e BOTH) · `fun SideMode.cyclesPerSide(cycles: Int): Map<Side, Int>`
  - `@Serializable sealed interface LoadSpec { None; Fixed(kg: Double); FixedPerSide(rightKg: Double?, leftKg: Double?); PercentOfMax(measureTypeId: Long, percent: Double) }`
  - `data class MaxValue(val single: Double?, val right: Double?, val left: Double?)`
  - `fun roundToStep(value: Double, stepKg: Double): Double` — **BigDecimal HALF_EVEN** sul multiplo del passo (riproduce la tabella del programma).
  - `fun kgFor(spec: LoadSpec, side: Side?, max: MaxValue?, stepKg: Double): Double?` — PercentOfMax: con `side` usa `right`/`left` se presenti, altrimenti `single`; senza valore → `null`. Fixed non viene arrotondato.
  - `@Serializable data class BlockRunContext(val blockName: String, val exercise: String, val equipment: String, val sideMode: SideMode, val kgRight: Double?, val kgLeft: Double?, val kgSingle: Double?)` + `data class SideLoad(val side: Side?, val kg: Double?)` + `fun BlockRunContext.loadForCycle(cycle: Int): SideLoad`
  - `fun parseDecimalOrNull(text: String): Double?` (≥ 0) · `fun formatKg(kg: Double): String` · `fun formatDuration(ms: Long): String` ("30:42", "1:05:00", "00:10" per la schermata timer: `formatClock`)

- [ ] **Step 1: Test che falliscono**
```kotlin
// tabella del programma: massimale dx 59, sx 55, passo 0.5
@Test fun `tabella carichi del programma`() = listOf(
    0.30 to (17.5 to 16.5), 0.35 to (20.5 to 19.0), 0.50 to (29.5 to 27.5), 0.55 to (32.5 to 30.0),
    0.60 to (35.5 to 33.0), 0.65 to (38.5 to 36.0), 0.80 to (47.0 to 44.0), 0.85 to (50.0 to 47.0), 0.90 to (53.0 to 49.5),
).forEach { (pct, exp) ->
    val spec = LoadSpec.PercentOfMax(1, pct * 100); val max = MaxValue(null, 59.0, 55.0)
    assertEquals(exp.first, kgFor(spec, Side.RIGHT, max, 0.5)); assertEquals(exp.second, kgFor(spec, Side.LEFT, max, 0.5)) }
@Test fun `massimale mancante da null`() {
    assertNull(kgFor(LoadSpec.PercentOfMax(1, 30.0), Side.LEFT, MaxValue(null, 59.0, null), 0.5))
    assertNull(kgFor(LoadSpec.PercentOfMax(1, 30.0), Side.RIGHT, null, 0.5)) }
@Test fun `alternato dispari`() {
    assertEquals(listOf(Side.RIGHT, Side.LEFT, Side.RIGHT), (1..3).map { SideMode.ALTERNATE_RIGHT_FIRST.sideForCycle(it) })
    assertEquals(mapOf(Side.RIGHT to 2, Side.LEFT to 1), SideMode.ALTERNATE_RIGHT_FIRST.cyclesPerSide(3))
    assertEquals(Side.LEFT, SideMode.ALTERNATE_LEFT_FIRST.sideForCycle(1)) }
@Test fun `parse decimali`() {
    assertEquals(17.5, parseDecimalOrNull("17,5")); assertEquals(17.5, parseDecimalOrNull("17.5")); assertEquals(17.0, parseDecimalOrNull(" 17 "))
    listOf("", "abc", "1,2,3", "-3").forEach { assertNull(parseDecimalOrNull(it)) } }
@Test fun `formato kg`() { assertEquals("17,5", formatKg(17.5)); assertEquals("47", formatKg(47.0)) }
@Test fun `formato orologio`() { assertEquals("30:42", formatClock(1_842_000)); assertEquals("00:10", formatClock(10_000)); assertEquals("1:05:00", formatClock(3_900_000)) }
```
Nel formato orologio il tempo residuo si arrotonda per eccesso al secondo (9 001 ms → "00:10"): aggiungere il test.
- [ ] **Step 2:** `--tests "*domain*"` → FAIL
- [ ] **Step 3: Implementare**
- [ ] **Step 4:** → PASS
- [ ] **Step 5: Commit** — `feat(domain): mano per ciclo, calcolo carichi e formati numerici`

---

### Task 6: Database Room

**Files:**
- Create: `…/data/db/AppDatabase.kt`, `…/data/db/Converters.kt`, `…/data/db/entity/*.kt`, `…/data/db/dao/*.kt`, `…/di/DatabaseModule.kt`
- Test: `…/data/db/DaoTest.kt` (Robolectric, `Room.inMemoryDatabaseBuilder`)

**Interfaces:**
- Consumes: `IntervalConfig`, `SideMode`, `Side`, `LoadSpec` (Task 2, 5)
- Produces (entità; `IntervalConfig` come `@Embedded(prefix = "cfg_")`; `LoadSpec` e enum via `Converters` JSON/nome):
  - `SavedTimerEntity(id: Long = 0, name: String, colorArgb: Long, config: IntervalConfig, createdAt: Long)`
  - `ProgramEntity(id, name, notes, attachmentPath: String?, attachmentName: String?, attachmentMime: String?)`
  - `SessionTemplateEntity(id, programId → Program CASCADE, position: Int, name, notes)`
  - `BlockEntity(id, sessionId → Session CASCADE, position, name, exercise, equipment, notes, sideMode: SideMode, load: LoadSpec, config: IntervalConfig)`
  - `MeasureTypeEntity(id, name, unit, perSide: Boolean)` · `MeasureEntryEntity(id, typeId → MeasureType CASCADE, date: LocalDate (epochDay), value: Double?, right: Double?, left: Double?, note)`
  - `WorkoutLogEntity(id, sourceSessionId: Long?, programName, sessionName, startedAt: Long, endedAt: Long?, status: WorkoutStatus {IN_PROGRESS, DONE}, note)` — nessuna FK verso i template.
  - `BlockLogEntity(id, workoutId → WorkoutLog CASCADE, sourceBlockId: Long?, position, blockName, exercise, equipment, side: Side?, plannedKg: Double?, plannedReps: Int, completed: Boolean, repsDone: Int?, actualKg: Double?, pump: Int?, note)`
  - DAO con `Flow` per le letture e `suspend` per le scritture: `SavedTimerDao`, `ProgramDao` (programmi, sedute, blocchi, riordino `position`), `MeasureDao` (`latestEntry(typeId): Flow<MeasureEntryEntity?>` = data più recente poi id più alto), `WorkoutDao` (`lastCompletedFor(sourceBlockId): Flow<List<BlockLogEntity>>` = righe dell'ultimo workout DONE che contiene quel blocco), `fun clearAll()` per l'import.

- [ ] **Step 1: Test che falliscono**
  - `eliminare un programma elimina sedute e blocchi ma non i log` — inserisci programma→seduta→blocco e un WorkoutLog+BlockLog con `sourceBlockId`; elimina il programma; i log restano con nomi invariati.
  - `latestEntry sceglie la data piu recente` — entry 2026-10-09 (59/55) e 2026-11-13 (61/57) → 61/57.
  - `eliminare un tipo di massimale elimina le sue misure`.
  - `lastCompletedFor ignora workout in corso`.
  - `round trip IntervalConfig e LoadSpec PercentOfMax`.
- [ ] **Step 2:** `--tests "*DaoTest"` → FAIL
- [ ] **Step 3: Implementare** entità, converter, DAO, `AppDatabase` (version 1, `exportSchema = true` con `room.schemaLocation` in `app/schemas`), modulo Hilt.
- [ ] **Step 4:** → PASS
- [ ] **Step 5: Commit** — `feat(data): database Room per timer, programmi, massimali e storico`

---

### Task 7: Repository e impostazioni

**Files:**
- Create: `…/data/repo/TimerRepository.kt`, `ProgramRepository.kt`, `MeasureRepository.kt`, `WorkoutRepository.kt`, `…/data/settings/AppSettings.kt`, `…/data/settings/SettingsRepository.kt`, `…/di/DataModule.kt`
- Test: `…/data/repo/WorkoutRepositoryTest.kt`, `…/data/repo/ProgramRepositoryTest.kt`, `…/data/settings/SettingsRepositoryTest.kt`

**Interfaces:**
- Consumes: DAO (Task 6), `kgFor`, `cyclesPerSide`, `BlockRunContext` (Task 5)
- Produces:
  - `data class AppSettings(volume: Float = 1f, tone: CueTone = CueTone.BEEP, voice: Boolean = true, countdown321: Boolean = true, tenSecondsLeft: Boolean = true, halfway: Boolean = false, intervalName: Boolean = true, ticking: Boolean = false, intervalSound: Boolean = true, vibration: Boolean = false, duckMusic: Boolean = true, keepScreenOn: Boolean = true, allowRotation: Boolean = true, openLastTimer: Boolean = false, softColors: Boolean = false, screenFlash: Boolean = false, phaseColors: Map<PhaseType, Long> = DefaultPhaseColors, roundingStepKg: Double = 0.5, lastTimerId: Long? = null)`; `enum class CueTone` mappato su toni di `ToneGenerator` (niente file audio). Default = valori visti in Timer Plus.
  - `SettingsRepository { val settings: Flow<AppSettings>; suspend fun update(transform: (AppSettings) -> AppSettings) }`
  - `ProgramRepository`: CRUD programmi/sedute/blocchi, `duplicateBlock`, `move(position)`, `fun blocksWithLoads(sessionId): Flow<List<BlockWithLoad>>` dove `data class BlockWithLoad(val block: BlockEntity, val kgRight: Double?, val kgLeft: Double?, val kgSingle: Double?, val missingMax: Boolean, val totalDurationMs: Long, val lastTime: List<BlockLogEntity>)`.
  - `MeasureRepository`: CRUD tipi ed entry, `fun latestMax(typeId): Flow<MaxValue?>`.
  - `WorkoutRepository`: `suspend fun startWorkout(sessionId: Long): Long` (riusa il workout IN_PROGRESS della stessa seduta se esiste), `suspend fun startBlock(workoutId: Long, blockId: Long): BlockStart` con `data class BlockStart(val blockLogIds: List<Long>, val config: IntervalConfig, val context: BlockRunContext)` — congela kg e crea una `BlockLogEntity` per mano usata (`plannedReps = rounds × cicli di quella mano`; una sola riga con `side = null` per NONE/BOTH, con `plannedKg = kgSingle` o null), `suspend fun markCompleted(blockLogIds: List<Long>)`, `suspend fun saveResults(workoutId, note, rows: List<BlockLogEntity>)`, `suspend fun finish(workoutId)`, `fun inProgress(): Flow<WorkoutLogEntity?>`, `fun history(): Flow<List<WorkoutLogEntity>>`, `fun detail(id)`, `delete(id)`.

- [ ] **Step 1: Test che falliscono**
  - `startBlock alternato congela kg e ripetute per mano` — blocco 7:3, 30 round, 6 cicli, ALTERNATE_RIGHT_FIRST, 30% del tipo con 59/55 → due righe: RIGHT 17.5 kg 90 rip, LEFT 16.5 kg 90 rip.
  - `cambiare il massimale dopo startBlock non cambia il previsto` — dopo startBlock registra 61/57; le righe restano 17.5/16.5.
  - `startWorkout riusa la seduta in corso`.
  - `blocksWithLoads segnala massimale mancante` — tipo senza entry → `missingMax = true`, kg null.
  - `settings round trip` (DataStore su file temporaneo).
- [ ] **Step 2:** → FAIL
- [ ] **Step 3: Implementare**
- [ ] **Step 4:** → PASS
- [ ] **Step 5: Commit** — `feat(data): repository, impostazioni e congelamento del blocco`

---

### Task 8: Servizio timer, segnali audio e ripristino

**Files:**
- Create: `…/timer/ActiveRun.kt`, `…/timer/ActiveRunStore.kt`, `…/timer/TimerController.kt`, `…/timer/TimerService.kt`, `…/timer/CuePlayer.kt`, `…/timer/CueOutput.kt`, `…/timer/TimerNotification.kt`
- Modify: `AndroidManifest.xml` (service `specialUse` + `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="interval workout timer"/>`, permessi `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `VIBRATE`)
- Test: `…/timer/CueOutputTest.kt`, `…/timer/TimerControllerTest.kt`

**Interfaces:**
- Consumes: `TimerEngine`, `cuesBetween`, `BlockRunContext`, `AppSettings`, `WorkoutRepository.markCompleted`
- Produces:
  - `@Serializable data class ActiveRun(val title: String, val config: IntervalConfig, val runState: TimerRunState, val savedTimerId: Long? = null, val workoutId: Long? = null, val blockLogIds: List<Long> = emptyList(), val context: BlockRunContext? = null)`
  - `ActiveRunStore { suspend fun save(run: ActiveRun?); suspend fun load(): ActiveRun? }` (DataStore, JSON)
  - `@Singleton class TimerController @Inject constructor(...)`: `val state: StateFlow<RunningUi?>`, `fun start(run: ActiveRun)`, `fun togglePause()`, `fun skipTo(index: Int)`, `fun stop()`, `suspend fun restoreIfAny()`; `data class RunningUi(val title: String, val phases: List<Phase>, val snapshot: TimerSnapshot, val current: SideLoad?, val next: SideLoad?, val context: BlockRunContext?)`. Loop di aggiornamento ogni 100 ms che legge solo `snapshot()`; persiste `ActiveRun` a ogni start/pausa/salto/stop; a fine timer chiama `markCompleted(blockLogIds)` e cancella `ActiveRun`.
  - `data class CueOutput(val sound: Boolean, val voice: Boolean, val vibrate: Boolean)`; `fun cueOutputFor(ringerMode: Int, settings: AppSettings): CueOutput` — SILENT: tutto false; VIBRATE: solo vibrazione; NORMAL: secondo le impostazioni.
  - `CuePlayer`: `ToneGenerator` per i toni, `TextToSpeech` (Locale.ITALIAN) per i nomi fase e per "Cambia, sinistra, 16,5 chili" all'inizio di CYCLE_REST con contesto, `Vibrator`, audio focus `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` se `duckMusic`.
  - `TimerService`: avviato da `TimerController.start`, `startForeground` con notifica (fase, tempo residuo, Pausa/Stop), `PARTIAL_WAKE_LOCK` finché il timer corre, si ferma a `state == null`.

- [ ] **Step 1: Test che falliscono**
  - `CueOutputTest`: silenzioso → `CueOutput(false, false, false)`; vibrazione → `(false, false, true)`; normale con `voice = false, vibration = true` → `(true, false, true)`.
  - `TimerControllerTest` (FakeClock, `ActiveRunStore` finto, `StandardTestDispatcher`): `ripristino dopo process death riprende dal punto giusto` — start, avanza 65 s, crea un nuovo controller sullo stesso store, `restoreIfAny()` → stesso `phaseIndex` e `current.side`; `a fine timer marca i blocchi completati` — verifica chiamata a `markCompleted` con gli id.
- [ ] **Step 2:** → FAIL
- [ ] **Step 3: Implementare** (separare la parte Android del servizio dietro un'interfaccia `ServiceLauncher` per poter testare il controller)
- [ ] **Step 4:** → PASS; `./gradlew assembleDebug lint` verde
- [ ] **Step 5: Commit** — `feat(timer): foreground service, segnali audio e ripristino`

---

### Task 9: Navigazione, tema e "I miei timer"

**Files:**
- Create: `…/ui/nav/AppNavHost.kt`, `…/ui/nav/Routes.kt`, `…/ui/theme/PhaseColors.kt`, `…/ui/timers/TimerListScreen.kt|ViewModel|UiState`, `…/ui/timers/TimerEditorScreen.kt|ViewModel|UiState`, `…/ui/components/IntervalConfigEditor.kt`, `…/ui/components/DurationPickerDialog.kt`
- Modify: `MainActivity.kt`
- Test: `…/ui/timers/TimerEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerRepository`, `TimerController.start`, `SettingsRepository`
- Produces: route type-safe `@Serializable` (`TimersRoute`, `TimerEditorRoute(id: Long?)`, `RunningRoute`, `ProgramsRoute`, `MeasuresRoute`, `HistoryRoute`, `SettingsRoute`, più quelle dei task 11–15); barra in basso **Timer · Programmi · Massimali · Storico**, icona Impostazioni in alto; `IntervalConfigEditor(config: IntervalConfig, onChange: (IntervalConfig) -> Unit)` riusato dal Task 12 — righe come Timer Plus (Prepara, Lavora, Riposa, Round, Cicli, Riposa tra i cicli, Rinfrescati opzionale) con dialog min/sec o numero; `fun phaseColor(type: PhaseType, settings: AppSettings): Color`.
  Lista: nome, durata totale (`formatClock(totalDurationMs())`), barra colore, menu ⋯ (modifica, duplica, elimina con conferma), FAB "+", tocco = avvio. Avvio di un timer salvato aggiorna `lastTimerId`.

- [ ] **Step 1: Test che fallisce** — `TimerEditorViewModelTest`: nome vuoto → `canSave = false`; `Round` sotto 1 non accettato; salvataggio crea il timer con la config inserita.
- [ ] **Step 2:** → FAIL · **Step 3:** implementare · **Step 4:** → PASS
- [ ] **Step 5:** installare su dispositivo (`./gradlew installDebug`) e creare "DITA VOLUME": la lista mostra **30:42**.
- [ ] **Step 6: Commit** — `feat(ui): navigazione e lista timer`

---

### Task 10: Schermata timer in esecuzione

**Files:**
- Create: `…/ui/running/RunningScreen.kt`, `RunningViewModel.kt`, `RunningUiState.kt`, `IntervalsSheet.kt`
- Test: `…/ui/running/RunningViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerController.state`, `togglePause`, `skipTo`, `stop`; `SettingsRepository`
- Produces: layout come `reference image/`: in alto X rossa (conferma se in corso) + tempo totale residuo + titolo; centro a colore fase con nome fase e `formatClock(phaseRemainingMs)` enorme (testo auto-dimensionato); "Modifica timer" (solo timer salvati), "Modifica suoni" (→ Impostazioni audio); "Vedi intervalli" apre `IntervalsSheet` con tutte le fasi e salto al tocco; striscia "Prossimo"; in basso round rimasti, play/pausa, cicli rimasti.
  Con `context`: banda grande sempre visibile "DESTRA · 17,5 kg" (o "SINISTRA", o solo kg, o nulla), esercizio/attrezzo in piccolo; in CYCLE_REST la banda diventa "CAMBIA → SINISTRA · 16,5 kg" (dal `next`). Kg null → "—". `keepScreenOn` e rotazione da impostazioni; flash schermo al cambio fase se attivo; permesso notifiche chiesto al primo avvio di un timer.

- [ ] **Step 1: Test che fallisce** — `RunningViewModelTest`: in CYCLE_REST con contesto alternato 17,5/16,5 dopo il ciclo 1 → `banner = "CAMBIA → SINISTRA · 16,5 kg"`; in WORK ciclo 1 → `"DESTRA · 17,5 kg"`; round rimasti / cicli rimasti corretti a metà del ciclo 2.
- [ ] **Step 2–4:** FAIL → implementare → PASS
- [ ] **Step 5:** su dispositivo: avvia DITA VOLUME, spegni lo schermo 2 minuti, i segnali suonano e al risveglio il tempo è giusto.
- [ ] **Step 6: Commit** — `feat(ui): schermata timer a tutto schermo con mano e peso`

---

### Task 11: Massimali

**Files:**
- Create: `…/ui/measures/MeasureListScreen|ViewModel|UiState`, `MeasureTypeEditorScreen|ViewModel`, `MeasureDetailScreen|ViewModel|UiState`, `MeasureEntryDialog.kt`, `MeasureChart.kt`
- Test: `…/ui/measures/MeasureDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `MeasureRepository`, `parseDecimalOrNull`, `formatKg`
- Produces: lista tipi (nome, unità, ultimo valore); editor tipo (nome, unità con suggerimenti kg/rip/s, "per mano" — non modificabile se ci sono già misure); dettaglio con grafico Vico (una linea, o dx e sx) e storico modificabile/eliminabile; dialog nuova misura (data, valore o dx+sx, nota). Route `MeasureDetailRoute(typeId: Long)`, `MeasureTypeEditorRoute(id: Long?)`.

- [ ] **Step 1: Test che fallisce** — inserimento "59,5" / "55" salvato come 59.5/55.0; "abc" → errore di campo e `canSave = false`; per un tipo per mano serve almeno un lato.
- [ ] **Step 2–4:** FAIL → implementare → PASS
- [ ] **Step 5: Commit** — `feat(ui): massimali generici con storico e grafico`

---

### Task 12: Programmi, sedute, blocchi e documento allegato

**Files:**
- Create: `…/ui/programs/ProgramListScreen|ViewModel`, `ProgramDetailScreen|ViewModel|UiState`, `SessionEditorDialog.kt`, `BlockEditorScreen|ViewModel|UiState`, `…/data/files/AttachmentStore.kt`, `res/xml/file_paths.xml`
- Modify: `AndroidManifest.xml` (FileProvider `${applicationId}.files`)
- Test: `…/ui/programs/BlockEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `ProgramRepository`, `MeasureRepository`, `IntervalConfigEditor`, `kgFor`
- Produces:
  - Dettaglio programma: nome, note, sedute riordinabili (aggiungi/rinomina/elimina), allegato: "Allega documento" (`OpenDocument`, qualunque tipo), "Apri" (`ACTION_VIEW` via FileProvider, messaggio se nessuna app lo apre), "Rimuovi".
  - `AttachmentStore { suspend fun import(programId: Long, uri: Uri): Attachment; fun uriFor(path: String): Uri; suspend fun delete(path: String) }` — copia in `filesDir/attachments/<programId>/`; eliminando il programma si elimina anche il file.
  - Editor blocco: nome, esercizio, attrezzo, note (testo libero), lato (6 opzioni), carico (Nessuno / kg / kg dx+sx / % di massimale con scelta tipo e percentuale), `IntervalConfigEditor` + "Copia da timer salvato"; anteprima live "Dx 17,5 kg · Sx 16,5 kg · 30:42"; avviso se manca il massimale.
  - Route `ProgramDetailRoute(id)`, `SessionRoute(sessionId)` (Task 13), `BlockEditorRoute(sessionId, blockId: Long?)`.

- [ ] **Step 1: Test che fallisce** — `BlockEditorViewModelTest`: 30% su tipo 59/55 → anteprima kg 17.5/16.5; tipo senza misure → `missingMax = true`; percentuale "abc" → `canSave = false`.
- [ ] **Step 2–4:** FAIL → implementare → PASS
- [ ] **Step 5: Commit** — `feat(ui): programmi, sedute, blocchi e documento allegato`

---

### Task 13: Esecuzione della seduta

**Files:**
- Create: `…/ui/session/SessionScreen.kt`, `SessionViewModel.kt`, `SessionUiState.kt`
- Test: `…/ui/session/SessionViewModelTest.kt`

**Interfaces:**
- Consumes: `ProgramRepository.blocksWithLoads`, `WorkoutRepository.startWorkout/startBlock/inProgress`, `TimerController.start`
- Produces: schermata seduta = elenco blocchi (nome, lato, kg calcolati o "—", durata, riga "Ultima volta: 17,5 kg · 90/90 · ghisa 3", spunta se fatto in questa seduta); per ogni blocco "Avvia" → `startWorkout` (se non c'è) + `startBlock` + `TimerController.start(ActiveRun(... workoutId, blockLogIds, context))` → `RunningRoute`; menu blocco: modifica (→ editor), duplica, elimina; "Termina seduta" → `ResultsRoute(workoutId)` (Task 14). Se esiste un workout IN_PROGRESS di un'altra seduta, avviso con "Riprendi" / "Chiudi quella seduta".

- [ ] **Step 1: Test che fallisce** — avvio del primo blocco crea un workout e lo riusa per il secondo; blocco completato appare spuntato; con un massimale mancante l'avvio è comunque permesso.
- [ ] **Step 2–4:** FAIL → implementare → PASS
- [ ] **Step 5: Commit** — `feat(ui): esecuzione della seduta blocco per blocco`

---

### Task 14: Risultati e storico

**Files:**
- Create: `…/ui/history/ResultsScreen|ViewModel|UiState`, `HistoryListScreen|ViewModel`, `WorkoutDetailScreen|ViewModel`
- Test: `…/ui/history/ResultsViewModelTest.kt`

**Interfaces:**
- Consumes: `WorkoutRepository`, `parseDecimalOrNull`, `formatKg`
- Produces: `ResultsRoute(workoutId)`: per ogni riga blocco/mano campi ripetute fatte, kg usati, ghisa 1–10 (selettore), nota, con il previsto come suggerimento; nota generale; "Salva" → `saveResults` + `finish`. Righe di blocchi non completati mostrate come "non completato" ma modificabili. Storico: lista per data (programma · seduta · durata · stato), dettaglio previsto vs fatto, "Modifica" (riusa ResultsScreen), "Elimina" con conferma.

- [ ] **Step 1: Test che fallisce** — campi vuoti salvano `null` (non 0); ghisa fuori 1–10 rifiutata; "16,5" salvato 16.5; dopo "Salva" lo stato è DONE e `inProgress()` emette null.
- [ ] **Step 2–4:** FAIL → implementare → PASS
- [ ] **Step 5: Commit** — `feat(ui): registrazione risultati e storico sedute`

---

### Task 15: Impostazioni

**Files:**
- Create: `…/ui/settings/SettingsScreen|ViewModel`, `AudioSettingsScreen.kt`, `PhaseColorPicker.kt`
- Modify: `MainActivity.kt` (apri ultimo timer all'avvio, rotazione)
- Test: `…/ui/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Consumes: `SettingsRepository`, `CuePlayer` (anteprima suono)
- Produces: Impostazioni audio (volume, tono predefinito con anteprima, avvisi vocali, 3-2-1, 10 s rimanenti, metà, nome intervallo, ticchettio, suono intervallo, vibrazione, riduzione volume musica); generali (schermo acceso, rotazione, apri ultimo timer, colori tenui, flash schermo, colori delle 5 fasi con palette come Timer Plus + "ripristina", passo arrotondamento 0,5 / 1 / 1,25 / 2,5 kg); voce Backup (Task 16).

- [ ] **Step 1: Test che fallisce** — cambio passo a 1,25 → `blocksWithLoads` ricalcola (30% di 59 → 17.5); "ripristina colori" torna ai default.
- [ ] **Step 2–4:** FAIL → implementare → PASS
- [ ] **Step 5: Commit** — `feat(ui): impostazioni audio, generali e colori`

---

### Task 16: Backup esporta/importa

**Files:**
- Create: `…/data/backup/BackupData.kt`, `BackupSerializer.kt`, `BackupRepository.kt`
- Modify: `SettingsScreen.kt` (voci "Esporta dati" con `CreateDocument("application/json")`, "Importa dati" con `OpenDocument` + conferma "sostituisce tutti i dati")
- Test: `…/data/backup/BackupRepositoryTest.kt`

**Interfaces:**
- Consumes: tutti i DAO, `clearAll()`
- Produces: `@Serializable data class BackupData(val version: Int = 1, val exportedAt: Long, val timers: List<…>, val programs, val sessions, val blocks, val measureTypes, val measureEntries, val workouts, val blockLogs)`; `BackupRepository { suspend fun export(out: OutputStream); suspend fun import(input: InputStream): ImportResult }` — import in un'unica transazione, mantenendo gli id; file non valido o versione sconosciuta → `ImportResult.Invalid` senza toccare i dati. Gli allegati non sono inclusi (scritto nella conferma di esportazione).

- [ ] **Step 1: Test che fallisce** — round trip completo (DB popolato → export → DB vuoto → import → stessi contenuti, incluse FK); file troncato → `Invalid` e DB invariato.
- [ ] **Step 2–4:** FAIL → implementare → PASS
- [ ] **Step 5: Commit** — `feat(data): backup esporta/importa in JSON`

---

### Task 17: Collaudo sul telefono e revisione

- [ ] **Step 1:** `./gradlew assembleDebug testDebugUnitTest lint ktlintCheck` → tutto verde; correggere gli avvisi di lint rilevanti.
- [ ] **Step 2: Scenario reale** su dispositivo (`installDebug`): crea tipo "Dinamometro 20 mm" (kg, per mano) con 59/55; programma "No-hang resistenza", seduta "Venerdì aerobico", blocco "Round 7:3" (Alternato dx, 30%, Prepara 10, 7/3, 30 round, 6 cicli, 10 s tra cicli). Verifica: elenco mostra Dx 17,5 · Sx 16,5 · 30:42; durante il timer la banda e la voce del cambio sono corrette; schermo spento 5 minuti senza derive; uccidere l'app da impostazioni di sistema a metà e riaprirla → riprende; a fine seduta inserire i risultati e ritrovarli nello storico con "Ultima volta".
- [ ] **Step 3:** modalità silenziosa e vibrazione rispettate; musica abbassata durante la voce; notifica con Pausa/Stop funzionante; rotazione durante il timer.
- [ ] **Step 4:** revisione del diff completo come da `CLAUDE.md` (ciclo di vita, process death, permessi Android 13+/14+, foreground service type, accessibilità di base: content description e dimensioni testo, codice morto) con superpowers:requesting-code-review.
- [ ] **Step 5: Commit** delle correzioni — `fix: correzioni dal collaudo`
