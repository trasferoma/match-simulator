# Punti aperti

Domande nate durante lo sviluppo che non bloccano il lavoro: scelte che il codice ha dovuto fare senza che la
specifica le decidesse, e limiti noti. Si raccolgono qui per deciderle tutte insieme alla fine, invece di fermare il
lavoro una volta per ognuna.

- **Domanda d'ingresso: è una scelta che il codice ha fatto senza che la specifica la decidesse, oppure un limite
  noto, che una persona dovrà confermare e che non blocca il lavoro?** Una domanda che blocca non entra: va nei
  problemi aperti del piano e ferma il lavoro. Una scelta già decisa dalla specifica non entra. Unico documento in cui,
  **nel dubbio, si entra**: perdere una domanda costa più che rileggerne una inutile.
- È l'unico documento di `docs/` con contenuto di dettaglio e temporaneo. La decisione su una voce va nella
  specifica, o in [DECISIONI.md](DECISIONI.md) se vale per ogni funzionalità.
- «Oggi» descrive il comportamento del codice quando la voce è scritta: può citare un valore, se serve a capire il
  problema, ma non numeri di criteri di accettazione o di fasi di un piano. I dettagli e i numeri di ogni scelta
  stanno nel registro del piano di implementazione.
- **Formato:** un gruppo `##` per specifica o funzionalità; se le voci sono molte, `## <specifica>: <tema>`. Per ogni
  voce:

  ```
  ### <la domanda, in poche parole>
  - **Oggi:** <cosa fa il codice e perché>.
  - **Alternative:** <le altre strade>.   oppure   **Da decidere:** <cosa manca per decidere>.
  - **Dal:** <data>.
  ```
- **Chi aggiorna e quando:** `CLAUDE.md`, sezione «Documenti».

Nelle intestazioni, «SPEC v1» è la specifica della prima versione, `spec-motore-partita-v1.md`.

## Reparti per zona: struttura

### Le regole del passaggio e del cross ricevono otto collaboratori nel costruttore
- **Oggi:** `CrossResolver` e `PossessionActionResolver` ricevono anche `ZonePresence` (scelta del crossatore e soglia
  degli uomini per il passaggio) e salgono a otto parametri nel costruttore; erano già a sette.
- **Alternative:** `CrossResolver` può chiedere i giocatori presenti a `PlayerPicker`, che ha già `ZonePresence`
  (scende a sette, ma con un metodo che inoltra soltanto); `PossessionActionResolver` può chiedere gli uomini nella
  zona a `Matchups`, che riceve `ZonePresence`, oppure ricevere i soli gruppi di configurazione che usa invece di
  `EngineConfig` intera; lasciare com'è.
- **Dal:** 2026-11-12.

### Una soglia del passaggio a zero può far fallire la partita con presenze non di default
- **Oggi:** la configurazione accetta una soglia degli uomini per il passaggio pari a 0. Con una tabella delle presenze
  che lascia una zona senza nessun compagno presente, il passaggio parte lo stesso e la scelta di chi riceve
  (`PlayerPicker.pickOtherThan`) fallisce con un'eccezione. Con le presenze di default non accade.
- **Alternative:** rifiutare in configurazione una soglia che non sia positiva; non tentare il passaggio quando gli
  uomini sono 0, qualunque sia la soglia; lasciare com'è, perché l'errore è esplicito e la configurazione non è di
  default.
- **Dal:** 2026-11-12.

### Il cross da calcio piazzato usa le presenze dell'area, non quelle della zona della battuta
- **Oggi:** per angolo e punizione crossata il duello del cross e la scelta del difensore usano il ruolo di zona
  dell'area avversaria, perché lo stato della palla è già in area quando parte il cross. Per il cross in gioco aperto
  usano la zona dell'azione (trequarti). La specifica dice «zona = quella dell'azione», che per un calcio piazzato non
  è univoco.
- **Alternative:** usare per i calci piazzati la zona da cui parte la battuta (trequarti per una punizione, area per
  l'angolo); lasciare com'è.
- **Dal:** 2026-11-12.

## Cronaca highlights

### Un messaggio più largo della finestra sporca il ridisegno sul posto
- **Oggi:** il terminale manda a capo la riga e la risalita di una riga cade sulla riga sbagliata, su due righe come su
  una; con la finestra a tutto schermo le frasi (fino a circa 120 caratteri) ci stanno.
- **Alternative:** troncare il messaggio alla larghezza della finestra con «…»; calcolare quante righe risalire.
- **Dal:** 2026-11-13.

### stderr rediretto su file usa la codifica della console
- **Oggi:** su un terminale interattivo stdout e stderr usano la codifica della console; con `2>file` il file non è
  UTF-8, perché il rilevamento guarda stdin e stdout.
- **Alternative:** stderr sempre in UTF-8.
- **Dal:** 2026-11-13.

### Rilevamento del terminale non provato a vista su JDK 22+ e con il terminale emulato di IntelliJ
- **Oggi:** `TerminalDetector` usa `System.console()` e, se esiste, `isTerminal()`; da Git Bash e dalla console Run di
  IntelliJ si ha il ripiego (con `--animate` una riga sola).
- **Da decidere:** provare su JDK 22+ e con «Emulate terminal in output console».
- **Dal:** 2026-11-13.

### Due eventi uguali consecutivi senza giocatore tengono la stessa frase
- **Oggi:** la variante diversa per eventi ripetuti vale solo se c'è un giocatore primario (con la squadra).
- **Alternative:** includere gli eventi senza giocatore nella regola.
- **Dal:** 2026-11-13.

### La configurazione degli highlights si legge a ogni avvio
- **Oggi:** `MatchCommandLine.standard` legge `narration/highlights.properties` anche per cronaca predefinita, `--full`
  e `--stepByStep`; un file rotto fermerebbe anche quelle.
- **Alternative:** lettura solo con `--highlights`.
- **Dal:** 2026-11-13.

### Pause di un gol raccontato in una fermata senza occasione
- **Oggi:** se il gol non è trattato come occasione (configurazione diversa dal default), prima del gol e del contesto
  c'è la pausa di riga d'evento (1000 ms), dopo il gol quella del gol; la specifica non lo decideva.
- **Alternative:** pausa d'occasione anche lì.
- **Dal:** 2026-11-13.

### Nel ripiego la riga del campo di una fermata con gol mostra il punteggio di prima
- **Oggi:** senza animazione il campo si stampa una volta prima della fermata, con il punteggio dopo il primo evento;
  il gol si legge dalla sua riga.
- **Alternative:** stampare la riga del campo anche dopo il gol.
- **Dal:** 2026-11-13.

### Il motore fa fare allo stesso giocatore due azioni uguali di fila
- **Oggi:** succede in molte partite (stesso passaggio o dribbling due volte); la cronaca lo maschera scegliendo
  un'altra variante della frase.
- **Alternative:** impedirlo nel motore (cambia le partite simulate e richiede una nuova taratura); lasciare com'è.
- **Dal:** 2026-11-13.
