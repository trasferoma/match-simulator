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

### Dove nascono i falli?
- **Oggi:** solo quando un giocatore perde un dribbling, cioè nel duello in cui conta il contrasto (`tackling`).
  Con il fallo possibile su ogni duello perso il fattore campo si annacquava (vittorie in trasferta fuori forbice).
  Così anche i rigori nascono solo da un dribbling perso in area.
- **Alternative:** fallo anche sull'intercetto di un passaggio, nel contropiede o nel duello aereo, con probabilità
  proprie.
- **Dal:** 2026-11-08.

### Reparto svuotato con un solo centrocampista
- **Oggi:** se un reparto resta vuoto e il centrocampo, che dovrebbe dare un uomo, ne ha uno solo, lo dà il reparto
  più numeroso (di solito l'attacco). Capita solo con formazioni insolite.
- **Alternative:** lettura letterale della SPEC: il centrocampista scende e il centrocampo si riempie dall'attacco
  (catena).
- **Dal:** 2026-11-08.

### Secondo giallo nel conteggio dei rossi
- **Oggi:** il secondo giallo conta sia fra i gialli sia fra i rossi del tabellino. I rossi simulati sono troppi
  rispetto ai campionati reali (0,50 a partita contro 0,11–0,29), quasi tutti da secondo giallo: un ammonito continua
  a fare falli come prima.
- **Da decidere:** come la fonte reale (football-data) conta il secondo giallo; se un ammonito deve rischiare di meno.
- **Dal:** 2026-11-08.

### Cross: probabilità di riuscita
- **Oggi:** il cross riesce secondo il passaggio del crossatore contro una difficoltà fissa: con un buon passatore
  circa 1 su 4.
- **Da decidere:** se il valore va bene o se il cross deve dipendere anche da chi difende.
- **Dal:** 2026-11-08.

### Chi crossa e chi batte l'angolo
- **Oggi:** sempre un centrocampista (così crossatore e chi colpisce di testa non coincidono).
- **Alternative:** anche difensori laterali o attaccanti.
- **Dal:** 2026-11-08.

### Cross e colpo di testa a cavallo della scadenza
- **Oggi:** cross e colpo di testa sono due azioni. Se il tempo scade dopo un cross riuscito, il colpo di testa non si
  gioca.
- **Alternative:** trattarli come un'unica azione che si conclude anche a tempo scaduto.
- **Dal:** 2026-11-08.

### Rigore parato in angolo
- **Oggi:** un rigore parato può finire in calcio d'angolo, come ogni tiro parato.
- **Alternative:** dopo il rigore parato il portiere trattiene sempre.
- **Dal:** 2026-11-08.

### Durata della punizione diretta
- **Oggi:** la punizione diretta spende la durata della punizione più quella del tiro.
- **Da decidere:** la SPEC dà entrambe le durate senza dire come si combinano.
- **Dal:** 2026-11-08.

### Quando può esserci fuorigioco
- **Oggi:** solo su un passaggio riuscito che arriva in trequarti o nell'area avversaria. Dribbling, contropiede e
  cross non danno mai fuorigioco. Il fuorigioco è un compagno di chi passa (mai chi passa) scelto come gli altri giocatori, non il destinatario
  di un passaggio nominato. Il banco non ha un obiettivo reale per i fuorigioco: oggi sono circa 2 a partita,
  probabilmente pochi.
- **Alternative:** fuorigioco anche sul cross e sul lancio del contropiede.
- **Dal:** 2026-11-08.

### Il fuorigioco non dipende dalla difesa
- **Oggi:** la probabilità dipende solo dal `positioning` dell'attaccante che riceve (come dice la SPEC). Una difesa
  ben piazzata non fa cadere più spesso gli avversari in fuorigioco.
- **Alternative:**
  1. duello di posizione: `positioning` dell'attaccante contro quello del difensore, come gli altri duelli (proposta
     della sessione: costo piccolo, una sola regola);
  2. trappola del fuorigioco come tattica della squadra («linea alta»), con un costo quando fallisce (attaccante solo
     davanti al portiere): più realistica, ma è una tattica nuova senza dati per tararla;
  3. il duello subito, la tattica in una versione successiva.
- **Dal:** 2026-11-08.

### Calcio d'inizio
- **Oggi:** la squadra di casa nel primo tempo, l'ospite nel secondo.
- **Alternative:** sorteggio con il seme della partita.
- **Dal:** 2026-11-08.

### Infortuni: quanti, a chi e quando
- **Oggi:** a ogni azione ogni squadra ha una piccola probabilità di infortunio (circa uno a partita in tutto); il
  giocatore è scelto a caso fra quelli in campo, portiere compreso; nessun infortunio dopo il 45' di ciascun tempo né
  mentre un cross è in volo.
- **Da decidere:** la SPEC li dichiara valori di orientamento e non dice nulla sulla finestra di tempo né su chi può
  infortunarsi.
