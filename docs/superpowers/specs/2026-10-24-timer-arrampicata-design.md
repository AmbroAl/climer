# Timer & programmi di allenamento per arrampicata — Design v1

Data: 2026-10-24 · Stato: bozza da approvare

## 1. Perché

Timer Plus, usato finora come timer a intervalli, è diventato a pagamento e ha bloccato funzioni dietro paywall.
L'utente (arrampicatore, allenamento no-hang al travetto con dinamometro) vuole un sostituto personale per Android che:

- faccia almeno quello che faceva Timer Plus (timer a intervalli a tutto schermo, suoni/voce, preset salvati);
- aggiunga ciò che oggi lo costringe a rileggere il documento del programma durante la seduta: **quale mano**, **quale peso**, **quale esercizio**;
- tenga **massimali** e **storico sedute** sul telefono, per confrontarli nel tempo.

Caso d'uso reale che ha originato tutto (venerdì aerobico del programma "No-hang resistenza"):
7:3 × 30 ripetute con la destra, 10 s per cambiare peso e mano, 7:3 × 30 con la sinistra… 3 cicli per mano = 6 cicli, pesi diversi per mano (30% del massimale: 17,5 kg dx, 16,5 kg sx).
In Timer Plus: Lavora 7, Riposa 3, Round 30, Cicli 6, Riposa tra i cicli 10 — ma senza sapere né la mano né il peso.

Successo = la seduta sopra si fa interamente dall'app, senza aprire il documento, e alla fine resta registrata.

## 2. Obiettivi, non-obiettivi, anti-obiettivi

**Obiettivi v1**
- Timer a intervalli equivalente a Timer Plus (sezione 4).
- Programmi → sedute → blocchi inseriti a mano, con esercizio, attrezzo, lato e carico per blocco.
- Carico in kg fissi o in % di un massimale, calcolato per mano.
- Massimali generici definiti dall'utente, storico e grafico per tipo.
- Registrazione manuale dei risultati a fine blocco/seduta, storico consultabile.
- Documento di riferimento allegabile a un programma.

