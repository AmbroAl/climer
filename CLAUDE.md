# CLAUDE.md — App Android: Timer & Tracker allenamenti

## Progetto
App Android per gestire timer di allenamento (intervalli, serie, recuperi) e tracciare le sessioni svolte.
Utente unico, dati solo locali (per ora niente backend/cloud).

## Stack (non cambiare senza chiedere)
- Kotlin, Jetpack Compose, Material 3
- Architettura: MVVM + Unidirectional Data Flow (ViewModel espone `StateFlow<UiState>`)
- Persistenza: Room (sessioni, esercizi, storico); DataStore per le preferenze
- DI: Hilt
- Coroutines/Flow; niente RxJava, niente LiveData
- minSdk 26, targetSdk ultimo stabile
- Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`)

## Struttura
- `app/src/main/java/.../ui/<feature>/` — Screen + ViewModel + UiState per feature
- `.../domain/` — modelli e logica pura (testabile senza Android)
- `.../data/` — Room, repository, DataStore
- `.../timer/` — engine del timer + Foreground Service

## Regole critiche sul timer
- La logica del timer sta in una classe pura nel package `timer` (input: tempo da un `Clock` iniettabile), così è testabile senza emulatore.
- Il timer deve sopravvivere a schermo spento e app in background: usa un Foreground Service con notifica persistente, non solo coroutine nel ViewModel.
- Calcola il tempo residuo da timestamp (`elapsedRealtime`), mai sommando tick: i delay derivano.
- Segnali di fine intervallo: suono + vibrazione. I suoni (e la voce) sono contenuti multimediali: seguono il volume multimediale e si sentono anche con il telefono in vibrazione o silenzioso.

## Comandi
- Build: `./gradlew assembleDebug`
- Test unitari: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lint ktlintCheck`
- Test strumentati (solo se richiesto): `./gradlew connectedDebugAndroidTest`

## Workflow
1. Per ogni feature nuova: prima un piano breve (file toccati, modelli, casi limite), aspetta il mio OK.
2. Lavora a passi piccoli; un commit per passo logico, messaggi in stile Conventional Commits.
3. Logica di dominio e timer: scrivi prima i test.
4. Prima di dire "fatto": build + test + lint verdi. Se qualcosa fallisce, dillo, non nasconderlo.
5. Non aggiungere dipendenze senza chiedere.
6. Se una richiesta è ambigua, fai una domanda invece di indovinare.

## Revisione
A fine feature, rivedi il diff come un reviewer: correttezza, gestione ciclo di vita (rotazione, process death), permessi (notifiche Android 13+, foreground service type), accessibilità di base, codice morto.

## Stile
- Composable piccoli e stateless; lo stato vive nel ViewModel.
- Niente `!!`; gestisci i null esplicitamente.
- Stringhe in `strings.xml` (lingua base: italiano).
- Commenti solo dove il "perché" non è ovvio.
