# Esempi da seguire

Per ogni tipo di compito, la classe da imitare. Un agente che deve scrivere una regola, un evento o un test parte da
qui, non dalla classe più vicina che trova.

**Stato al 2026-11-07: non esiste ancora codice.** Le righe indicano la prima classe prevista dal piano
(`C:\build\git\spec\match-misulator\implementation-motore-partita-v1.md`) per ogni tipo di compito, con la fase che
la crea. Finché una riga dice `prevista`, la classe non esiste: non va cercata né imitata.

**Stati.** `prevista`: la classe non esiste ancora. `esiste`: la classe c'è, con il percorso reale, ed è un buon
modello. Se la classe scritta non è un buon modello, la riga resta `prevista` e il motivo va in
[DEVIAZIONI.md](DEVIAZIONI.md); se una classe successiva diventa un esempio migliore, la riga cambia. Chi aggiorna e
quando: `CLAUDE.md`, sezione «Documenti».

Package sotto `org.tasktelemetry.matchsimulator` (decisioni D31 e D32).

| Per fare | Guarda | Fase | Stato |
|---|---|---|---|
| un tipo del dominio con invarianti (validazione nel costruttore, messaggio che nomina la regola) | `engine.team.Team` | 3 | prevista |
| un valore della configurazione (numero di taratura con default) | `engine.config.EngineConfig`, `DurationRange` | 3 | prevista |
| una regola che legge il campo (ruolo della zona, mai l'indice) | `engine.pitch.PitchLayout`, `ZoneRole` | 3 | prevista |
| un test di guardia che legge i sorgenti del motore | `EngineIsolationTest`, `TuningConstantsTest` | 3 | prevista |
| una regola che risolve un'azione (esiti, stato successivo, durata) | `engine.resolution.PossessionActionResolver`, `ActionOutcome` | 4 | prevista |
| un confronto fra due valori, con la probabilità esposta | `engine.resolution.Duel` | 4 | prevista |
| una scelta casuale | `engine.MatchRandom`, passato alla regola, mai creato da lei | 4 | prevista |
| un tipo di evento nuovo con il suo dettaglio | `engine.event.MatchEvent`, `EventType`, `EventDetail` | 4 | prevista |
| un test di regola: probabilità esatta (1e-9) e direzione stretta | `DuelTest` | 4 | prevista |
| un test sull'effetto di un attributo | `PlayerAttributeInfluenceTest` | 4 | prevista |
| un modello di probabilità (xG o simili) | `engine.resolution.shot.ExpectedGoalsModel` | 5 | prevista |
| una palla inattiva (squadra e zona di ripartenza) | `engine.resolution.stoppage.SetPieceResolver` | 5 | prevista |
| un calcolo ricavato dagli eventi | `summary.MatchSummaryCalculator` | 6 | prevista |
| un test di calcolo su eventi costruiti a mano | `MatchSummaryCalculatorTest` | 6 | prevista |
| una metrica del banco confrontata con una forbice | `calibration.LeagueMetrics`, `CalibrationReport` | 7 | prevista |
| un test lento del banco (tag `calibration`) | `LeagueStrengthTest` | 7 | prevista |
| un parametro del banco, non del motore (D55) | `calibration.BenchConfig` | 7 | prevista |
| un ramo nuovo dell'azione d'attacco | `engine.resolution.shot.CrossResolver` | 8 | prevista |
| un moltiplicatore di stile | `engine.config.PlayStyleModifiers` | 9 | prevista |
| una modifica degli uomini in campo | `engine.resolution.lineup.LineupRepair` | 10 | prevista |
| una lettura di file con errori che indicano file e campo | `teamfile.TeamFileReader`, `InvalidTeamFileException` | 13 | prevista |
| una frase della cronaca | `narration.PhraseCatalog`, `phrases_it.properties` | 14 | prevista |
