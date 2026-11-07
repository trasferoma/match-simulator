# Decisioni di progetto

Principi che valgono per **ogni** funzionalità, presente o futura. Il comportamento di una funzionalità (valori,
spareggi, validazioni, criteri di accettazione) sta nella sua SPEC, non qui. La SPEC della prima versione è
`C:\build\git\spec\match-misulator\spec-motore-partita-v1.md` (qui «SPEC»).

- **Cosa entra.** Una scelta entra qui se la prossima SPEC dovrà rispettarla. Altrimenti resta solo nella SPEC.
- **Conflitti.** Sui principi vince questo registro: una SPEC che ne contraddice uno si corregge, oppure prima si
  cambia la decisione, con il sì dell'utente. Sui dettagli di una funzionalità vince la SPEC: qui non ci sono.
- **Da dove arrivano.** Una scelta nasce in una SPEC; se si rivela generale viene promossa qui, con una riga che
  rimanda alla SPEC senza ricopiarne il dettaglio.
- **Niente valori di taratura:** la taratura li cambia; stanno nella SPEC e nella configurazione.
- **Chi aggiorna e quando:** `CLAUDE.md`, sezione «Documenti».

I numeri non si riusano. Le decisioni che erano qui ma riguardano solo la prima versione sono in fondo, con il punto
della SPEC in cui stanno, così i riferimenti `D<n>` continuano a funzionare.

## Modello di simulazione

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
- **D7. Fallo a catena:** fallo con gravità → punizione o rigore → cartellino → espulsione. Fonte: analisi §2.5.
- **D24. Il gioco sulle fasce è una probabilità legata alla tattica, non una posizione** (cross dalla trequarti,
  duello aereo). Perché: dà cross e colpi di testa senza corsie nel campo. Fonte: SPEC, "Stato e azioni"; analisi §1.1.
- **D25. Ogni attributo dei giocatori è letto da almeno una regola;** nessun attributo decorativo. Elenco e regole:
  SPEC, "Ingresso e motore".

## Architettura

- **D8. Motore isolato:** riceve squadre, configurazione e seme, esegue a passi, restituisce solo eventi; niente
  I/O, e non conosce file, racconto, riga di comando né taratura. Perché: il motore si prova e si tara da solo, e gli
  strati sopra cambiano senza toccarlo. Fonte: SPEC, "Ingresso e motore"; analisi §6.1-6.2.
- **D9. Gli eventi sono l'unica fonte di verità:** tabellino e statistiche si calcolano solo dalla lista degli
  eventi. Perché: due calcoli separati prima o poi divergono. Fonte: analisi §5.1.
- **D10. Il racconto non inventa nulla:** usa solo informazioni presenti nell'evento; se serve una sfumatura, la
  calcola il motore e la mette nel dettaglio dell'evento. Fonte: analisi §5.2-5.3.
- **D11. Un gesto con conseguenze diverse ha un tipo di evento diverso.** Fonte: analisi §5.4.
- **D12. Riproducibilità:** stesso seme, stesse squadre, stessa configurazione → stessa sequenza di eventi, anche in
  un'altra JVM. L'unica sorgente di casualità è il generatore con seme della partita, e nessun risultato dipende
  dall'ordine di iterazione di strutture a ordine non garantito. Elenco dei divieti: SPEC, Vincoli. Fonte: analisi
  §6.3.
- **D13. Tutti i numeri in un'unica configurazione** con valori di default; nessuna costante di taratura nelle
  regole. Perché: la taratura tocca un solo posto. Fonte: analisi §6.4.
- **D14. Ogni tattica ha un effetto in un solo punto e in una sola direzione; lo stile neutro moltiplica per 1;
  ogni stile ha un costo oltre al beneficio.** Fonte: analisi §4.3.
- **D32. Due moduli Maven:** `match-engine`, il motore senza dipendenze di runtime, e `match-app`, che lo usa.
  Perché: l'isolamento del motore (D8) lo garantisce il compilatore. Fonte: SPEC, Vincoli.
