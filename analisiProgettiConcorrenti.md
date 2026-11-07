# Analisi dei progetti concorrenti

Scopo: raccogliere le idee riutilizzabili da due motori di simulazione open source, come base per la futura spec del nostro simulatore. Interessano le idee, non il funzionamento specifico dei due progetti.

Contesto: simulatore di partite di calcio di livello 2, cioè una catena di stati sul possesso che produce un racconto azione per azione. Niente rappresentazione grafica per ora.

## Progetti esaminati

| Progetto | Linguaggio | Licenza | Stato | Cosa ho letto |
|---|---|---|---|---|
| [OpenFootManager](https://github.com/openfootmanager/openfootmanager) | Rust (motore), TypeScript (interfaccia) | GPLv3 | Molto attivo (ultimo commit 2026-11-04) | `docs/MATCH_SIMULATION.md`, `docs/legacy/simulation.rst`, crate `engine` (eventi, risoluzione per zona), banco di taratura `sim-bench`, generazione del commento |
| [openengine](https://github.com/atas76/openengine) | Java | MIT | Sperimentale. L'autore ha spostato lo sviluppo in un repository privato | README, `docs/EventData_FormatNotes.md`, motore `mpn`, dati di esempio, modello tattico astratto |

**Licenze.** OpenFootManager è GPLv3: se copiassimo il suo codice, anche il nostro progetto dovrebbe diventare GPL. Le idee invece si possono riprendere liberamente. Openengine è MIT, ma il suo codice è comunque troppo sperimentale per essere riusato.

**Sintesi.** I due progetti partono da filosofie opposte.
- **OpenFootManager** è *guidato dalle regole*. Il programmatore scrive come si risolve ogni azione, usando gli attributi dei giocatori, poi tara i numeri sulle statistiche reali.
- **Openengine** è *guidato dai dati*. Le transizioni fra fasi di gioco si estraggono da partite reali annotate a mano, e il motore le ricampiona con meno logica possibile.

Per noi la base giusta è la prima. Dalla seconda prendiamo alcune idee mirate.

---

## 1. Modello dello stato e del campo

### 1.1 Granularità delle zone: partire grossi

OpenFootManager ha fatto il percorso inverso a quello che ci si aspetterebbe.
- La **prima versione** aveva 15 zone (tre corsie per cinque fasce), matrici di transizione fra zone e catene di eventi fini.
- La **versione attuale** usa 5 zone in fila lungo il campo: area di casa, difesa di casa, centrocampo, difesa ospite, area ospite. Ogni minuto risolve 1–3 azioni nella zona corrente. Un'azione riuscita avanza il pallone di una zona, una fallita lo fa arretrare o cambia il possesso.

Il motivo dichiarato del cambio: il modello a 5 zone è veloce (una partita in circa 2 ms) e soprattutto **più facile da tarare**, pur producendo statistiche e racconti realistici.

**Valutazione: valida.** Con 15 zone le probabilità da tarare crescono di un ordine di grandezza. Il limite delle 5 zone è che perdono il gioco sulle fasce, quindi cross e cambi di gioco. OpenFootManager lo compensa con una probabilità di cross che dipende dalla tattica, non dalla posizione.

**Per la spec:** modellare la zona come concetto proprio, in modo che il passaggio da 5 a più zone, per esempio aggiungendo le corsie, non cambi il resto del motore.

### 1.2 Lo stato non è solo la zona: anche la fase di gioco

Openengine non usa le zone del campo come stato, ma le **fasi di gioco**: costruzione, transizione, pressing, possesso, attacco, contropiede, palla inattiva (rimessa laterale, calcio d'angolo, punizione, rinvio dal fondo), tentativo di tiro, esito del tiro.

Esempio di sequenza estratta da una partita reale, con i tempi:

```
T: 03:50 Goalkeeper -> Buildup
T: 03:58 Buildup -> Pressing
T: 04:11 Pressing -> Transition
T: 04:18 Transition -> Possession
T: 04:22 Possession -> Attack
T: 04:35 Attack -> Corner
```

**Valutazione: molto valida per il racconto.** "Ripartenza in contropiede" o "lunga fase di possesso" raccontano la partita meglio di "il pallone passa dal centrocampo alla trequarti".

**Per la spec:** valutare uno stato composto da **possesso + zona + fase**. La fase può partire con pochi valori (gioco manovrato, contropiede, palla inattiva) e crescere in seguito.

### 1.3 Le fasi della partita come macchina a stati

OpenFootManager definisce in modo esplicito la sequenza delle fasi:

```
Prima del calcio d'inizio → Primo tempo → Intervallo → Secondo tempo → Fine
   (se pari e previsti) → Supplementari → Rigori → Conclusa
```

**Valutazione: valida e necessaria.** È la macchina a stati di livello partita, distinta da quella di livello azione.

---

## 2. Risoluzione delle azioni

### 2.1 Il duello come rapporto fra due valori

In OpenFootManager quasi ogni azione è un duello fra un attaccante e un difensore, risolto così:

```
probabilità di successo = valore attaccante / (valore attaccante + valore difensore)
```

Esempio: un passaggio con valore 70 contro un pressing con valore 50 riesce con probabilità 70 / 120 ≈ 58%.

Il nome tecnico è **modello di Bradley-Terry**.

**Valutazione: ottima.** Il risultato sta sempre fra 0 e 1 senza bisogno di correzioni. È simmetrico e intuitivo, e i modificatori (tattica, fattore campo, stanchezza) si applicano moltiplicando i due valori.

**Per la spec:** adottarlo come meccanismo standard dei duelli.

### 2.2 Esiti multipli e a cascata

La prima versione di OpenFootManager dava a ogni azione più esiti possibili:
- **passaggio e cross:** riuscito, sbagliato, intercettato, fuorigioco;
- **tiro:** murato (e il pallone resta all'attaccante oppure passa all'avversario), parato (e il portiere trattiene oppure concede il calcio d'angolo), palo (e il pallone resta in gioco oppure esce), fuori, gol.

Gli esiti si valutano in sequenza. Un passaggio prima riesce o fallisce in base all'abilità di chi passa. Se riesce, si controlla il fuorigioco in base al posizionamento di chi riceve. Se fallisce, si decide se è un'intercettazione o una palla persa.

**Valutazione: valida.** È proprio ciò che rende il racconto vario. Ogni esito determina lo stato successivo (chi ha palla, dove, in quale fase).

**Per la spec:** ogni azione si definisce con l'elenco dei suoi esiti e, per ciascun esito, con lo stato che produce.

### 2.3 Il tiro in due tempi, con gli expected goals

- **OpenFootManager** risolve il tiro in due passi: prima *va in porta?*, poi *il portiere para?*. Le basi sono tarate: per esempio il 46% dei tiri non murati va in porta, un valore che cade nella forbice reale della Premier League.
- **Openengine** assegna a ogni tiro un valore di **expected goals** (xG), cioè la probabilità che quel tiro diventi gol, e poi estrae: è gol se il numero casuale è sotto l'xG. Un rigore vale 0,79.

**Valutazione: valide entrambe, e si combinano bene.** L'xG è una misura standard e confrontabile con i dati reali. Inoltre serve al racconto: un tiro da 0,03 è "un tentativo da lontano", uno da 0,45 è "una grande occasione".

**Per la spec:** calcolare per ogni tiro un xG che dipende da zona, fase (un contropiede crea occasioni migliori) e tiratore. L'xG resta poi registrato nell'evento.

### 2.4 Chi è coinvolto nell'azione

OpenFootManager sceglie i giocatori in base alla zona: in costruzione passa un difensore, a centrocampo si sfidano due centrocampisti, sulla trequarti un attaccante affronta un difensore. La scelta è casuale, pesata, fra i giocatori del reparto giusto.

**Valutazione: valida e semplice.** Basta un ruolo per giocatore per avere un racconto con nomi credibili.

### 2.5 Eventi rari come probabilità di base

Openengine assegna i rigori con una probabilità fissa per ogni azione d'attacco (0,525%), calcolata sulla media delle partite. OpenFootManager deriva rigori, cartellini e infortuni a catena dal fallo: fallo, poi rigore se il fallo è in area, poi cartellino, poi seconda ammonizione e quindi espulsione.

**Valutazione:** la catena di OpenFootManager è più coerente, perché un rigore nasce sempre da un fallo raccontato. La probabilità fissa è una scorciatoia accettabile solo all'inizio.

---

## 3. Tempo

Due approcci:
- **OpenFootManager:** il tempo avanza a minuti, con 1–3 azioni per minuto.
- **Openengine:** ogni transizione ha una **durata in secondi** e l'orologio avanza della somma delle durate. Un calcio d'angolo "consuma" tempo, un'azione di contropiede ne consuma poco.

**Valutazione:** la durata per azione è più realistica e dà al racconto il minuto esatto ("23'14''"). Il possesso palla diventa inoltre misurabile in tempo reale invece che in conteggi di azioni.

**Per la spec:** ogni esito ha una durata, anche estratta da un intervallo. Il minuto dell'evento si ricava dall'orologio.

---

## 4. Squadre, tattiche e giocatori

### 4.1 Valori di reparto e valori individuali

OpenFootManager usa entrambi:
- **valori di reparto** (difesa, centrocampo, attacco, portiere), calcolati come media pesata degli attributi dei giocatori del reparto, per le contese generali di possesso;
- **attributi individuali** per i duelli puntuali (passaggio, tiro, contrasto).

Ha 19 attributi divisi in fisici, tecnici, mentali e da portiere.

**Per la spec:** partire con pochi attributi, una decina al massimo, e aggiungerne solo quando una regola li usa davvero. Un attributo che nessuna regola legge è rumore.

### 4.2 Stili di gioco come moltiplicatori

Ogni squadra ha uno stile (equilibrato, offensivo, difensivo, possesso, contropiede, pressing alto) che moltiplica i valori di certe fasi. Esempio: il contropiede ha ×1,18 in attacco e ×0,92 a centrocampo.

Nella prima versione di OpenFootManager lo stile cambiava invece **le probabilità di transizione**: con il possesso c'erano più passaggi, con il contropiede più cross.

### 4.3 Le "manopole" tattiche: un effetto, un punto, una direzione

Oltre allo stile, OpenFootManager ha nove manopole tattiche: costruzione corta o lunga, ampiezza, linea difensiva, marcatura, pressing, ritmo, compattezza, riaggressione, velocità di ripartenza. Seguono tre principi:
1. **ogni manopola agisce in un solo punto del motore e in una sola direzione**, così non si sommano effetti opposti nascosti;
2. **il valore neutro moltiplica per 1,0**: una squadra con le impostazioni di default gioca esattamente come senza manopole;
3. **ogni manopola ha un costo** oltre al beneficio. Per esempio il pressing aggressivo recupera più palloni ma stanca di più.

**Valutazione: principi eccellenti**, indipendenti dal numero di manopole. Rendono le tattiche testabili una alla volta.

**Per la spec:** adottare i tre principi anche se partiamo con una o due tattiche.

### 4.4 Altri modificatori

- **Fattore campo:** un unico moltiplicatore per la squadra di casa (default 1,08).
- **Tratti del giocatore** ("specialista del tiro", "testa calda"): bonus moltiplicativi in contesti precisi.
- **Condizione fisica:** cala minuto per minuto e riduce gli attributi effettivi. È ciò che rende sensate le sostituzioni.
- **Inferiorità numerica:** un giocatore espulso lascia un buco nel reparto e aumenta il campo da coprire in difesa. È tarato sulla letteratura: dopo un'espulsione al 30' la squadra in undici segna circa il doppio.

**Valutazione:** validi, ma nessuno è indispensabile nella prima versione. Fattore campo e condizione fisica sono i primi candidati.

---

## 5. Eventi e racconto

### 5.1 L'evento come unico prodotto del motore

L'evento di OpenFootManager contiene: minuto, tipo (circa 30 tipi), squadra, zona, giocatore principale, giocatore secondario (chi ha servito l'assist, chi ha subito il fallo) e un **dettaglio** opzionale.

Il **tabellino finale** (tiri, passaggi, possesso, voti) non viene calcolato a parte: si ricava dalla lista degli eventi. C'è una sola fonte di verità.

**Valutazione: ottima.** È esattamente il registro di eventi che una futura messa in scena leggerà.

### 5.2 Il dettaglio "veritiero"

Il dettaglio aggiunge sfumature per il commento: pericolosità del tiro (tentativo, discreta, grande occasione), qualità della parata (normale, difficile, prodigiosa), gravità del fallo (lieve, duro, imprudente), contesto del gol (vantaggio, pareggio, allungo, gol della bandiera).

La regola: **il dettaglio contiene solo valori che il motore ha davvero calcolato**. Il commento non può dire "parata prodigiosa" se la simulazione non l'ha decisa.

Openengine ha lo stesso principio come motto: *"don't fake output"*, cioè non inventare l'output.

**Valutazione: fondamentale.** È ciò che distingue un racconto coerente da frasi casuali appiccicate al risultato.

**Per la spec:** regola esplicita. Il racconto usa solo informazioni presenti nell'evento.

### 5.3 Racconto separato dal motore

In OpenFootManager il testo non sta nel motore ma in uno strato a parte:
- **modelli di frase** in file di traduzione, con più varianti per ogni tipo di evento e di dettaglio;
- **variante scelta in modo deterministico** a partire dall'evento (un'impronta di minuto, tipo e giocatore), così lo stesso evento produce sempre la stessa frase;
- **varianti di contesto**, per esempio "doppietta" e "tripletta", calcolate rileggendo gli eventi precedenti.

**Valutazione: valida.** Permette di cambiare lingua o stile del commento senza toccare il motore.

### 5.4 Eventi distinti per significati distinti

I rigori della lotteria finale sono tipi di evento diversi dai rigori in partita, così non finiscono per errore nei gol della partita o nelle statistiche del giocatore.

**Per la spec:** quando lo stesso gesto ha conseguenze diverse sul risultato, si usa un tipo di evento diverso.

---

## 6. Architettura

### 6.1 Motore isolato dal resto

Il motore di OpenFootManager **non conosce il dominio del gioco** (club, campionati, database). Ha i suoi tipi di ingresso, cioè giocatori, squadre e configurazione, e uno strato ponte li traduce. Si testa e si evolve da solo.

**Per la spec:** il nostro motore riceve due squadre, una configurazione e un seme. Restituisce gli eventi. Nient'altro.

### 6.2 Esecuzione a passi

OpenFootManager offre due modalità:
- **istantanea:** l'intera partita in una sola chiamata;
- **a passi:** un minuto alla volta. Fra un passo e l'altro si possono inviare comandi (sostituzione, cambio di modulo o di stile) e si può fotografare lo stato corrente (*snapshot*).

Con due motori separati i numeri hanno cominciato a divergere: oggi OpenFootManager usa quasi solo quello a passi.

**Per la spec:** un solo motore, costruito a passi. La partita istantanea è solo "esegui passi fino alla fine". I comandi in corsa possono arrivare dopo, ma il ciclo va pensato per accoglierli.

### 6.3 Riproducibilità

Generatore casuale con seme: stesso seme, stessa partita. OpenFootManager ha test dedicati.

### 6.4 Tutti i numeri in una configurazione

Tutte le probabilità e i moltiplicatori di base (fattore campo, precisione al tiro, probabilità di fallo, di cartellino, di rigore, di infortunio, recupero massimo) stanno in un unico oggetto di configurazione con valori di default.

**Per la spec:** stessa scelta. Niente numeri sparsi nel codice.

---

## 7. Taratura e verifica

È la parte più matura di OpenFootManager, e probabilmente la più preziosa.

- **Banco di prova:** uno strumento simula migliaia di partite e confronta le medie con forbici reali (gol a partita, tiri, percentuale in porta, cartellini, porta inviolata in casa e in trasferta).
- **Ogni obiettivo cita la sua fonte.** Esempi: risultati delle cinque grandi leghe dal dataset aperto *openfootball*; cartellini dal *CIES Football Observatory*; effetto delle espulsioni da studi pubblicati. Un obiettivo senza fonte si mostra solo per orientamento e **non si usa per tarare**: tarare il motore su un numero che nessuno sa giustificare trasforma un'ipotesi in comportamento.
- **Test che bloccano le frequenze:** test automatici verificano che i tassi tarati, per esempio quelli dei cartellini, restino nella forbice.
- **Esplorazione delle tattiche:** uno strumento misura l'effetto di ogni manopola su possesso, tiri e gol contro un avversario neutro.

**Valutazione: da adottare fin dall'inizio**, in forma ridotta. Il primo obiettivo della spec dovrebbe essere "la media di N partite fra squadre uguali cade nelle forbici X, Y, Z", con le fonti indicate.

---

## 8. L'approccio guidato dai dati (openengine)

L'idea centrale di openengine: **non scrivere le regole, estrarle dalle partite reali**.
- Ogni partita è annotata a mano come sequenza di transizioni fra fasi, con tempi e xG (esempio al § 1.2).
- Il motore costruisce per ogni squadra la raccolta di "cosa ha fatto in ogni fase". In partita, dalla fase corrente estrae a caso una delle transizioni reali di quella squadra.
- Dove i dati sono troppo pochi, interviene con regole esplicite che l'autore chiama **interventi**. Esempi: i rigori assegnati con la frequenza media e non quella di una sola partita; l'esito del tiro deciso dall'xG invece che copiato dal dato.

L'autore stesso documenta i limiti: con i dati di una sola partita, senza interventi, segnerebbe solo il Liverpool e metà dei gol arriverebbe su rigore.

**Valutazione:**
- **Come motore di base: no.** Servono molti dati annotati a mano, i campioni piccoli producono risultati distorti, e gli attributi dei giocatori e le tattiche non entrano nel modello.
- **Come idee da recuperare: sì.**
  - La **notazione testuale delle sequenze** (`squadra: tempo FaseA -> FaseB; xG = 0.03`) è utile come formato leggibile per i casi di test e per il debug: una partita simulata si può stampare e confrontare in quel formato.
  - Le **frequenze reali delle transizioni fra fasi** sono un'ottima fonte di taratura per le nostre probabilità.
  - La distinzione fra **dati e interventi** ci ricorda di tenere separate le probabilità tarate dalle regole scritte a mano.

### Coerenza fra ingresso e uscita

Una riflessione del README di openengine: il livello di dettaglio dell'ingresso e quello dell'uscita devono essere coerenti fra loro. Se in ingresso ci sono solo forze di squadra, un racconto che nomina dribbling individuali è inventato. Se in ingresso ci sono attributi individuali, l'uscita deve rifletterli.

**Per la spec:** ogni elemento del racconto deve avere una causa nell'ingresso. È la stessa regola del § 5.2 vista dal lato opposto.

---

## 9. Lezioni dalla storia dei due progetti

- **OpenFootManager** è partito con un modello fine (15 zone, matrici) ed è tornato a un modello grosso, più facile da tarare. Il dettaglio si aggiunge dopo, dove la taratura dimostra che serve.
- **Openengine** è stato ricominciato da capo molte volte in oltre vent'anni. L'autore attribuisce i fallimenti all'eccesso di ambizione e all'indecisione fra approcci diversi.
- In entrambi la cosa che ha funzionato è stata **un nucleo piccolo, misurabile e tarato**, esteso per gradi.

---

## 10. Proposta di base per la spec

Scelte che suggerisco di portare nella spec come punto di partenza:

1. **Stato** = squadra in possesso + zona (5 zone lungo il campo, estendibili) + fase (gioco manovrato, contropiede, palla inattiva).
2. **Azioni** con esiti multipli. Ogni esito definisce lo stato successivo e una durata in secondi.
3. **Duelli** risolti con il rapporto fra due valori (Bradley-Terry); modificatori moltiplicativi.
4. **Tiro** con un xG calcolato da zona, fase e tiratore; gol se l'estrazione cade sotto l'xG.
5. **Falli** come catena: fallo, poi eventualmente rigore, cartellino ed espulsione.
6. **Fasi della partita** come macchina a stati: primo tempo, intervallo, secondo tempo, fine. Supplementari e rigori più avanti.
7. **Evento** come unico prodotto: minuto e secondo, tipo, squadra, zona, giocatori coinvolti, dettaglio veritiero (xG, qualità, contesto).
8. **Tabellino** ricavato dagli eventi.
9. **Racconto** in uno strato separato, con modelli di frase e varianti deterministiche.
10. **Motore isolato**: riceve squadre, configurazione e seme; esegue a passi; restituisce eventi.
11. **Configurazione unica** di tutte le probabilità di base.
12. **Banco di taratura** fin dalla prima versione, con obiettivi dotati di fonte.

Fuori dalla prima versione: sostituzioni e comandi in corsa, condizione fisica, tratti, manopole tattiche oltre a una o due, supplementari e rigori, intelligenza dell'allenatore avversario.

## 11. Decisioni già prese

- **Riferimento per la taratura:** i cinque grandi campionati insieme (Premier League, Liga, Bundesliga, Serie A, Ligue 1), stagioni escluse quelle a porte chiuse. Gli obiettivi sui risultati si ricalcolano da [openfootball](https://github.com/openfootball/football.json) (CC0), senza copiare i file di OpenFootManager. Le misure del nostro motore si confrontano su un campionato simulato con squadre di forza diversa, non su una sola partita ripetuta. I dati sono in [data/calibration](data/calibration/README.md).
- **Statistiche di gioco:** tiri, falli, angoli e cartellini da football-data.co.uk (stesse 40 stagioni); possesso, azioni, passaggi e xG da StatsBomb Open Data (stagione 2015-16, quattro campionati, uso non commerciale e senza ridistribuire i dati grezzi).
- **Il progetto resta non commerciale:** le forbici di StatsBomb sono utilizzabili.
- **Perimetro della prima versione** (deciso il 2026-11-07):
  - campo a 5 zone in fila lungo il campo;
  - squadre di 11 giocatori con nome, ruolo e pochi attributi (6-8), più la panchina;
  - uno stile di gioco per squadra, scelto fra pochi;
  - eventi strutturati più racconto in italiano, con le frasi in un file di risorse;
  - oltre a passaggi, contrasti, tiri e gol: falli e cartellini, palle inattive (angoli, punizioni, rigori, rimesse dal fondo), fuorigioco, infortuni con sostituzione;
  - partita di 90 minuti più recupero, senza supplementari né rigori;
  - programma a riga di comando: legge due squadre da file e un seme opzionale, stampa cronaca e tabellino; il motore è una libreria separata;
  - banco di taratura ridotto: simula un campionato e confronta le medie con le forbici di `data/calibration`.

## 12. Domande aperte per la spec

1. Le 5 zone lungo il campo bastano, o vogliamo da subito le corsie laterali (per esempio 5×3) per raccontare cross e gioco sulle fasce?
2. Quanti e quali attributi per giocatore nella prima versione?
3. Quante tattiche nella prima versione: nessuna, uno stile di gioco, oppure una o due manopole?
4. Il racconto testuale fa parte della prima versione, o la prima versione produce solo eventi strutturati?
5. In che lingua è il racconto, e serve più di una lingua?
