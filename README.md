# Climer

Timer a intervalli e diario di allenamento per l'arrampicata (hangboard, no-hang, ripetute), per Android.
Funziona tutto sul telefono: niente account, niente pubblicità, niente connessione a Internet.

## Cosa fa

- **Timer a intervalli**: Prepara, Lavora, Riposa, Round, Cicli, Pausa tra i cicli e Break, con colori per fase.
- **Segnali**: suoni, voce in italiano e vibrazione. I suoni seguono il volume multimediale e si sentono anche col telefono in silenzioso.
- **Schermo spento e app in background**: il timer continua con una notifica, e riprende anche se Android chiude l'app.
- **Programmi e sedute**: blocchi con esercizio, attrezzo, mano (destra, sinistra, alternata) e carico in kg o in % di un massimale. Un blocco si avvia da solo, oppure si avvia tutta la seduta e i blocchi si susseguono.
- **Massimali**: tipi di misura liberi, storico e grafico, anche per mano.
- **Storico** delle sedute con le note.
- **Cronometro** con giri e avviso periodico: suono, voce, vibrazione, cambio colore.
- **Backup** su file, da esportare e reimportare.

## Privacy

L'app **non chiede il permesso Internet**: non può inviare dati da nessuna parte.
I permessi che usa sono solo questi:

| Permesso | A cosa serve |
| --- | --- |
| `POST_NOTIFICATIONS` | la notifica del timer o del cronometro in corso |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | tenere vivo il timer a schermo spento |
| `WAKE_LOCK` | non far addormentare il telefono mentre il timer corre |
| `VIBRATE` | la vibrazione dei segnali |

Puoi controllarlo da solo: i permessi sono in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml),
oppure nell'APK con `aapt2 dump permissions Climer-x.y.z.apk`.

## Installazione

1. Scarica l'APK dall'ultima versione nella pagina [Releases](https://github.com/AmbroAl/climer/releases).
2. Aprilo dal telefono. Android chiede di consentire l'installazione da questa fonte: è normale per le app fuori dal Play Store.
3. Al primo avvio di timer o cronometro consenti le notifiche.

Serve Android 8.0 o successivo.

### Verificare l'APK

Ogni versione riporta l'impronta SHA-256 dell'APK. Per controllare che il file scaricato sia proprio quello:

- Windows (PowerShell): `Get-FileHash Climer-x.y.z.apk -Algorithm SHA256`
- macOS / Linux: `shasum -a 256 Climer-x.y.z.apk`

Il risultato deve coincidere con quello pubblicato.

## Compilare dal codice

Serve il JDK 21 e l'Android SDK (basta Android Studio).

```sh
./gradlew assembleDebug        # APK di prova in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # test
./gradlew lint ktlintCheck     # controlli
```

Per un APK di release firmato crea nella cartella principale un file `keystore.properties` (non va mai messo in git):

```properties
storeFile=keystore/la-tua-chiave.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Senza quel file `./gradlew assembleRelease` produce un APK non firmato.

Tecnologie: Kotlin, Jetpack Compose (Material 3), Room, DataStore, Hilt, coroutine. Il motore del timer calcola il tempo dai timestamp di sistema, non sommando i tick, così non accumula ritardi.

## Licenza

Climer è software libero, distribuito con licenza [GNU GPL v3.0](LICENSE): puoi usarlo, studiarlo, modificarlo e ridistribuirlo,
a patto che anche le versioni modificate restino libere con la stessa licenza.
