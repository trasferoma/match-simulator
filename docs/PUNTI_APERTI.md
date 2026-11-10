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

## SPEC v1: regole di gioco

### Cross: probabilità di riuscita
- **Oggi:** il cross riesce secondo il passaggio del crossatore contro una difficoltà fissa: con un buon passatore
  circa 1 su 4.
- **Deciso (2026-11-10):** il cross dipenderà anche da chi difende; correzione da svolgere dopo la revisione dei
  punti aperti, poi la voce si toglie.
- **Dal:** 2026-11-08.

### Chi crossa e chi batte l'angolo
- **Oggi:** sempre un centrocampista (così crossatore e chi colpisce di testa non coincidono).
- **Deciso (2026-11-10):** crosseranno anche difensori e attaccanti, mai lo stesso giocatore che colpisce di testa;
  correzione da svolgere dopo la revisione dei punti aperti, poi la voce si toglie.
- **Dal:** 2026-11-08.

### Cross e colpo di testa a cavallo della scadenza
- **Oggi:** cross e colpo di testa sono due azioni. Se il tempo scade dopo un cross riuscito, il colpo di testa non si
  gioca.
- **Deciso (2026-11-10):** diventano un'unica azione che si conclude anche a tempo scaduto; correzione da svolgere
  dopo la revisione dei punti aperti, poi la voce si toglie.
- **Dal:** 2026-11-08.

### Il fuorigioco non dipende dalla difesa
- **Oggi:** la probabilità dipende solo dal `positioning` dell'attaccante che riceve (come dice la SPEC). Una difesa
  ben piazzata non fa cadere più spesso gli avversari in fuorigioco.