- **Dal:** 2026-11-08.

### Chi entra al posto di un infortunato
- **Oggi:** «stesso ruolo» è il ruolo che il giocatore ha in campo in quel momento (può essere stato spostato di
  reparto), non quello di origine. Se esce il portiere ed entra un giocatore di movimento, questo tiene il suo ruolo e
  in porta va il miglior `goalkeeping` fra chi è in campo, anche fra chi è appena entrato.
- **Alternativa:** stesso ruolo di origine.
- **Dal:** 2026-11-08.

### Possesso durante infortuni e cambi
- **Oggi:** il tempo di infortuni e cambi non va nel possesso di nessuna squadra (palla ferma).
- **Alternativa:** attribuirlo alla squadra che aveva la palla, come dice la SPEC alla lettera; serve un'informazione
  in più nell'evento.
- **Dal:** 2026-11-08.

### Inferiorità numerica a squadra completa
- **Oggi:** la penalità per uomo in meno guarda i reparti, non il numero di giocatori. Se il portiere si infortuna, entra
  un centrocampista e in porta va un difensore, la difesa risulta con un uomo in meno e viene penalizzata anche se la
  squadra è ancora in 11 (il centrocampo con un uomo in più non riceve nulla). Caso raro.
- **Alternativa:** penalità solo quando la squadra ha davvero meno di 11 giocatori.
- **Dal:** 2026-11-08.

### Fine del tempo con cambio o infortunio di mezzo
- **Oggi:** il cambio dopo un'espulsione nell'azione che supera la scadenza aggiunge la sua durata e può allungare il
  tempo oltre il limite scritto nella SPEC. Un rigore già concesso si batte anche se a far scadere il tempo è un
  infortunio successivo; angolo e punizione no.
- **Da decidere:** se il limite della durata dei tempi deve contare anche la durata del cambio.
- **Dal:** 2026-11-08.

### Rosso diretto e secondo giallo
- **Oggi:** il conteggio dei rossi comprende i secondi gialli, e il controllo «ogni evento di contorno compare nel
  campionato del banco» passa anche se il rosso diretto non comparisse mai.
- **Da decidere:** se «rosso» nei criteri di presenza vale anche per il secondo giallo o va contato a parte.
- **Dal:** 2026-11-08.

## SPEC v1: stili di gioco

### Dove agiscono attacco e difesa dello stile
- **Oggi:** i moltiplicatori di attacco e difesa dello stile valgono in tutte le zone fuori dal centrocampo, anche
  nell'area propria (lo stile difensivo rende più difficile anche uscire dalla propria area).
- **Alternative:** solo nella metà campo avversaria per l'attacco e nella propria per la difesa.
- **Dal:** 2026-11-08.

### Stili e fattore campo su cross e duello aereo
- **Oggi:** stili e fattore campo agiscono solo sui duelli del gioco aperto e del contropiede, non su cross, duello
  aereo, tiro e portiere.
- **Alternative:** estenderli anche a quei duelli.
- **Dal:** 2026-11-08.

## SPEC v1: taratura

### Peso del talento di chi tira e del portiere
- **Oggi:** per portare i pareggi nella forbice reale (ora 22,9% contro un minimo di 22,1%, margine sottile) la
  bravura di chi tira e del portiere pesa poco: un tiratore 20 punti sopra il neutro moltiplica le quote di gol per
  circa 1,13 (prima 1,8). La forza della squadra conta ancora attraverso i duelli, che decidono quanti tiri si fanno.
  Compromesso accettato dall'utente il 2026-11-09.
- **Da decidere:** se nella versione successiva il talento individuale deve pesare di più; in quel caso pareggi e
  possesso vanno ritarati insieme (il giro palla è il candidato per il possesso).
- **Dal:** 2026-11-09.

### Pochi calci d'angolo
- **Oggi:** circa 2,5 angoli a partita contro circa 10 reali. L'angolo nasce solo da parate, tiri murati e respinte
  dopo un cross.
- **Alternative:** aggiungere una sorgente (per esempio cross o passaggio deviato in angolo).
- **Dal:** 2026-11-08.

## SPEC v1: file delle squadre e messaggi

### Campo indicato per gli errori di formazione
- **Oggi:** due portieri, un reparto senza titolari o un nome ripetuto danno come campo «starters, bench» con il
  messaggio della regola violata, in italiano (per esempio «la squadra deve avere esattamente un portiere tra i titolari, ne ha 2»). Per un attributo
  fuori scala il campo è il giocatore (`starters[4].attributes`) e l'attributo è nel messaggio; per un attributo
  mancante il campo è completo (`starters[4].attributes.pace`).
- **Alternativa:** indicare caso per caso `starters` o `bench`; ora che il motore dice quale regola è violata, con i suoi
  dati, il lettore potrebbe farlo senza ripetere le regole.
- **Dal:** 2026-11-09.

