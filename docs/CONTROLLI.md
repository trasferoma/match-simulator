# Controlli

Per ogni principio o regola importante: chi la fa rispettare. Una regola scritta solo qui o nel `CLAUDE.md` è una
probabilità che un agente la legga e la applichi; un controllo che fallisce lo ferma sempre.

**Scala**, dal più forte al più debole:

1. **compilatore o struttura**: l'errore non si può scrivere (moduli Maven, tipi);
2. **test che copre tutti i casi**, anche futuri (tutti i sorgenti, tutti gli stili, tutti gli attributi, tutte le
   chiavi delle frasi);
3. **test specifico**: protegge un caso scritto a mano;
4. **revisione**: una persona o un agente controlla a fine fase;
5. **memoria**: nessun controllo.

Per ogni regola si sceglie il gradino più alto realistico. Chi aggiorna questo file e quando: `CLAUDE.md`, sezione
«Documenti».

Stato al 2026-11-07: nessun codice, quindi nessun controllo è ancora `fatto`. I criteri DoD (27) sono quelli della
SPEC (`C:\build\git\spec\match-misulator\spec-motore-partita-v1.md`), le fasi (17) quelle del piano
(`implementation-motore-partita-v1.md` nella stessa cartella). Le `D<n>` sono in [DECISIONI.md](DECISIONI.md).

## Coperte dal piano

| Principio o regola (fonte) | Controllo previsto | Gradino | Fase, DoD | Stato |
|---|---|---|---|---|
| Motore isolato (D8, D32) | modulo `match-engine` senza dipendenze di runtime, che non vede `match-app`; `EngineIsolationTest`: import solo da `java.*` e dai package del motore, niente `java.io`, `java.nio.file`, `java.net`, `System.out`, `System.err` | 1 e 2 | 2 e 3, DoD 20 | da fare |
| Nessuna casualità fuori dal generatore (D12) | `EngineIsolationTest`: niente `Math.random`, `java.util.Random`, `ThreadLocalRandom`, `SplittableRandom`, `RandomGenerator.getDefault`; fabbrica del generatore solo in `MatchRandom` | 2 | 3, DoD 21 | da fare |
| Ordine garantito (D12) | `EngineIsolationTest`: nessuna struttura a ordine non garantito vietata dalla SPEC | 2 | 3, DoD 21 | da fare |
| Nessun numero sparso (D13) | `TuningConstantsTest`: decimali solo in `engine.config` salvo 0.0 e 1.0; interi solo -1, 0, 1, 2 e l'elenco chiuso di costanti di regolamento | 2 | 3, DoD 22 | da fare |
| Riproducibilità, nella stessa JVM e fra JVM (D12) | `MatchReproducibilityTest`; `CrossJvmReproducibilityTest` sul motore e poi anche sul testo della cronaca | 3 | 4 e 14, DoD 1 | da fare |
| Duello di Bradley-Terry (D4) | `DuelTest`: probabilità `a / (a + d)` esatta, modificatori neutri invariati | 3 | 4, DoD 4 | da fare |
| Regole sul ruolo della zona (D3) | `PitchLayoutTest` con un layout a più zone che riusa gli stessi ruoli | 3 | 3, DoD 5 | da fare |
| Ogni attributo letto da una regola (D25) | `PlayerAttributeInfluenceTest`: un caso per **ognuno** degli 8 attributi | 2 | 4, 5, 8, 9, 10, 11; DoD 8 | da fare |
| Tattiche: un punto, una direzione, neutro = 1 (D14) | `PlayStyleModifiersTest` su **tutti** gli stili | 2 | 9, DoD 14 | da fare |
| Eventi unica fonte (D9) | `MatchSummaryCalculatorTest` su eventi costruiti a mano, anche con partita sospesa | 3 | 6 e 10, DoD 15 | da fare |
| Racconto che non inventa (D10) | `NarratorTest`, `PhraseCatalogTest`: ogni chiave ha una frase, che nomina il giocatore principale e il secondario se ci sono | 2 | 14, DoD 16 | da fare |
| Lessico delle qualità (D10) | `PhraseCatalogTest`: nessuna frase usa parole di un altro valore della stessa qualità | 2 | 14, DoD 16 | da fare |
| Cronaca rilevante o completa (SPEC) | `NarratedEventFilterTest`: tutti e soli gli eventi dell'elenco; una riga per evento nella completa | 3 | 14, DoD 17 | da fare |
| Minuti di recupero in cronaca (SPEC) | `NarratorTest` con annunci di 2', 5' e 8' | 3 | 14, DoD 17 | da fare |
| Recupero (SPEC) | `StoppageTimeTest`: arrotondamento per eccesso, minimi e massimi, momento del calcolo | 3 | 12, DoD 3 | da fare |
| Regola dei 7 (SPEC) | `MinimumPlayersRuleTest`: conta per espulsione e per infortunio, risultato a tavolino | 3 | 10 e 12, DoD 27 | da fare |
| Squadre valide (SPEC) | `TeamTest`, `TeamFileReaderTest`, `TeamFileTermsTest`: uno per regola violata, valori italiani esatti | 3 | 3 e 13, DoD 18 | da fare |
| Attribuzione StatsBomb (D22) | `CalibrationReportTest`: il report contiene «Data: StatsBomb» | 3 | 7, DoD 23 | da fare |
| Squadre di forza diversa (D19) | `LeagueGeneratorTest` (forze, profili, scala); `LeagueStrengthTest`, tag `calibration`: la più forte vince più della più debole | 3 | 7, DoD 24 | da fare |
| Parametri di contorno mai a zero (D20) | `ContourEventsPresenceTest`, tag `calibration`: ogni evento di contorno compare nel campionato del banco | 3 | 12 e 16, DoD 25 | da fare |
| Criterio di «finito» (SPEC) | `CalibrationTargetsReaderTest` (forbici lette e forbice del possesso calcolata); `CalibrationAcceptanceTest`, tag `calibration`, sulle dieci metriche | 3 | 7 e 16, DoD 26 | da fare |

## Senza un controllo automatico nel piano

| Principio o regola (fonte) | Oggi nel piano | Proposta | Gradino | Quando |
|---|---|---|---|---|
| Dati grezzi fuori dal repository (D22) | nessuno | test che elenca i file di `data/calibration` e fallisce su un file non in elenco (oggi i quattro file aggregati e il README); nessuna dipendenza nuova, da concordare | 2 | Fase 2 o 3 |
| Un gesto, un tipo di evento (D11) | test caso per caso nelle fasi 10-12 | nessuno: resta alla revisione, perché distinguere i significati è un giudizio | 4 | — |
| Si taglia grosso, si raffina dove la taratura lo chiede (`CLAUDE.md`) | nessuno | nessuno: resta alla revisione | 4 | — |
| Idee sì, codice no da OpenFootManager e openengine (D23) | nessuno | nessuno: resta alla persona | 5 | — |
| Documenti aggiornati (`ESEMPI.md`, questo file) | regola nel piano, a chiusura di ogni fase; controllo finale in Fase 17 | nessuno | 4 | ogni fase |

## Integrazione continua

Il repository non ha ancora una CI: oggi tutti i controlli girano solo se qualcuno lancia i test. Quando il
repository avrà un remoto, il primo controllo da aggiungere è `mvn verify` su ogni push, senza il tag `calibration`.
