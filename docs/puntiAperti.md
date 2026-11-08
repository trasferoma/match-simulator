# Punti aperti

Domande emerse durante l'implementazione della prima versione del motore, da rivedere tutte insieme alla fine.
Per ognuna: cosa fa oggi il codice e quali sono le alternative. Una domanda decisa si toglie da qui e la decisione va
nella SPEC (o in [DECISIONI.md](DECISIONI.md), se vale per ogni funzionalità).

Dettagli e numeri di ogni scelta stanno nel registro del piano di implementazione (`implementation-motore-partita-v1.md`).

## Regole di gioco

### Dove nascono i falli?
- **Oggi:** solo quando un giocatore perde un dribbling, cioè nel duello in cui conta il contrasto (`tackling`).
- **Perché:** con il fallo possibile su ogni duello perso il fattore campo si annacquava (vittorie in trasferta fuori
  forbice). Così anche i rigori nascono solo da un dribbling perso in area.
- **Alternative:** fallo anche sull'intercetto di un passaggio, nel contropiede o nel duello aereo, con probabilità
  proprie.

### Reparto svuotato con un solo centrocampista
- **Oggi:** se un reparto resta vuoto e il centrocampo, che dovrebbe dare un uomo, ne ha uno solo, lo dà il reparto
  più numeroso (di solito l'attacco).
- **Alternativa:** lettura letterale della SPEC: il centrocampista scende e il centrocampo si riempie dall'attacco
  (catena).
- Capita solo con formazioni insolite.

### Secondo giallo nel conteggio dei rossi
- **Oggi:** il secondo giallo conta sia fra i gialli sia fra i rossi del tabellino. I rossi simulati sono troppi
  rispetto ai campionati reali, quasi tutti da secondo giallo: un ammonito continua a fare falli come prima.
- **Da decidere:** come la fonte reale (football-data) conta il secondo giallo; se un ammonito deve rischiare di meno.

### Cross: probabilità di riuscita
- **Oggi:** il cross riesce secondo il passaggio del crossatore contro una difficoltà fissa: con un buon passatore
  circa 1 su 4.
- **Da decidere:** se il valore va bene o se il cross deve dipendere anche da chi difende.

### Chi crossa e chi batte l'angolo
- **Oggi:** sempre un centrocampista (così crossatore e chi colpisce di testa non coincidono).
- **Alternativa:** anche difensori laterali o attaccanti.

### Cross e colpo di testa a cavallo della scadenza
- **Oggi:** cross e colpo di testa sono due azioni. Se il tempo scade dopo un cross riuscito, il colpo di testa non si
  gioca.
- **Alternativa:** trattarli come un'unica azione che si conclude anche a tempo scaduto.

### Rigore parato in angolo
- **Oggi:** un rigore parato può finire in calcio d'angolo, come ogni tiro parato.
- **Alternativa:** dopo il rigore parato il portiere trattiene sempre.

### Durata della punizione diretta
- **Oggi:** la punizione diretta spende la durata della punizione più quella del tiro.
- **Da confermare:** la SPEC dà entrambe le durate senza dire come si combinano.

### Quando può esserci fuorigioco
- **Oggi:** solo su un passaggio riuscito che arriva in trequarti o nell'area avversaria. Dribbling, contropiede e
  cross non danno mai fuorigioco. Il fuorigioco è un attaccante scelto come gli altri giocatori, non il destinatario
  di un passaggio nominato.
- **Alternativa:** fuorigioco anche sul cross e sul lancio del contropiede.
- Il banco non ha un obiettivo reale per i fuorigioco: oggi sono circa 2 a partita, probabilmente pochi.

### Il fuorigioco non dipende dalla difesa
- **Oggi:** la probabilità dipende solo dal `positioning` dell'attaccante che riceve (come dice la SPEC). Una difesa
  ben piazzata non fa cadere più spesso gli avversari in fuorigioco.
- **Alternative:**
  1. duello di posizione: `positioning` dell'attaccante contro quello del difensore, come gli altri duelli (proposta
     della sessione: costo piccolo, una sola regola);
  2. trappola del fuorigioco come tattica della squadra («linea alta»), con un costo quando fallisce (attaccante solo
     davanti al portiere): più realistica, ma è una tattica nuova senza dati per tararla;
  3. il duello subito, la tattica in una versione successiva.

### Calcio d'inizio
- **Oggi:** la squadra di casa nel primo tempo, l'ospite nel secondo.
- **Alternativa:** sorteggio con il seme della partita.

## Stili di gioco

### Dove agiscono attacco e difesa dello stile
- **Oggi:** i moltiplicatori di attacco e difesa dello stile valgono in tutte le zone fuori dal centrocampo, anche
  nell'area propria (lo stile difensivo rende più difficile anche uscire dalla propria area).
- **Alternativa:** solo nella metà campo avversaria per l'attacco e nella propria per la difesa.

### Stili e fattore campo su cross e duello aereo
- **Oggi:** stili e fattore campo agiscono solo sui duelli del gioco aperto e del contropiede, non su cross, duello
  aereo, tiro e portiere.
- **Alternativa:** estenderli anche a quei duelli.

## Taratura

### Possesso troppo equilibrato
- **Oggi:** le partite simulate stanno quasi tutte fra 45% e 55% di possesso; nella realtà vanno da circa 35% a 65%.
  Con le forze del banco (da 55 a 80) il duello non va oltre 59/41, e gli stili non bastano ad allargare la
  distribuzione.
- **Alternative:** un parametro che renda il duello più netto; stili con effetto più forte sul possesso; cambiare la
  forbice o il modo di misurarla nella SPEC.

### Pochi pareggi
- **Oggi:** i pareggi sono sotto la forbice reale.
- **Da capire:** se dipende dal numero di gol, dalla forza delle squadre o dal fattore campo.

### Pochi calci d'angolo
- **Oggi:** circa 2,5 angoli a partita contro circa 10 reali. L'angolo nasce solo da parate, tiri murati e respinte
  dopo un cross.
- **Alternativa:** aggiungere una sorgente (per esempio cross o passaggio deviato in angolo).