### Campi sconosciuti nel file squadra
- **Oggi:** un campo in più (per esempio `"stile"` scritto in italiano) viene ignorato in silenzio; la panchina è
  obbligatoria ma può essere vuota.
- **Alternativa:** rifiutare i campi sconosciuti, così un nome di campo sbagliato viene segnalato subito.
- **Dal:** 2026-11-09.

## SPEC v1: cronaca

### Nomi delle squadre nella cronaca
- **Oggi:** le frasi nominano solo i giocatori («porta in vantaggio la sua squadra»), perché gli eventi non portano i
  nomi delle squadre; anche la sospensione non dice quale squadra è responsabile.
- **Alternativa:** passare al racconto i nomi delle due squadre, che sono un dato d'ingresso e non un'invenzione.
- **Dal:** 2026-11-09.

### Assist sui gol nati da un passaggio
- **Oggi:** il giocatore secondario di un gol è solo il crossatore; un gol nato da un passaggio non ha assist. La SPEC
  elenca l'assist fra i possibili giocatori secondari, ma non lo impone. Se il motore lo aggiungesse, alla cronaca
  servirebbero frasi nuove (oggi una combinazione senza frase ferma il racconto con un errore).
- **Alternativa:** il motore mette come assist chi ha fatto l'ultimo passaggio riuscito prima del tiro.
- **Dal:** 2026-11-09.

### Ripresa del gioco fra le azioni che preparano un tiro
- **Oggi:** la cronaca predefinita mostra fino a due eventi della stessa squadra prima di un tiro; il calcio d'inizio
  dopo un gol può essere fra questi («Si riparte dal centrocampo»), come dice la SPEC alla lettera.
- **Alternativa:** escluderlo, perché è una ripresa e non un'azione.
- **Dal:** 2026-11-09.

### Quale squadra «prepara» un tiro
- **Oggi:** per intercetti, contrasti, respinte e falli conta la squadra di chi agisce, non quella che aveva la palla.
  Così un intercetto che apre un contropiede finito in gol compare nella cronaca, mentre un fallo subito prima di una
  punizione no.
- **Alternativa:** contare la squadra che aveva la palla.
- **Dal:** 2026-11-09.

### Minuto nella scelta della variante
- **Oggi:** la variante della frase dipende dal minuto intero: il 45+2 del secondo tempo e il 47º minuto danno lo stesso
  valore; la riga d'apertura del secondo tempo mostra «45'» dopo le righe «45+2'» del primo.
- **Da decidere:** se va bene così.
- **Dal:** 2026-11-09.

## SPEC v1: riga di comando

### Possesso che somma a 101%
- **Oggi:** il tabellino arrotonda il possesso di ciascuna squadra per conto suo: con 50,5% e 49,5% stampa 51% e
  50%.
- **Alternativa:** arrotondare la casa e stampare per l'ospite 100 meno la casa.
- **Dal:** 2026-11-09.

### Forma `--seed=42`
- **Oggi:** è accettata solo la forma in due argomenti, `--seed 42`; `--seed=42` è un'opzione sconosciuta, quindi
  errore d'uso.
- **Alternativa:** accettare anche la forma con il segno uguale.
- **Dal:** 2026-11-09.

### Percorso non valido per il sistema
- **Oggi:** un percorso che il sistema non accetta (per esempio con `|` su Windows) è un errore d'uso: messaggio,
  riga d'uso e codice 2.
- **Alternativa:** trattarlo come file squadra non leggibile: messaggio con il file e codice 1, senza riga d'uso.
- **Dal:** 2026-11-09.

### Passaggio verso un reparto senza compagni
- **Oggi:** un passaggio non nomina chi lo riceve. Così una squadra con un solo attaccante può passare dalla
  trequarti verso l'area anche se in area non c'è nessun altro compagno; solo il fuorigioco, su quel passaggio, non
  può avvenire.
- **Alternativa:** un passaggio verso un reparto dove non c'è nessun compagno non è possibile (o diventa un
  avanzamento palla al piede).
- **Dal:** 2026-11-09.

### Errori del banco di taratura
- **Oggi:** il banco, avviato da una cartella diversa dalla radice senza indicare i dati, si ferma con un errore
  tecnico in inglese e lo stack trace. Per decisione dell'utente il banco è uno strumento interno e non segue la regola
  dei messaggi in italiano.
- **Alternativa:** messaggio in italiano senza stack trace, come per la riga di comando.
- **Dal:** 2026-11-09.

### Doppietta con lo stesso file per casa e ospite
- **Oggi:** la cronaca conta doppiette e triplette confrontando il giocatore, non la squadra. Con lo stesso file per
  casa e ospite due giocatori identici potrebbero sommare i gol (sospetto della revisione finale, non dimostrato).
- **Alternativa:** confrontare anche la squadra dell'evento.
- **Dal:** 2026-11-09.
