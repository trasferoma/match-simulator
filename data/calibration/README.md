# Dati di taratura

Statistiche reali dei cinque grandi campionati europei, usate come obiettivi per tarare il motore di simulazione. Il motore è tarato quando le medie di un campionato simulato cadono dentro le forbici.

Tre fonti, ognuna con il suo file:

| File | Cosa contiene | Fonte |
|---|---|---|
| `big5-targets.json` | risultati: gol, esiti, porte inviolate, distribuzione dei punteggi | openfootball, 40 stagioni di campionato |
| `big5-match-stats-targets.json` | tiri, tiri in porta, falli, angoli, cartellini | football-data.co.uk, le stesse 40 stagioni di campionato |
| `statsbomb-1516-targets.json` | possesso, azioni, passaggi, xG | StatsBomb Open Data, stagione 2015-16, quattro campionati |

# 1. Risultati (openfootball)

## Fonte

- **Dataset:** [openfootball/football.json](https://github.com/openfootball/football.json), licenza CC0 (pubblico dominio).
- **Commit:** `e6744429ee395bc86f247348c6184bb08d4eb361` del 2026-09-22.
- **Campionati:** Premier League, Liga, Bundesliga, Serie A, Ligue 1.
- **Stagioni:** 2014-15 → 2018-19 e 2021-22 → 2023-24. In totale 40 stagioni di campionato e 14.534 partite.

Stagioni escluse:

| Stagioni | Motivo |
|---|---|
| 2010-11 → 2013-14 | non tutti e cinque i campionati sono presenti nel dataset |
| 2019-20 | porte chiuse; Ligue 1 interrotta a 279 partite su 380 |
| 2020-21 | in gran parte a porte chiuse |
| 2024-25 | incompleta nel dataset: Liga e Serie A hanno 370 risultati su 380 |
| 2025-26 | incompleta nel dataset in tutti e cinque i campionati |

Regola: una stagione entra solo se è completa in tutti e cinque i campionati, così ogni campionato pesa allo stesso modo.

## File

- **`big5-targets.json`**: gli obiettivi di taratura.
  - `metrics`: una voce per metrica. `low` e `high` sono la forbice da rispettare, cioè il 10° e il 90° percentile dei valori delle 40 stagioni. `observed` riporta minimo, mediana, massimo e il valore calcolato su tutte le partite insieme (`pooled`). `per_league` riporta mediana, minimo e massimo di ogni campionato.
  - `distributions_pct`: distribuzioni calcolate su tutte le partite. Contiene i gol della squadra di casa, quelli della squadra ospite, il totale per partita e i 15 risultati più frequenti.
- **`big5-matches.csv`**: le 14.534 partite (campionato, stagione, data, squadre, risultato del primo tempo e finale). Serve per ricalcolare metriche nuove senza scaricare di nuovo il dataset.

## Valori principali

| Metrica | Forbice | Mediana |
|---|---|---|
| Gol a partita | 2,55 – 3,12 | 2,71 |
| Gol della squadra di casa | 1,44 – 1,76 | 1,53 |
| Gol della squadra ospite | 1,09 – 1,36 | 1,20 |
| Vittorie in casa | 41,8% – 48,4% | 45,2% |
| Pareggi | 22,1% – 28,9% | 24,8% |
| Vittorie in trasferta | 27,3% – 33,7% | 29,2% |
| Entrambe le squadre segnano | 48,3% – 59,2% | 51,4% |
| Porta inviolata in casa | 27,2% – 35,8% | 32,2% |
| Porta inviolata in trasferta | 18,3% – 25,0% | 23,3% |
| Pareggi 0-0 | 5,2% – 8,9% | 7,2% |
| Partite con almeno 3 gol | 46,0% – 58,7% | 51,8% |
| Quota di gol segnati nel primo tempo | 41,5% – 46,3% | 44,5% |

Risultati più frequenti: 1-1 (11,8%), 1-0 (9,6%), 2-1 (8,7%), 2-0 (7,5%), 0-1 (7,3%), 0-0 (7,1%).

## Avvertenze

- **Il confronto va fatto su un campionato simulato.** Le forbici descrivono stagioni intere, con squadre forti e deboli mescolate. Una sola partita ripetuta fra due squadre uguali non è confrontabile: produce per esempio più pareggi.
- **Primo tempo:** il risultato all'intervallo manca in 1.034 partite (circa il 7%), distribuite in modo uniforme fra campionati e stagioni. La quota di gol del primo tempo è calcolata solo sulle partite che lo hanno.
- **Solo risultati.** Il dataset non contiene tiri, possesso, passaggi, falli né cartellini: stanno nelle sezioni 2 e 3.
- **Controllo incrociato:** le forbici dei gol a partita e delle porte inviolate coincidono con quelle calcolate in modo indipendente da OpenFootManager sullo stesso insieme di stagioni.

# 2. Tiri, falli, angoli, cartellini (football-data.co.uk)

## Fonte

- **Dataset:** statistiche di [football-data.co.uk](https://www.football-data.co.uk/), lette dal mirror [datasets/football-datasets](https://github.com/datasets/football-datasets) di DataHub.
- **Commit del mirror:** `436a51f15258e7d0c6042c231227202b70a5a0ae` del 2026-06-23.
- **Licenza:** il mirror dichiara la licenza PDDL (pubblico dominio). Le condizioni del sito originale non sono state verificate. Per prudenza nel repository c'è **solo il file aggregato**, non le partite.
- **Popolazione:** le stesse 40 stagioni di campionato della sezione 1. Sono 14.532 partite: 2 sono state scartate perché prive di statistiche.

## File

`big5-match-stats-targets.json` ha la stessa struttura di `big5-targets.json`: `metrics` con forbice (10°–90° percentile delle stagioni), valori osservati e dettaglio per campionato.

## Valori principali

| Metrica | Forbice | Mediana |
|---|---|---|
| Tiri a partita (due squadre) | 23,7 – 26,4 | 25,3 |
| Tiri della squadra di casa | 13,0 – 14,6 | 13,9 |
| Tiri della squadra ospite | 10,4 – 11,9 | 11,3 |
| Tiri in porta a partita | 8,3 – 9,6 | 8,7 |
| Tiri in porta / tiri | 32,8% – 37,0% | 35,1% |
| Gol / tiri | 10,2% – 11,9% | 11,0% |
| Gol / tiri in porta | 29,9% – 32,9% | 31,5% |
| Falli a partita | 21,5 – 28,7 | 25,9 |
| Calci d'angolo a partita | 9,4 – 10,7 | 9,7 |
| Gialli a partita | 3,35 – 5,03 | 3,92 |
| Rossi a partita | 0,11 – 0,29 | 0,21 |
| Partite con almeno un rosso | 11,0% – 25,1% | 18,2% |
| Gialli / falli | 13,1% – 18,9% | 16,0% |

## Avvertenze

- **Definizioni del fornitore.** "Tiro in porta" include i gol. I tiri murati contano come tiri ma non come tiri in porta. Le definizioni di StatsBomb (sezione 3) sono simili ma non identiche: i valori delle due fonti non vanno mescolati nella stessa metrica.
- **I falli variano molto fra campionati** (la forbice è larga): dipendono dallo stile arbitrale più che dal gioco.
- **Controllo incrociato:** i gialli (3,35–5,03) e i rossi (0,11–0,29) sono coerenti con il CIES Football Observatory, che sulle stesse leghe riporta 3,39–5,27 gialli e 0,12–0,27 rossi.

# 3. Possesso, azioni, passaggi, xG (StatsBomb Open Data)

## Fonte

- **Dataset:** [StatsBomb Open Data](https://github.com/statsbomb/open-data), dati evento (ogni passaggio, tiro, contrasto, con tempi e sequenze di possesso).
- **Commit:** `4b73468fc5b0f1950f9f66fada70ad3a4f9327cb`.
- **Popolazione:** stagione 2015-16. Premier League, Liga e Serie A complete (380 partite ciascuna), Ligue 1 377 su 380. In totale 1.517 partite.
- **Esclusa la Bundesliga:** nei dati aperti ci sono solo le 34 partite di una squadra.
- **Attribuzione obbligatoria: Data: StatsBomb.**

## Licenza: vincoli da rispettare

Il contratto d'uso (*StatsBomb Public Data User Agreement*, versione dell'8 settembre 2023) stabilisce che:
- i dati **non si possono ridistribuire**: per questo nel repository c'è solo il file aggregato, e i dati evento non vanno mai salvati qui;
- né i dati **né le analisi derivate** si possono sfruttare commercialmente: se il progetto diventasse commerciale, queste forbici andrebbero sostituite;
- ogni pubblicazione di analisi basate sui dati deve citare StatsBomb con il suo logo;
- StatsBomb chiede di registrarsi su [statsbomb.com/resource-centre](https://www.statsbomb.com/resource-centre) prima di usare i dati.

## File

`statsbomb-1516-targets.json`. Con una sola stagione non ha senso una forbice fra stagioni: i valori sono **distribuzioni sulle singole partite** (10°, 25°, 50°, 75° e 90° percentile), sia su tutti e quattro i campionati (`all_leagues`) sia per campionato (`per_league`). Le definizioni sono nel campo `definitions`.

## Valori principali

**Possesso** (quota di passaggi, la convenzione delle statistiche televisive):

| Metrica | Valore |
|---|---|
| Possesso di una squadra in una partita, dal 10° al 90° percentile | 36% – 64% |
| Possesso medio della squadra di casa | 51,4% |
| Squadre con almeno il 60% di possesso | 18,0% delle partite di squadra |
| Squadre con almeno il 65% | 8,0% |
| Squadre con almeno il 70% | 1,9% |
| La squadra con più possesso vince / pareggia / perde | 38% / 27% / 35% |

**Azioni** (una "azione" è una sequenza di possesso StatsBomb):

| Metrica | Valore |
|---|---|
| Azioni per squadra in una partita | mediana 98 (85 – 112) |
| Durata di un'azione | mediana 11,5 s; 10° percentile 1,9 s, 90° percentile 40 s |
| Azioni che finiscono con un tiro | 11,6% |
| Azioni che finiscono con un gol | 1,3% |

**Passaggi e tiri:**

| Metrica | Valore |
|---|---|
| Passaggi per squadra in una partita | mediana 478 (348 – 637) |
| Passaggi riusciti | 76,7% |
| xG a partita (due squadre) | 2,47 |
| xG di un tiro | mediana 0,05; 90° percentile 0,23 |
| Tiri con xG ≥ 0,3 ("grandi occasioni") | 7,2% |
| xG di una squadra in una partita | mediana 1,09 (0,42 – 2,21) |

## Avvertenze

- **Il possesso non serve come media**, che è sempre 50%. Si tarano la **dispersione** (quanto spesso una squadra domina il pallone) e il **legame con il risultato**: avere più palla dà un vantaggio piccolo (38% di vittorie contro 35% di sconfitte).
- **Durata delle azioni:** si usano i percentili, non la media. Dentro un'azione StatsBomb restano anche le interruzioni (falli, rimesse) se la palla resta alla stessa squadra, e questo gonfia la coda. Da ogni partita è stata esclusa un'azione a cavallo dell'intervallo, che è un artefatto del calcolo.
- **Falli:** StatsBomb registra circa 30 falli a partita, football-data circa 26, perché le definizioni sono diverse. Per i falli e i cartellini fa fede la sezione 2.
- **Controllo incrociato con la sezione 2:** tiri a partita 25,0 (football-data 25,3); gialli 4,40 (forbice football-data 3,35–5,03). Gol nelle stesse partite: 2,63 a partita, contro 2,47 di xG.
- **Una sola stagione,** e ormai datata: il calcio di oggi ha in media più passaggi e più possesso consolidato. È il limite dei dati aperti disponibili.
