# match-misulator

Simulatore di partite di calcio fra due squadre che produce una partita **raccontabile azione per azione**. Niente grafica: il motore produce eventi, uno strato separato li trasforma in cronaca.

## Stato del progetto

- Fase di specifica: nessun codice applicativo ancora scritto.
- Spec e piano di implementazione: `C:\build\git\spec\match-misulator\` (`spec-motore-partita-v1.md`, `implementation-motore-partita-v1.md`). Fuori dal repository, per scelta.
- Base concordata della spec: [analisiProgettiConcorrenti.md](analisiProgettiConcorrenti.md). Le decisioni di progetto valide per ogni funzionalità sono in [docs/DECISIONI.md](docs/DECISIONI.md).

## Documenti

Unico posto in cui si dice a cosa serve ogni documento, chi lo aggiorna e quando: gli altri file rimandano qui.
«Sessione principale» è la sessione che apre e chiude le fasi del piano; l'implementazione di una fase può essere
delegata a un subagent.

| Documento | A cosa serve | Quando leggerlo | Chi lo aggiorna, e quando |
|---|---|---|---|
| [docs/DECISIONI.md](docs/DECISIONI.md) | principi validi per ogni funzionalità, presente o futura | prima di scrivere una SPEC e prima di una scelta che tocca modello, architettura, tecnologia o dati | la sessione principale, solo con il sì dell'utente: quando una scelta di una SPEC si rivela generale (promozione) o quando un principio cambia |
| SPEC (cartella sopra) | comportamento di una funzionalità: valori, regole di dettaglio, criteri DoD | prima di implementare | la sessione di specifica, con la conferma dell'utente; durante l'implementazione solo con una decisione esplicita (regole del piano) |
| Piano (cartella sopra) | fasi, stato, registro di decisioni tecniche e deviazioni | all'inizio e alla ripresa del lavoro | la sessione principale, a ogni fase, come dicono le regole del piano |
| [docs/ESEMPI.md](docs/ESEMPI.md) | quale classe imitare per ogni tipo di compito | prima di scrivere una classe | la sessione principale, alla chiusura di ogni fase dopo la verifica: le classi create passano da `prevista` a `esiste` |
| [docs/DEVIAZIONI.md](docs/DEVIAZIONI.md) | cosa c'è nel codice da non imitare | prima di imitare codice esistente | chi accetta un compromesso nel codice, nella stessa modifica; la sessione principale lo controlla alla chiusura della fase |
| [docs/CONTROLLI.md](docs/CONTROLLI.md) | quale test o struttura fa rispettare ogni principio | quando si aggiunge un principio o si trova un errore tardi | la sessione principale: alla chiusura di ogni fase i controlli scritti passano a `fatto`; una riga nuova per ogni principio nuovo o errore trovato tardi che un controllo avrebbe fermato |
| questo file | regole sempre attive e indice | a ogni sessione | la sessione principale: «Comandi» nella Fase 2; principi solo con il sì dell'utente, insieme a `docs/DECISIONI.md` |
| [data/calibration/README.md](data/calibration/README.md) | fonti, licenze e valori dei dati di taratura | prima di toccare la taratura o i dati | chi cambia i dati di taratura, nella stessa modifica |

## Comandi

Da completare con la Fase 2 del piano (impianto Maven a due moduli). Oggi il progetto non ha codice né wrapper Maven.

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
