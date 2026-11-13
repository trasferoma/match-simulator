# Controlli

Per ogni principio di [DECISIONI.md](DECISIONI.md): chi lo fa rispettare. Una regola scritta solo in prosa è una
probabilità che venga letta e applicata; un controllo che fallisce ferma sempre chi la viola, anche se non l'ha letta.

**Scala**, dal più forte al più debole. Per ogni principio si sceglie il gradino più alto realistico.

1. **compilatore o struttura**: l'errore non si può scrivere (moduli, tipi, visibilità);
2. **test che copre tutti i casi**, anche futuri (tutte le classi di un package, tutti i valori di un enum, tutti i
   sorgenti);
3. **test specifico**: protegge un caso scritto a mano;
4. **revisione**: una persona o un agente controlla;
5. **memoria**: nessun controllo.

- **Domanda d'ingresso: protegge un principio di [DECISIONI.md](DECISIONI.md)?** Un test che protegge una regola di
  dettaglio di una funzionalità non entra: lo segue la sua specifica. Nel dubbio, non entra.
- Ogni riga nomina il controllo con un nome stabile (classe di test, modulo, regola), **mai** con il numero di un
  criterio di accettazione o di una fase: il legame con quei numeri lo tiene il piano.
- **Chi aggiorna e quando:** `CLAUDE.md`, sezione «Documenti».

## Coperti

| Principio (D<n>) | Controllo | Gradino | Stato |
|---|---|---|---|
| D3 regole sul ruolo della zona | `PitchLayoutTest`: un layout con un numero diverso di zone riusa gli stessi ruoli | 3 | fatto |
| D4 duello di Bradley-Terry | `DuelTest`: probabilità esatta, modificatori neutri invariati | 3 | fatto |
| D5 esiti con stato successivo e durata | `PossessionActionResolverTest`: ogni esito porta uno stato valido e una durata nell'intervallo | 2 | fatto |
| D6 tiro con xG | `ExpectedGoalsModelTest`, `ShotResolverTest` | 3 | fatto |
| D7 fallo a catena | `FoulResolverTest` | 3 | fatto |
| D8 motore isolato | modulo `match-engine` che non vede `match-app`; `EngineIsolationTest`: import solo da `java.*` e dai package del motore, niente I/O né console | 1 e 2 | fatto |
| D9 eventi unica fonte | `MatchSummaryCalculatorTest` su eventi costruiti a mano | 3 | fatto |
| D10 racconto che non inventa | `NarratorTest`, `PhraseCatalogTest`: ogni chiave ha una frase che nomina chi fa cosa e usa solo il lessico del proprio valore; `HighlightsNarratorTest`: ogni riga della cronaca highlights è quella di `--full`, il contesto del gol usa la stessa frase | 2 | fatto |
| D12 riproducibilità: casualità e ordine | `EngineIsolationTest`: nessuna sorgente di casualità fuori da `MatchRandom`, nessuna struttura a ordine non garantito | 2 | fatto |
| D12 riproducibilità: stessa partita | `MatchReproducibilityTest`; `CrossJvmReproducibilityTest` in `match-engine` sugli eventi e in `match-app` sul testo della cronaca | 3 | fatto |
| D13 nessun numero sparso | `TuningConstantsTest` sui sorgenti di `match-engine` | 2 | fatto |
| D14 tattiche: un punto, una direzione, neutro = 1 | `PlayStyleModifiersTest` su tutti gli stili | 2 | fatto |
| D16 dipendenze esterne solo se concordate | `EngineIsolationTest`: il pom di `match-engine` non ha dipendenze fuori dallo scope test (non controlla quelle ereditate dal pom padre) | 2 | fatto |
| D19 squadre di forza diversa | `LeagueGeneratorTest`; `LeagueStrengthTest` (tag `calibration`) | 3 | fatto |
| D20 parametri di orientamento mai a zero | `ContourEventsPresenceTest` (tag `calibration`): ogni evento di contorno compare nel campionato del banco | 3 | fatto |
| D22 attribuzione StatsBomb | `CalibrationReportTest`: il report contiene «Data: StatsBomb» | 3 | fatto |
| D24 fasce come probabilità | `CrossResolverTest` | 3 | fatto |
| D25 ogni attributo letto da una regola | `PlayerAttributeInfluenceTest`: un caso per ognuno degli attributi | 2 | fatto |
| D32 due moduli | struttura Maven: `pom.xml` radice con i moduli `match-engine` e `match-app` | 1 | fatto |
| D33 Jackson solo in `match-app` | struttura Maven e `EngineIsolationTest` sul pom del motore | 1 e 2 | fatto |
| D35 librerie di test solo nello scope test | `EngineIsolationTest` sul pom del motore | 2 | fatto |
| D49 il motore vede solo le proprie costanti | modulo `match-engine` che non vede `match-app`; `TeamFileTermsTest` per la traduzione | 1 e 3 | fatto |
| D57 tutti i reparti in ogni zona, forza per uomini, scelta per presenza | `ZonePresenceTest` (un 4-4-2 contro un 4-4-2 è neutro, un uomo in meno pesa), `PlayerPickerTest` (scelta per presenza, uniforme nel reparto), `FormationEffectTest` (i moduli estremi non battono il 4-4-2) | 3 | fatto |
| D56 giocatore identificato con la sua squadra | `NarrationContextTest` e `NarratorTest` (doppietta con lo stesso giocatore in casa e in ospite), `MatchAssistsTest` (stesso `Team` per casa e ospite), `NarratedEventFilterTest` (cartellino a un omonimo dell'altra squadra dopo il fallo), `NarratorRepeatedEventsTest` (un omonimo dell'altra squadra non è un evento ripetuto); un gradino più alto richiederebbe un tipo che porti insieme giocatore e squadra | 3 | fatto |

`Stato`: `previsto` se il controllo è pianificato ma non esiste, `fatto` se la classe o la struttura esiste.

## Senza controllo automatico

| Principio (D<n>) | Proposta | Gradino possibile |
|---|---|---|
| D1 catena di stati sul possesso | revisione: è il livello del modello | 4 |
| D2 stato dell'azione | revisione | 4 |
| D11 un gesto, un tipo di evento | revisione: distinguere i significati è un giudizio | 4 |
| D15 Java 21 puro, niente framework | revisione dei pom | 4 |
| D20 un obiettivo senza fonte non si usa per tarare | revisione del banco | 4 |
| D22 dati grezzi fuori dal repository | test che elenca i file di `data/calibration` e fallisce su un file non in elenco; nessuna dipendenza nuova, da concordare | 2 |
| D23 idee sì, codice no da OpenFootManager e openengine | persona | 5 |
| D31 nome e package radice | revisione | 4 |
| D55 configurazione del banco separata da quella del motore | revisione: `BenchConfig` in `match-app`, nessun parametro del banco in `EngineConfig` | 4 |
