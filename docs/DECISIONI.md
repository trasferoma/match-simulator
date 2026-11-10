# Decisioni di progetto

Principi che valgono per **ogni** funzionalità, presente o futura. Il comportamento di una singola funzionalità
(valori, spareggi, validazioni, criteri di accettazione) sta nella sua specifica, non qui.

- **Domanda d'ingresso: la prossima specifica, o la prossima modifica in un'altra parte del progetto, dovrà
  rispettarla?** Se no, non entra: resta nella specifica o nel codice. Nel dubbio, non entra.
- **Conflitti.** Sui principi vince questo registro: una specifica che ne contraddice uno si corregge, oppure prima si
  cambia la decisione. Sui dettagli di una funzionalità vince la specifica: qui non ci sono.
- **Ogni riga rimanda alla sua fonte** (una specifica, una revisione, il codice esistente) e non ne ricopia il
  dettaglio.
- **Cosa non si scrive qui:** valori di taratura o di configurazione, numeri di criteri di accettazione o di fasi di
  un piano, percorsi che cambiano a ogni refactoring. Cambiano spesso e renderebbero falso il registro.
- **Formato:** `- **D<n>. <decisione in una frase>.** Perché: <motivo>. Fonte: <documento e sezione, o classe>.`
  I numeri non si riusano. Il «Perché» si scrive quando aiuta a giudicare un caso nuovo; si omette per le
  convenzioni che si seguono e basta (linguaggio, librerie, nomi), se la fonte non dà un motivo.
- **Chi aggiorna e quando:** `CLAUDE.md`, sezione «Documenti».

Nelle fonti, «SPEC» è la specifica della prima versione, `C:\build\git\spec\match-misulator\spec-motore-partita-v1.md`.

## Decisioni

### Modello di simulazione

- **D1. Catena di stati sul possesso ("livello 2"), non simulazione spaziale ad agenti.** Perché: produce una
  partita raccontabile senza il costo di simulare posizioni e movimenti. Fonte: SPEC, Obiettivo; analisi §1.
- **D2. Stato dell'azione = squadra in possesso + zona + fase di gioco.** Perché: la sola zona non distingue un
  contropiede da una manovra né una palla inattiva. Fonte: SPEC, "Stato e azioni"; analisi §1.2.
- **D3. Campo a zone in fila; le regole leggono il ruolo della zona rispetto a chi ha la palla, mai l'indice.**
  Perché: cambiare il numero di zone o aggiungere corsie cambia solo la mappa zone → ruolo, non le regole. Fonte:
  SPEC, "Stato e azioni"; analisi §1.1.
- **D4. Duelli con `p = attacco / (attacco + difesa)` (Bradley-Terry), modificatori moltiplicativi.** Perché: un
  solo meccanismo per tutti i confronti, e un modificatore neutro vale 1. Fonte: analisi §2.1.
- **D5. Ogni azione ha esiti multipli; ogni esito porta lo stato successivo e una durata in secondi.** Perché: il
  tempo avanza con le azioni, e la durata serve sia al recupero sia al possesso. Fonte: analisi §2.2 e §3.
- **D6. Ogni tiro ha un xG; è gol se l'estrazione cade sotto la probabilità di gol.** Perché: lega i gol a una
  misura confrontabile con i dati reali. Fonte: analisi §2.3.
- **D7. Fallo a catena:** fallo con gravità → punizione o rigore → cartellino → espulsione. Perché: un rigore o un
  cartellino nasce sempre da un fallo che il racconto può nominare, mai da una probabilità fissa slegata dal gioco.
  Fonte: analisi §2.5.
- **D24. Il gioco sulle fasce è una probabilità legata alla tattica, non una posizione** (cross dalla trequarti,
  duello aereo). Perché: dà cross e colpi di testa senza corsie nel campo. Fonte: SPEC, "Stato e azioni"; analisi §1.1.
