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
