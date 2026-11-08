# Esempi da seguire

Per ogni tipo di compito, la classe da imitare. Chi deve scrivere una classe di un tipo già presente parte da qui,
non dalla classe più vicina che trova: la più vicina può essere un caso storto.

- **Domanda d'ingresso: è un tipo di compito che si ripeterà, e questa classe esistente è un buon modello per
  farlo?** Una classe unica nel suo genere (un punto d'ingresso, un adattatore usato una volta) non entra, anche se è
  scritta bene. Nel dubbio, non entra.
- **Solo classi che esistono.** Una classe prevista ma non ancora scritta non entra: il suo nome e il suo package
  possono cambiare prima che esista.
- **Una riga per tipo di compito**, con il percorso reale e il motivo per cui è un buon modello.
- Una classe che esiste ma non va imitata non sta qui: sta in [DEVIAZIONI.md](DEVIAZIONI.md).
- **Chi aggiorna e quando:** `CLAUDE.md`, sezione «Documenti».

| Per fare | Guarda | Perché è un buon modello |
|---|---|---|
| un tipo del dominio con invarianti | `match-engine/src/main/java/org/tasktelemetry/matchsimulator/engine/team/Team.java` | record con costruttore compatto: copia difensiva delle liste, poi una guardia nominata per regola, con un messaggio che nomina la regola violata |
| una regola che risolve un'azione | `match-engine/src/main/java/org/tasktelemetry/matchsimulator/engine/resolution/PossessionActionResolver.java` | riceve stato e squadre e restituisce un esito con stato successivo e durata; il generatore arriva dall'esterno; i duelli sono metodi con nome di dominio che espongono la probabilità; legge solo il ruolo della zona |
| un ramo nuovo dell'azione d'attacco | `match-engine/src/main/java/org/tasktelemetry/matchsimulator/engine/resolution/shot/CrossResolver.java` | la regola che lo apre (`PossessionActionResolver`) decide solo se entrare, con una probabilità di configurazione; il ramo ha un proprio gruppo di parametri, espone le probabilità dei suoi duelli e riusa le regole esistenti (`ShotResolver`) invece di ricalcolarle |
| il dettaglio di un tipo di evento | `match-engine/src/main/java/org/tasktelemetry/matchsimulator/engine/event/EventDetail.java` | interfaccia sigillata con un record per dettaglio, guardie nel costruttore: chi aggiunge un dettaglio aggiunge un record, e il compilatore segnala gli `switch` da completare |
| un modello di probabilità (separato dal sorteggio) | `match-engine/src/main/java/org/tasktelemetry/matchsimulator/engine/resolution/shot/ExpectedGoalsModel.java` | calcolo puro, senza generatore: riceve i fattori e restituisce la probabilità, limitata e crescente nei suoi attributi; chi sorteggia (`ShotResolver`) la usa senza ricalcolarla |
| un gruppo di parametri della configurazione | `match-engine/src/main/java/org/tasktelemetry/matchsimulator/engine/config/ExpectedGoalsConfig.java` | record con mappe complete per ogni valore di enum, copiate e non modificabili, guardie su valori e soglie; si aggancia a `EngineConfig` come un suo componente |
| un calcolo ricavato dalla lista degli eventi | `match-app/src/main/java/org/tasktelemetry/matchsimulator/summary/MatchSummaryCalculator.java` | riceve solo la lista degli eventi, senza stato; attribuisce per la squadra che aveva la palla, non per il giocatore; legge il dettaglio con uno `switch` esaustivo sull'interfaccia sigillata, così un dettaglio nuovo ferma la compilazione |
| un test su eventi costruiti a mano | `match-app/src/test/java/org/tasktelemetry/matchsimulator/summary/MatchSummaryCalculatorTest.java` | costruisce pochi eventi con valori scelti e confronta il risultato esatto; include un caso che distingue un'attribuzione sbagliata (perdita di palla con il giocatore principale della squadra opposta) |
| un moltiplicatore di stile | `match-engine/src/main/java/org/tasktelemetry/matchsimulator/engine/config/PlayStyleModifiers.java` (con `StyleEffect.java`; applicazione in `resolution/Matchups.java`) | ogni stile parte da `StyleEffect.neutral()` e cambia solo i punti che tocca, con un nome; mappa completa su tutti gli stili; il valore si legge in un solo metodo che lo applica |
| un test di una regola probabilistica | `match-engine/src/test/java/org/tasktelemetry/matchsimulator/engine/resolution/PossessionActionResolverTest.java` | probabilità esatta per la regola, frequenza a seme fisso con ±1 punto per il percorso reale, e un caso che distingue un accoppiamento sbagliato di attributi |
| un test lento del banco | `match-app/src/test/java/org/tasktelemetry/matchsimulator/calibration/CalibrationAcceptanceTest.java` | tag `calibration`, cartella dei dati dalla proprietà di sistema e mai dal default; se fallisce, il messaggio allega il report completo |
| un parametro del banco (non del motore) | `match-app/src/main/java/org/tasktelemetry/matchsimulator/calibration/BenchConfig.java` | record nel banco, fuori da `EngineConfig`; `defaults()` dà un nome a ogni valore; guardie con il nome del parametro e il valore ricevuto |
| un parametro della configurazione del motore | `match-engine/src/main/java/org/tasktelemetry/matchsimulator/engine/config/EngineConfig.java` (con `DurationRange.java`) | record con guardie sui null; `defaults()` dà un nome a ogni valore prima di costruire |
