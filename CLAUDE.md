# match-misulator

Simulatore di partite di calcio fra due squadre che produce una partita **raccontabile azione per azione**. Niente grafica: il motore produce eventi, uno strato separato li trasforma in cronaca.

## Stato del progetto

- Implementazione della prima versione in corso, secondo il piano (lo stato delle fasi sta nel piano, non qui).
- Spec e piano di implementazione: `C:\build\git\spec\match-misulator\` (`spec-motore-partita-v1.md`, `implementation-motore-partita-v1.md`). Fuori dal repository, per scelta.
- Base concordata della spec: [analisiProgettiConcorrenti.md](analisiProgettiConcorrenti.md). Le decisioni di progetto valide per ogni funzionalità sono in [docs/DECISIONI.md](docs/DECISIONI.md).

## Documenti

Unico posto in cui si dice a cosa serve ogni documento, chi lo aggiorna e quando: gli altri file rimandano qui e non
ripetono queste regole. L'introduzione di ogni documento contiene invece le regole sul suo contenuto (domanda
d'ingresso, formato, cosa contiene e cosa no).

| Documento | A cosa serve | Quando leggerlo | Chi lo aggiorna, e quando |
|---|---|---|---|
| [docs/DECISIONI.md](docs/DECISIONI.md) | principi validi per ogni funzionalità, presente o futura | prima di scrivere una specifica e prima di una scelta che tocca dominio, architettura, tecnologia o dati | la sessione principale, solo con il sì dell'utente: quando una scelta nata in una specifica o in una revisione si rivela generale, o quando un principio cambia |
| [docs/ESEMPI.md](docs/ESEMPI.md) | quale classe imitare per ogni tipo di compito | prima di scrivere una classe | la sessione principale, a chiusura di un lavoro verificato: quando nasce il primo buon esempio di un tipo di compito, o uno migliore di quello indicato |
| [docs/DEVIAZIONI.md](docs/DEVIAZIONI.md) | cosa c'è nel codice da non imitare | prima di imitare codice esistente | chi accetta un compromesso nel codice, nella stessa modifica; la riga si toglie quando il compromesso sparisce |
| [docs/CONTROLLI.md](docs/CONTROLLI.md) | quale test o struttura fa rispettare ogni principio | quando si aggiunge un principio o si trova un errore tardi | la sessione principale: una riga per ogni principio nuovo; `fatto` quando il controllo esiste; una riga nuova quando un errore trovato tardi poteva essere fermato da un controllo |
| [docs/PUNTI_APERTI.md](docs/PUNTI_APERTI.md) | domande nate durante lo sviluppo che non bloccano, da decidere tutte insieme alla fine | prima di aggiungere una voce, per non ripeterla; alla revisione di fine sviluppo | la sessione principale, alla chiusura di ogni fase o lavoro, anche con le domande segnalate dai subagent; l'utente, alla revisione di fine sviluppo, decide ogni voce (va nella specifica, in DECISIONI.md o si scarta) e la voce si toglie |
| questo file | regole sempre attive e indice | a ogni sessione | la sessione principale; «Comandi» quando nasce il build; le regole sempre attive solo con il sì dell'utente |
| SPEC (cartella sopra) | comportamento di una funzionalità: valori, regole di dettaglio, criteri di accettazione | prima di implementare | la sessione di specifica, con la conferma dell'utente; durante l'implementazione solo con una decisione esplicita (regole del piano) |
| Piano (cartella sopra) | fasi, stato, registro di decisioni tecniche e deviazioni dalla SPEC | all'inizio e alla ripresa del lavoro | la sessione principale, a ogni fase, come dicono le regole del piano |
| [data/calibration/README.md](data/calibration/README.md) | fonti, licenze e valori dei dati di taratura | prima di toccare la taratura o i dati | chi cambia i dati di taratura, nella stessa modifica |

**Regole comuni a tutti i documenti:**

- I documenti di `docs/` contengono solo regole e comportamenti generali; l'eccezione è `PUNTI_APERTI.md`, che
  raccoglie domande temporanee. Ognuno ha una **domanda d'ingresso**, scritta nella sua introduzione: una riga entra
  solo se la supera, chiunque la scriva. Nel dubbio non entra, tranne in `PUNTI_APERTI.md`.
- A fine implementazione `/update-docs` cerca cosa è nato dal lavoro, scarta ciò che non supera nessuna domanda
  d'ingresso e propone il resto.
- «Sessione principale» è la sessione che coordina il lavoro con l'utente; può delegare la scrittura del codice a
  un subagent, ma l'aggiornamento dei documenti resta suo.
- Ogni informazione ha una sola casa: gli altri documenti rimandano, non ricopiano.
- Nei documenti di `docs/` niente numeri di criteri di accettazione, di fasi di un piano né valori di taratura: si
  citano nomi stabili (principi, classi, test). Solo `PUNTI_APERTI.md` può citare un valore, per descrivere il
  problema.
- Un file di `docs/` lo modifica una sola sessione alla volta.
- A chiusura di un lavoro che ha toccato codice o documenti, `/check-docs` verifica che i documenti siano coerenti.

## Comandi

Maven a due moduli: `match-engine` (motore) e `match-app` (file, cronaca, banco, riga di comando). Tutti i comandi si lanciano dalla radice del repository.

**JDK 21 obbligatorio.** Il Java predefinito della macchina è il 17: la build si ferma subito all'enforcer con un messaggio che lo spiega. Per il comando si imposta `JAVA_HOME` sul JDK 21, senza toccare l'ambiente permanente (in bash):

```bash
JAVA_HOME="/c/Users/fabio.dearcangelis/.jdks/ms-21.0.9" mvn -q verify
```

Negli esempi sotto si omette il prefisso `JAVA_HOME=...`, ma serve in ogni comando.

| Compito | Comando |
|---|---|
| Build e test (esclude il tag `calibration`) | `mvn verify` |
| Test del banco di taratura (solo il tag `calibration`, lenti) | `mvn verify -Pcalibration` |
| Riga di comando | `java -jar match-app/target/match-app-1.0-SNAPSHOT.jar <squadra-casa> <squadra-ospite> [--seed 42] [--full] [--stepByStep]` (esempio: `data/teams/esempio-casa.json data/teams/esempio-ospite.json`) |
| Banco di taratura (dalla Fase 7) | `java -cp match-app/target/match-app-1.0-SNAPSHOT.jar org.tasktelemetry.matchsimulator.calibration.CalibrationBench [cartella-dati]` |

- Il tag `calibration` si esclude tramite la proprietà `test.excludedGroups`, sovrascrivibile da riga di comando (`-Dtest.excludedGroups=...`).
- Riga di comando: le opzioni valgono in qualunque posizione; `--stepByStep` mostra un evento della cronaca a ogni Invio; senza `--seed` il seme si genera e si stampa in fondo. Codici di uscita: 0 riuscita, 1 file squadra mancante o non valido, 2 argomenti errati.
- Il default della cartella dei dati (`data/calibration`) vale solo se si avvia dalla radice del repository; altrimenti si passa il percorso come argomento. I test leggono il percorso dalla proprietà di sistema `calibration.dir`, impostata da Surefire in `match-app`.

## Tecnologia

- Java 21, Maven, Java puro (niente Spring, niente Liferay).
- Dipendenze esterne solo se strettamente necessarie e approvate.

## Modello di simulazione (sintesi)

- Catena di stati sul possesso ("livello 2"), non simulazione spaziale ad agenti.
- Stato = squadra in possesso + zona (5 zone in fila lungo il campo) + fase di gioco.
- Duelli con il rapporto `attacco / (attacco + difesa)` (Bradley-Terry); modificatori moltiplicativi.
- Ogni tiro ha un xG (probabilità di gol); è gol se l'estrazione cade sotto l'xG.
- Il tempo avanza della durata di ogni azione, in secondi.

## Principi da rispettare

- **Il motore è isolato:** riceve squadre, configurazione e seme; esegue a passi; restituisce solo eventi. Non conosce il formato dei file, la cronaca né il banco di taratura.
- **Gli eventi sono l'unica fonte di verità:** il tabellino e le statistiche si ricavano dagli eventi, mai calcolati a parte.
- **Il racconto non inventa nulla:** la cronaca usa solo informazioni presenti nell'evento. Se una sfumatura serve al racconto, il motore deve calcolarla e metterla nell'evento.
- **Riproducibilità:** stesso seme, stessa partita, anche in un'altra JVM. Nessuna sorgente di casualità fuori dal generatore con seme, e nessun risultato che dipenda dall'ordine di iterazione di strutture a ordine non garantito.
- **Nessun numero sparso:** probabilità e moltiplicatori di base stanno in un'unica configurazione.
- **Ogni tattica ha un effetto in un solo punto e in una sola direzione; il valore neutro moltiplica per 1.**
- **Si taglia grosso e si raffina dove la taratura lo chiede:** niente dettaglio aggiunto in anticipo.

## Taratura

- Obiettivi reali in [data/calibration](data/calibration/README.md): risultati (openfootball), tiri/falli/angoli/cartellini (football-data.co.uk), possesso/azioni/passaggi/xG (StatsBomb 2015-16).
- Il confronto si fa su un **campionato simulato** con squadre di forza diversa, mai su una sola partita ripetuta fra squadre uguali.
- Un obiettivo senza fonte non si usa per tarare.

## Vincoli sui dati

- **StatsBomb:** uso non commerciale; i dati grezzi **non** si possono mettere nel repository né ridistribuire; ogni pubblicazione di analisi basate su questi dati cita StatsBomb. Nel repository ci sono solo valori aggregati.
- **football-data.co.uk:** nel repository solo valori aggregati, finché non sono verificate le condizioni del sito originale.
- **openfootball:** pubblico dominio (CC0), anche le singole partite possono stare nel repository.
- Il progetto resta **non commerciale**.

## Riferimenti esterni

- OpenFootManager (GPLv3): se ne riprendono le idee, **mai il codice**.
- openengine (MIT): solo idee.