- **D25. Ogni attributo dei giocatori è letto da almeno una regola;** nessun attributo decorativo. Perché: un
  attributo che nessuna regola legge confonde chi scrive le squadre e non cambia la partita. Fonte: SPEC, "Ingresso e
  motore".
- **D56. Un giocatore si identifica sempre insieme alla sua squadra:** ogni confronto fra giocatori che possono
  appartenere a squadre diverse confronta anche la squadra. Perché: un giocatore è un valore (nome, ruolo,
  attributi), e con lo stesso file per casa e ospite due giocatori di squadre diverse risultano uguali; è successo due
  volte, con una doppietta sommata fra le due squadre e un assist cancellato dall'infortunio di un avversario
  omonimo. Fonte: SPEC, "Evento, tabellino, racconto" (doppietta) e «Assist»; revisione finale del piano
  (decisione dell'utente del 2026-11-10).

### Architettura

- **D8. Motore isolato:** riceve squadre, configurazione e seme, esegue a passi, restituisce solo eventi; niente
  I/O, e non conosce file, racconto, riga di comando né taratura. Perché: il motore si prova e si tara da solo, e gli
  strati sopra cambiano senza toccarlo. Fonte: SPEC, "Ingresso e motore"; analisi §6.1-6.2.
- **D9. Gli eventi sono l'unica fonte di verità:** tabellino e statistiche si calcolano solo dalla lista degli
  eventi. Perché: due calcoli separati prima o poi divergono. Fonte: analisi §5.1.
- **D10. Il racconto non inventa nulla:** usa solo informazioni presenti nell'evento; se serve una sfumatura, la
  calcola il motore e la mette nel dettaglio dell'evento. Perché: una frase che descrive ciò che il motore non ha
  deciso (una «parata prodigiosa» senza una parata difficile) rende il racconto incoerente con la partita. Fonte:
  analisi §5.2-5.3.
- **D11. Un gesto con conseguenze diverse ha un tipo di evento diverso.** Perché: chi conta gli eventi non deve
  distinguerli dal contesto; per esempio un rigore della lotteria finale non finisce nei gol della partita. Fonte:
  analisi §5.4.
- **D12. Riproducibilità:** stesso seme, stesse squadre, stessa configurazione → stessa sequenza di eventi, anche in
  un'altra JVM. L'unica sorgente di casualità è il generatore con seme della partita, e nessun risultato dipende
  dall'ordine di iterazione di strutture a ordine non garantito. Elenco dei divieti: SPEC, Vincoli. Perché: una
  partita che si rigioca uguale si può confrontare, correggere e tarare; senza, un difetto visto una volta non si
  ritrova. Fonte: analisi §6.3.
- **D13. Tutti i numeri in un'unica configurazione** con valori di default; nessuna costante di taratura nelle
  regole. Perché: la taratura tocca un solo posto. Fonte: analisi §6.4.
- **D14. Ogni tattica ha un effetto in un solo punto e in una sola direzione; lo stile neutro moltiplica per 1;
  ogni stile ha un costo oltre al beneficio.** Perché: non si sommano effetti opposti nascosti, una squadra con lo
  stile neutro gioca come senza stile, e ogni tattica si prova da sola. Fonte: analisi §4.3.
- **D32. Due moduli Maven:** `match-engine`, il motore senza dipendenze di runtime, e `match-app`, che lo usa.
  Perché: l'isolamento del motore (D8) lo garantisce il compilatore. Fonte: SPEC, Vincoli.
- **D49. Il motore vede solo le proprie costanti;** i valori dei file d'ingresso si traducono nel modulo che legge
  il file. Perché: il formato dei file delle squadre, compresi i termini in italiano, può cambiare senza toccare il
  motore (D8). Fonte: SPEC, "Riga di comando".
- **D55. La configurazione del banco di taratura è separata da quella del motore** e sta in `match-app`. Perché: il
  banco descrive l'obiettivo, non il motore. Fonte: SPEC, "Banco di taratura"; piano, "File coinvolti".

### Tecnologia

- **D15. Java 21 puro con Maven:** niente Spring né altri framework. Fonte: SPEC, Vincoli.
- **D16. Dipendenze esterne solo se indispensabili e concordate;** il motore non ne ha a runtime. Fonte: SPEC,
  Vincoli.
- **D31. Nome:** artifactId `match-simulator` (`match-misulator` era un refuso), package radice
  `org.tasktelemetry.matchsimulator`. Le cartelle del repository e delle spec non cambiano nome. Fonte: SPEC.
- **D33. JSON letto con Jackson databind, solo in `match-app`.** Perché: un solo parser per tutti gli ingressi, e le
  forbici di taratura restano nei loro file. Fonte: SPEC, Vincoli.
- **D35. Test con JUnit 5 e AssertJ, solo nello scope test.** Fonte: SPEC, Vincoli.

### Taratura e dati

- **D19. Si tara su un campionato simulato con squadre di forza diversa,** mai su una partita ripetuta fra squadre
  uguali, contro i cinque grandi campionati. Perché: le forbici descrivono stagioni intere. Fonte: analisi §11;
  [data/calibration](../data/calibration/README.md).
- **D20. Un obiettivo senza fonte non si usa per tarare;** i parametri senza obiettivo restano valori di orientamento
  dichiarati come tali, ma mai a zero. Perché: tarare su un numero che nessuno sa giustificare trasforma un'ipotesi
  in comportamento; un parametro a zero toglie l'evento dalla partita. Fonte: SPEC, Vincoli; analisi §7.
- **D22. Il progetto resta non commerciale.** Dati StatsBomb: nel repository solo valori aggregati, mai i dati
  grezzi, attribuzione «Data: StatsBomb» nell'output che li usa. football-data.co.uk: solo aggregati finché non sono
  verificate le condizioni del sito. openfootball (CC0): anche le singole partite. Perché: sono le condizioni di
  licenza delle fonti dei dati. Fonte:
  [data/calibration](../data/calibration/README.md).
- **D23. OpenFootManager (GPLv3) e openengine (MIT): se ne riprendono le idee, mai il codice.** Perché:
  copiare codice GPLv3 obbligherebbe a rilasciare il progetto sotto GPL; il codice di openengine è troppo
  sperimentale per essere riusato. Fonte: `CLAUDE.md`; analisi, "Licenze".

## Proposte da confermare

Scelte generali non ancora approvate: non sono regole, e nessuno le applica come se lo fossero.

Nessuna.

## Spostate altrove

Decisioni che erano qui ma riguardano una sola funzionalità: il numero resta valido e rimanda alla fonte.

| Decisioni | Tema | Dove stanno ora |
|---|---|---|
| D17, D18, D52 | perimetro: dentro e fuori | SPEC, "Comportamento atteso", "Fuori scope" |
| D21, D40 | criterio di «finito» della taratura | SPEC, "Banco di taratura", tabella del criterio |
| D26, D27, D37 | stili, fattore campo, propensione al cross | SPEC, "Decisioni prese", 1 e 2 |
| D28, D38, D42, D45, D46, D47 | porta scoperta, chi esce, reparto svuotato, panchinaro di ripiego, spareggi | SPEC, "Eventi di contorno" |
| D29, D43 | fine dei tempi e recupero | SPEC, "Tempo e partita" |
| D30 | cronaca predefinita e completa | SPEC, "Evento, tabellino, racconto" |
| D34, D54 | misura e forbice del possesso | SPEC, "Banco di taratura", «Forbici» |
| D36 | valori iniziali di taratura | SPEC, "Decisioni prese", 3 |
| D39 | validità della squadra | SPEC, "Ingresso e motore" |
| D41, D50, D53 | squadre del banco: composizione, profili, forze | SPEC, "Banco di taratura" |
| D44 | rigore | SPEC, "Tiro" |
| D48 | regola dei 7 e risultato a tavolino | SPEC, "Eventi di contorno" |
| D51 | uscita in UTF-8 e numeri in formato italiano | SPEC, "Riga di comando" |
