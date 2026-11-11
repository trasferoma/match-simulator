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

## SPEC v1: scelta dei giocatori

### Ogni zona coinvolge un solo reparto: pochi nomi dominano la cronaca
- **Oggi:** `PlayerPicker.sectorOf` lega ogni zona a un solo reparto (area propria e difesa → difensori,
  centrocampo → centrocampisti, trequarti e area avversaria → attaccanti); dentro il reparto si estrae pesando gli
  attributi di movimento. Con due attaccanti e due zone d'attacco, gli attaccanti compaiono molto più degli altri:
  in tre partite intere fra le squadre d'esempio ogni attaccante della squadra di casa compare circa 120 volte, i
  centrocampisti fra 30 e 50. Nel calcio reale i centrocampisti toccano più palloni e anche in trequarti partecipano
  centrocampisti e terzini.
- **Alternative:** per ogni zona un mix di reparti con pesi in configurazione (per esempio trequarti: attaccanti 50%,
  centrocampisti 40%, difensori 10%); lasciare la regola com'è se la taratura non ne soffre. Il mix cambia le
  estrazioni (stesso seme, partita diversa) e chiede di ripassare il banco di taratura.
- **Dal:** 2026-11-11.

### Il numero di giocatori in un reparto non cambia la forza del reparto
- **Priorità: alta.** Secondo l'utente, oggi il modello su questo punto non ha senso: va deciso prima di altre voci.
- **Oggi:** in ogni zona `PlayerPicker.pickFrom` estrae **un solo** giocatore del reparto e il duello è sempre uno
  contro uno. La forza di un reparto è quindi la qualità media dei suoi giocatori, non la quantità: un solo difensore
  da 80 difende come cinque difensori da 80, e meglio di cinque con media 70. Il modulo (5-4-1 o 3-4-3) non ha effetto
  tattico, e chi compone la squadra ha l'incentivo opposto al calcio reale: mettere in un reparto pochi giocatori, i
  migliori. Il numero conta solo in tre casi: la penalità per uomo in meno rispetto ai titolari del reparto
  (`Matchups.inferiorityFactor`), il dribbling forzato quando nel reparto davanti non c'è un compagno
  (`PossessionActionResolver.hasNoTeammateToPassTo`), il cross negato all'unico attaccante (`CrossResolver`).
- **Alternative:** la forza del duello dipende anche da quanti giocatori il reparto ha rispetto al reparto avversario
  che lo fronteggia (superiorità numerica di zona, per esempio un moltiplicatore per ogni uomo in più o in meno);
  un modulo esplicito nel file squadra con effetti in configurazione; una soglia minima per reparto nelle regole
  della squadra (attenua l'incentivo, non dà effetto al modulo). Si lega alla voce sopra: con un mix di reparti per
  zona il conteggio entra già in parte nel calcolo. Cambia le estrazioni e chiede di ripassare il banco di taratura.
- **Dal:** 2026-11-11.

### Il giocatore del reparto si estrae pesando la qualità: i migliori giocano più palloni
- **Oggi:** dentro il reparto `PlayerPicker` estrae con peso proporzionale a `PlayerAttributes.movementAverage`,
  cioè la media di tutti gli attributi tranne `goalkeeping` (non solo la velocità). Il peso è lineare: fra due
  giocatori con media 80 e 60, il primo gioca circa il 57% dei palloni. Con un giocatore molto più forte degli altri,
  il reparto finisce per coincidere con lui; per l'utente non ha senso che la qualità decida quanto spesso un
  giocatore è coinvolto.
- **Alternative:** estrazione uniforme nel reparto; peso legato al ruolo specifico o alla posizione invece che alla
  qualità; peso attenuato (per esempio radice della media) se si vuole che i migliori tocchino comunque un po' più
  palloni. Si decide insieme alla voce sopra, perché oggi è proprio il peso sulla qualità a premiare i reparti
  corti fatti di soli giocatori forti.
- **Dal:** 2026-11-11.