**Non-obiettivi v1** (forse in seguito)
- Calendario/settimana corrente, proposta automatica della seduta del giorno.
- Progressione automatica dei carichi (decide l'utente modificando % o kg).
- Test guidati (es. 7:3 a oltranza con conteggio): i test si registrano a mano come massimali.
- Tipi di massimale/programmi preimpostati: si parte vuoti.
- Lettura automatica del documento per generare il programma.
- Cronometro (lo "Stopwatch" di Timer Plus), Modelli HIIT preconfezionati.
- Cloud, account, sincronizzazione, backend. Wear OS, widget.

**Anti-obiettivi** (sarebbe un fallimento anche se "funziona")
- Timer che si ferma o deriva a schermo spento / app in background.
- Durante la seduta servono tocchi per andare avanti dentro un blocco: dentro un blocco il timer deve scorrere da solo, incluso il cambio mano.
- Peso/mano non leggibili a colpo d'occhio a 1–2 m di distanza, con le mani impegnate.
- Perdere lo storico modificando un programma (lo storico deve restare come era).

## 3. Vincoli

Da `CLAUDE.md` (vincolanti): Kotlin, Jetpack Compose, Material 3, MVVM + UDF con `StateFlow<UiState>`, Room, DataStore, Hilt, Coroutines/Flow, minSdk 26, targetSdk ultimo stabile, Gradle KTS + version catalog, timer come classe pura con `Clock` iniettabile, Foreground Service, tempo da `elapsedRealtime`, suono + vibrazione rispettando la modalità silenziosa, stringhe in `strings.xml` in italiano, TDD su dominio e timer.

- Utente unico, dati solo sul telefono.
- L'utente ha autorizzato l'installazione di tutto il necessario (Android Studio/JDK/SDK e le librerie che servono): non serve chiedere per ogni dipendenza ragionevole, ma vanno elencate nel piano.
- L'ambiente di sviluppo oggi non ha né JDK né Android SDK: il primo passo del piano è prepararlo.

## 4. Timer a intervalli (parità con Timer Plus)

**Configurazione** (`IntervalConfig`, riusata identica da timer salvati e blocchi):
- Prepara (s) — conto alla rovescia iniziale.
- Lavora (s), Riposa (s): un round = lavoro + riposo.
- Round (n).
- Cicli (n): ripetizioni dell'intera serie di round.
- Riposa tra i cicli (s).
- Rinfrescati (s, opzionale) — defaticamento finale.
- Nome e colore del timer.

Comportamento (come Timer Plus): l'ultimo riposo di un ciclo è sostituito dal riposo tra i cicli; dopo l'ultimo round dell'ultimo ciclo non c'è riposo.
Durata = Prepara + Cicli × (Round × Lavora + (Round − 1) × Riposa) + (Cicli − 1) × RiposaTraCicli + Rinfrescati.
Verificata sui due timer reali di Timer Plus, da usare come test d'accettazione:
"DITA VOLUME" (Prepara 10, 7/3, 30 round, 6 cicli, 10 s tra cicli) = 10 + 6×297 + 5×10 = 1842 s = **30:42**;
"MY ROUNDS" (Prepara 5, 7/3, 30 round, 1 ciclo) = 5 + 297 = 302 s = **05:02**.

**Schermata in esecuzione** (riferimento: `reference image/`):
- Sfondo pieno a colore di fase: Prepara giallo, Lavora verde, Riposa rosso, Riposa tra i cicli arancione, Rinfrescati azzurro.
- Nome fase grande + tempo residuo enorme; in alto tempo totale residuo e nome timer; striscia "Prossimo: …"; in basso round rimasti, play/pausa, cicli rimasti; X per uscire (con conferma se in corso).
- "Vedi intervalli": lista di tutte le fasi con la corrente evidenziata; tocco su una fase = salta lì.
- Scorciatoie a "Modifica timer" e "Modifica suoni".

**Audio e segnali** (impostazioni globali, come Timer Plus):
volume generale, suono predefinito, avvisi vocali (TTS) on/off, 3-2-1, "10 secondi rimanenti", "metà", nome intervallo, ticchettio, suono intervallo, vibrazione, riduzione volume musica (audio ducking).

**Impostazioni generali**: schermo sempre acceso durante il timer, rotazione consentita, apri ultimo timer all'avvio, colori tenui, colori delle fasi personalizzabili, flash schermo a cambio fase. (Torcia di Timer Plus: esclusa, poco utile.)

**Lista "I miei timer"**: timer salvati con durata totale e colore, crea/modifica/duplica/elimina, avvio diretto. Resta utilizzabile senza alcun programma.

## 5. Programmi, sedute, blocchi

Gerarchia, tutto inserito a mano:

- **Programma**: nome, note, documento allegato opzionale.
  - **Seduta** (ordinata): nome (es. "Lunedì intensivo"), note.
    - **Blocco** (ordinato): l'unità che fa partire un timer.

**Blocco** contiene:
- Nome (es. "Ripetute 7:3").
- Esercizio, attrezzo, presa/note: testo libero (es. "No-hang", "Travetto 20 mm", "mezza arcuata").
- **Lato**: Nessuno · Destra · Sinistra · Entrambe · Alternato (inizia dx) · Alternato (inizia sx).
  Con "Alternato" ogni **ciclo** è una mano: ciclo 1 dx, 2 sx, 3 dx… Il riposo tra i cicli è il momento del cambio peso/mano.
- **Carico**: Nessuno · kg fissi (un valore, oppure dx e sx separati) · % di un tipo di massimale (sezione 6).
- **Timer**: un `IntervalConfig`. In editor si può partire da zero o copiare la configurazione di un timer salvato.

**Durante il blocco**, oltre alla schermata timer standard:
- Indicazione fissa e molto grande di **mano** e **peso** del ciclo corrente (es. "DESTRA · 17,5 kg"), più esercizio/attrezzo in piccolo.
- Nel riposo tra i cicli la schermata diventa "CAMBIA → SINISTRA · 16,5 kg"; con avvisi vocali attivi la voce lo annuncia.
- Il prossimo ciclo è mostrato nella striscia "Prossimo".

## 6. Massimali (generici)

Nessun tipo preimpostato. L'utente crea **tipi di misura**:
- Nome (es. "Dinamometro 20 mm", "7:3 al 50% a cedimento").
- Unità: testo libero con suggerimenti (kg, rip, s).
- Per lato sì/no (se sì, ogni valore ha dx e sx).

**Registrazione**: data, valore (o dx + sx), nota. Si inseriscono a mano.
**Confronto**: per ogni tipo, storico in lista + grafico nel tempo (una linea, o due per dx/sx). Si confrontano solo valori dello stesso tipo.

**Carico in %**: un blocco con carico "% di massimale" punta a un tipo (con unità kg, idealmente) e una percentuale.
Peso = ultimo valore registrato di quel tipo (per la mano del ciclo) × %, arrotondato al passo configurabile (default 0,5 kg, impostazione globale).
Con lato "Entrambe" e un tipo per lato si mostrano entrambi i pesi (dx e sx); con un tipo non per lato si usa il valore unico per qualunque mano.
Quando si registra un nuovo valore, tutti i blocchi collegati mostrano subito il nuovo peso. Se manca il valore per una mano, il blocco mostra "—" e un avviso nell'editor e prima dell'avvio, ma si può partire comunque.

## 7. Esecuzione di una seduta

1. Programmi → programma → seduta: schermata con l'elenco dei blocchi (nome, lato, pesi calcolati, durata).
2. L'utente **avvia a mano ogni blocco** (tocco su "Avvia"). Dentro il blocco il timer scorre da solo fino alla fine, inclusi cambi mano.
3. Fine blocco → torna all'elenco, blocco segnato come fatto. Si può fare un blocco in qualunque ordine, saltarlo o ripeterlo.
4. "Termina seduta" → **schermata risultati**, compilata **a mano** dall'utente: per ogni blocco eseguito (e per mano, se ha lato) ripetute fatte, kg usati, ghisa 1–10, nota; nota generale di seduta. I campi mostrano come suggerimento i valori previsti.
5. Salvataggio nello **storico**. Se si esce senza terminare, la seduta resta "in corso" e si può riprendere o chiudere.

Il piano di avvio di un blocco si "congela" al momento dell'avvio (kg e configurazione): lo storico registra cosa era previsto quel giorno anche se poi si cambia il programma o il massimale.

## 8. Storico

- Elenco sedute per data (programma, seduta, durata).
- Dettaglio: blocchi con previsto vs fatto, ghisa, note. Modificabile ed eliminabile.
- Nell'elenco blocchi di una seduta da avviare: riga "ultima volta: 17,5 kg · 30/30 · ghisa 3" per aiutare la scelta del carico.
- Lo storico contiene copie di nomi e valori (snapshot), non riferimenti vivi: modificare/eliminare un programma non altera lo storico.

## 9. Documento allegato

Un file per programma (PDF, DOCX, immagine…), scelto con il selettore di sistema, **copiato** nello spazio dell'app. Si apre con l'app esterna predefinita del telefono (FileProvider + intent). Nessuna lettura del contenuto.

## 10. Navigazione (proposta)

Barra in basso: **Timer** · **Programmi** · **Massimali** · **Storico**. Impostazioni da icona in alto. Tema scuro come Timer Plus.

## 11. Robustezza

- Timer e blocchi girano in un Foreground Service (tipo appropriato per Android 14+), notifica persistente con fase e tempo, controlli pausa/stop; permesso notifiche su Android 13+.
- Sopravvive a rotazione, schermo spento, app in background, process death (stato ricostruibile da timestamp di avvio/pause salvati).
- Backup: **esporta/importa** tutti i dati in un file (JSON) dalle Impostazioni, perché i dati sono solo sul telefono.

## 12. Scelte fatte da Claude (non discusse con l'utente)

- Gerarchia Programma → Seduta → Blocco; "Alternato" = un ciclo per mano.
- Cronometro escluso dalla v1; torcia esclusa.
- Esercizio/attrezzo come testo libero (niente libreria esercizi).
- Documento aperto con app esterna, non visualizzato dentro l'app.
- Passo di arrotondamento globale, default 0,5 kg.
- Seduta "in corso" riprendibile; snapshot nello storico.
- Esporta/importa JSON in v1.
- Navigazione a 4 tab.
- Stringhe solo in italiano.
- Lato "Entrambe" con massimale per lato: mostra i due pesi.

## 13. Lasciato al builder

Nomi di package e classi, schema Room dettagliato e migrazioni, scelta libreria grafici, libreria/approccio audio (TTS di sistema, SoundPool), dettagli grafici entro lo stile Timer Plus, ordine dei passi del piano.