- **Deciso (2026-11-10):** duello di posizione (`positioning` dell'attaccante contro quello del difensore), da
  svolgere dopo la revisione dei punti aperti, poi la voce si toglie; la linea alta è fra i rinvii della SPEC.
- **Dal:** 2026-11-08.

### Inferiorità numerica a squadra completa
- **Oggi:** la penalità per uomo in meno guarda i reparti, non il numero di giocatori. Se il portiere si infortuna, entra
  un centrocampista e in porta va un difensore, la difesa risulta con un uomo in meno e viene penalizzata anche se la
  squadra è ancora in 11 (il centrocampo con un uomo in più non riceve nulla). Caso raro.
- **Deciso (2026-11-10):** penalità solo quando la squadra ha davvero meno di 11 giocatori, e un calo di rendimento
  per chi gioca fuori dal suo ruolo naturale; correzione da svolgere dopo la revisione dei punti aperti, poi la voce si
  toglie.
- **Dal:** 2026-11-08.

### Rosso diretto e secondo giallo
- **Oggi:** il conteggio dei rossi comprende i secondi gialli, e il controllo «ogni evento di contorno compare nel
  campionato del banco» passa anche se il rosso diretto non comparisse mai.
- **Deciso (2026-11-10):** nel controllo di presenza si contano a parte; la SPEC è aggiornata, il test si
  aggiorna dopo la revisione dei punti aperti, poi la voce si toglie.
- **Dal:** 2026-11-08.

## SPEC v1: stili di gioco

### Stili e fattore campo su cross e duello aereo
- **Oggi:** stili e fattore campo agiscono solo sui duelli del gioco aperto e del contropiede, non su cross, duello
  aereo, tiro e portiere.
- **Deciso (2026-11-10):** estenderli anche a quei duelli (e al nuovo duello del cross), con nuova taratura;
  correzione da svolgere dopo la revisione dei punti aperti, poi la voce si toglie.
- **Dal:** 2026-11-08.

## SPEC v1: taratura

### Pochi calci d'angolo
- **Oggi:** circa 2,5 angoli a partita contro circa 10 reali. L'angolo nasce solo da parate, tiri murati e respinte
  dopo un cross.
- **Deciso (2026-11-10):** il cross fallito può finire in angolo, deviato dalla difesa; correzione da svolgere dopo
  la revisione dei punti aperti, poi la voce si toglie.
- **Dal:** 2026-11-08.

## SPEC v1: file delle squadre e messaggi

### Campo indicato per gli errori di formazione
- **Oggi:** due portieri, un reparto senza titolari o un nome ripetuto danno come campo «starters, bench» con il
  messaggio della regola violata, in italiano (per esempio «la squadra deve avere esattamente un portiere tra i titolari, ne ha 2»). Per un attributo
  fuori scala il campo è il giocatore (`starters[4].attributes`) e l'attributo è nel messaggio; per un attributo
  mancante il campo è completo (`starters[4].attributes.pace`).
- **Deciso (2026-11-10):** ogni regola indica il suo campo (`starters` o `bench`, entrambi solo se la regola li
  riguarda tutti e due); correzione da svolgere dopo la revisione dei punti aperti, poi la voce si toglie.
- **Dal:** 2026-11-09.

### Campi sconosciuti nel file squadra
- **Oggi:** un campo in più (per esempio `"stile"` scritto in italiano) viene ignorato in silenzio; la panchina è
  obbligatoria ma può essere vuota.
- **Deciso (2026-11-10):** i campi sconosciuti si rifiutano con un errore che nomina file e campo; correzione da
  svolgere dopo la revisione dei punti aperti, poi la voce si toglie.
- **Dal:** 2026-11-09.

## SPEC v1: cronaca

### Nomi delle squadre nella cronaca
- **Oggi:** le frasi nominano solo i giocatori («porta in vantaggio la sua squadra»), perché gli eventi non portano i
  nomi delle squadre; anche la sospensione non dice quale squadra è responsabile.
- **Deciso (2026-11-10):** il racconto riceve i nomi delle due squadre e li usa; correzione da svolgere dopo la
  revisione dei punti aperti, poi la voce si toglie.
- **Dal:** 2026-11-09.

### Assist sui gol nati da un passaggio
- **Oggi:** il giocatore secondario di un gol è solo il crossatore; un gol nato da un passaggio non ha assist. La SPEC
  elenca l'assist fra i possibili giocatori secondari, ma non lo impone. Se il motore lo aggiungesse, alla cronaca
  servirebbero frasi nuove (oggi una combinazione senza frase ferma il racconto con un errore).
- **Deciso (2026-11-10):** il motore mette come assist chi ha fatto l'ultimo passaggio riuscito prima del tiro;
  correzione da svolgere dopo la revisione dei punti aperti, poi la voce si toglie.
- **Dal:** 2026-11-09.

### Ripresa del gioco fra le azioni che preparano un tiro
- **Oggi:** la cronaca predefinita mostra fino a due eventi della stessa squadra prima di un tiro; il calcio d'inizio
  dopo un gol può essere fra questi («Si riparte dal centrocampo»), come dice la SPEC alla lettera.
- **Deciso (2026-11-10):** escluderlo, perché è una ripresa e non un'azione; correzione da svolgere dopo la
  revisione dei punti aperti, poi la voce si toglie.
- **Dal:** 2026-11-09.

## SPEC v1: riga di comando

### Passaggio verso un reparto senza compagni
- **Oggi:** un passaggio non nomina chi lo riceve. Così una squadra con un solo attaccante può passare dalla
  trequarti verso l'area anche se in area non c'è nessun altro compagno; solo il fuorigioco, su quel passaggio, non
  può avvenire.
- **Deciso (2026-11-10):** il passaggio non si tenta; si tenta il dribbling solo se c'è una possibilità concreta di
  segnare (criterio da definire nella correzione), altrimenti la squadra resta nella sua zona e rigioca l'azione;
  correzione da svolgere dopo la revisione dei punti aperti, poi la voce si toglie.
- **Dal:** 2026-11-09.

### Doppietta con lo stesso file per casa e ospite
- **Oggi:** la cronaca conta doppiette e triplette confrontando il giocatore, non la squadra. Con lo stesso file per
  casa e ospite due giocatori identici potrebbero sommare i gol (sospetto della revisione finale, non dimostrato).
- **Deciso (2026-11-10):** test che riproduce il caso e, se il difetto c'è, confronto anche della squadra
  dell'evento; correzione da svolgere dopo la revisione dei punti aperti, poi la voce si toglie.
- **Dal:** 2026-11-09.