- **D49. Il motore vede solo le proprie costanti;** i valori dei file d'ingresso si traducono nel modulo che legge
  il file. Fonte: SPEC, "Riga di comando".
- **D55. La configurazione del banco di taratura è separata da quella del motore** e sta in `match-app`. Perché: il
  banco descrive l'obiettivo, non il motore. Fonte: SPEC, "Banco di taratura"; piano, "File coinvolti".

## Tecnologia

- **D15. Java 21 puro con Maven:** niente Spring né altri framework. Fonte: SPEC, Vincoli.
- **D16. Dipendenze esterne solo se indispensabili e concordate;** il motore non ne ha a runtime. Fonte: SPEC,
  Vincoli.
- **D31. Nome:** artifactId `match-simulator` (`match-misulator` era un refuso), package radice
  `org.tasktelemetry.matchsimulator`. Le cartelle del repository e delle spec non cambiano nome. Fonte: SPEC.
- **D33. JSON letto con Jackson databind, solo in `match-app`.** Perché: un solo parser per tutti gli ingressi, e le
  forbici di taratura restano nei loro file. Fonte: SPEC, Vincoli.
- **D35. Test con JUnit 5 e AssertJ, solo nello scope test.** Fonte: SPEC, Vincoli.

## Taratura e dati

- **D19. Si tara su un campionato simulato con squadre di forza diversa,** mai su una partita ripetuta fra squadre
  uguali, contro i cinque grandi campionati. Perché: le forbici descrivono stagioni intere. Fonte: analisi §11;
  [data/calibration](../data/calibration/README.md).
- **D20. Un obiettivo senza fonte non si usa per tarare;** i parametri senza obiettivo restano valori di orientamento
  dichiarati come tali, ma mai a zero. Fonte: SPEC, Vincoli.
- **D22. Il progetto resta non commerciale.** Dati StatsBomb: nel repository solo valori aggregati, mai i dati
  grezzi, attribuzione «Data: StatsBomb» nell'output che li usa. football-data.co.uk: solo aggregati finché non sono
  verificate le condizioni del sito. openfootball (CC0): anche le singole partite. Fonte:
  [data/calibration](../data/calibration/README.md).
- **D23. OpenFootManager (GPLv3) e openengine (MIT): se ne riprendono le idee, mai il codice.** Fonte: `CLAUDE.md`.

## Proposte da confermare

Nessuna.

## Spostate nella SPEC della prima versione

Riguardano solo la prima versione: il dettaglio vale come scritto nella SPEC.

| Decisioni | Tema | Dove nella SPEC |
|---|---|---|
| D17, D18, D52 | perimetro: dentro e fuori | "Comportamento atteso", "Fuori scope" |
| D21, D40 | criterio di «finito» della taratura | "Banco di taratura", tabella del criterio |
| D26, D27, D37 | stili, fattore campo, propensione al cross | "Decisioni prese", 1 e 2 |
| D28, D38, D42, D45, D46, D47 | porta scoperta, chi esce, reparto svuotato, panchinaro di ripiego, spareggi | "Eventi di contorno" |
| D29, D43 | fine dei tempi e recupero | "Tempo e partita" |
| D30 | cronaca predefinita e completa | "Evento, tabellino, racconto" |
| D34, D54 | misura e forbice del possesso | "Banco di taratura", «Forbici» |
| D36 | valori iniziali di taratura | "Decisioni prese", 3 |
| D39 | validità della squadra | "Ingresso e motore" |
| D41, D50, D53 | squadre del banco: composizione, profili, forze | "Banco di taratura" |
| D44 | rigore | "Tiro" |
| D48 | regola dei 7 e risultato a tavolino | "Eventi di contorno" |
| D51 | uscita in UTF-8 e numeri in formato italiano | "Riga di comando" |
