# TurnoGiusto-I-Disagili

Progetto sviluppato per il corso
di "Metodi e Sviluppo Agile".

## Descrizione
TurnoGiusto è un sistema per la gestione
dei turni lavorativi nella ristorazione.

## Funzionalità previste
- Gestione disponibilità dei dipendenti
- Pianificazione dei turni
- Richieste di scambio e sostituzione
- Gestione delle emergenze
- Reportistica per il titolare

## Metodologia
Il progetto segue il framework Scrum.

## Stato del progetto
Sprint 1 in corso (consegna il 12 novembre). La logica di US-03/US-04 è disponibile;
l'interfaccia JavaFX è temporaneamente conservata nei commenti in attesa del CSS
condiviso del team. Vedi [guida US-03/US-04](docs/US03-US04.md) per test e ripristino.

---

# Guida per il team

## 1. Cosa serve (una volta sola)
- **JDK 21** (Temurin) e **IntelliJ IDEA**
- **Docker Desktop**, deve essere *avviato* quando si lavora (per il database MySQL)
- **Git** e un account GitHub con accesso a questo repository
- Maven **non** serve installarlo: il progetto usa il Maven Wrapper (`mvnw`)

## 2. Primo avvio
1. In IntelliJ: *Clone Repository…* con l'URL del repository, poi *Trust Project*.
2. In `File > Project Structure > Project` controllare che l'SDK sia **21**.
3. Attendere che Maven scarichi le dipendenze. Se non parte: pannello Maven, icona *Reload All Maven Projects*.
4. Avviare il database. Dalla cartella del progetto (terminale di IntelliJ, `Alt+F12`):

```bash
docker compose up -d --wait
```

La prima volta scarica l'immagine di MySQL (qualche minuto).

5. L'avvio JavaFX è temporaneamente sospeso. Il comando resta disponibile, ma
   stampa un messaggio e termina senza aprire finestre:

```bash
.\mvnw javafx:run
```

Anche la schermata Hello è conservata nei commenti. Il collegamento al database
si verifica con i test.

6. Eseguire i test:

```bash
.\mvnw test
```

Tutti i test, inclusi quelli JDBC, partono anche dal triangolo verde di IntelliJ.
Usano soltanto il database dedicato `turnogiusto_us03_test`, sulla porta `33317`.
Se MySQL Server locale è installato, i test avviano e spengono un'istanza temporanea;
in alternativa avviare il Compose di test indicato nella [guida](docs/US03-US04.md).

Per la verifica completa prima della pull request:

```powershell
.\mvnw.cmd clean verify
```

Per lavorare senza MySQL usare `.\mvnw.cmd -DexcludedGroups=database test`.
Non occorrono profili o variabili d'ambiente per l'avvio ordinario dei test.
Configurazioni IntelliJ, comandi Docker e dettagli sono nella
[guida US-03/US-04](docs/US03-US04.md).

## 3. Struttura del progetto
```
docker-compose.yml          database MySQL per lo sviluppo
.env.example                modello delle impostazioni personali (vedi sezione Database)
db/init/01_schema.sql       tabelle (bozza da rivedere insieme)
db/init/02_seed.sql         dati di prova
pom.xml                     dipendenze e build (Maven)
src/main/java/it/disagili/turnogiusto/
    Launcher.java                           avvio temporaneo senza finestra
    HelloApplication.java, HelloController.java  interfaccia di prova commentata
    repository/Database.java                connessione al database
src/main/resources/...                      FXML e CSS conservati nei commenti
src/test/java/...                           test JUnit 5
```
Struttura prevista per il codice nuovo: `domain` (regole e modello: dipendenti, contratti, vincoli), `repository` (accesso al database) e `ui` (schermate), con la logica nel `domain` e il meno possibile nelle schermate.

## 4. Database
- MySQL 8.4 in Docker. Database `turnogiusto`, utente `turnogiusto`, password `turnogiusto`: sono credenziali **solo di sviluppo**, valide soltanto sul proprio PC.
- Il database del progetto vive **dentro Docker**, non è il MySQL eventualmente installato in Windows.
- Porta di default: **3306**. Se è occupata (capita se sul PC c'è un MySQL installato in Windows; l'errore di `docker compose up` parla di "ports are not available"), copiare `.env.example` in `.env` e cambiare `MYSQL_PORT` (ad esempio 3307). Vale sia per Docker sia per l'applicazione, che legge la porta da quel file. Il file `.env` è personale e non viene caricato su Git.

### Comandi utili
Tutti da lanciare nella cartella del progetto.

```bash
docker compose ps
```
mostra se il database è acceso (deve essere *healthy*);

```bash
docker compose stop
```
```bash
docker compose start
```
spengono e riaccendono il database senza perdere i dati;

```bash
docker compose down -v
```
cancella il database **e i dati inseriti** (da rifare con `docker compose up -d --wait`).

### Guardare o provare i dati
Con un programma (DBeaver, MySQL Workbench, scheda Database di IntelliJ) creando una **nuova** connessione: host `localhost`, porta quella di `MYSQL_PORT` (3306 se non l'avete cambiata), database `turnogiusto`, utente e password `turnogiusto`. Oppure da terminale:

```bash
docker compose exec db mysql -uturnogiusto -pturnogiusto turnogiusto
```

### Modificare tabelle e dati di prova
- Le **tabelle** si modificano in `db/init/01_schema.sql`, i **dati di prova** in `db/init/02_seed.sql`. Poi si committa il file, così tutti ricevono le stesse modifiche.
- Gli script partono **solo alla prima creazione** del database: dopo aver cambiato un file bisogna ricrearlo con `docker compose down -v` e `docker compose up -d --wait` (i dati inseriti a mano vanno persi).
- Le modifiche fatte da un programma esterno (DBeaver, Workbench...) **non sono condivise** con gli altri e spariscono al primo reset: la struttura va sempre scritta nei file `.sql`.
- Nel file dei dati gli ID sono scritti a mano: per aggiungere righe usare numeri nuovi e usare gli stessi numeri nelle tabelle collegate (`contratto`, `disponibilita`), altrimenti MySQL dà un errore di chiave esterna.
- `01_schema.sql` contiene le scelte ancora da decidere insieme, segnate con `DA DECIDERE`.

## 5. Come lavoriamo
- Prima di iniziare: `Git > Pull`.
- Un ramo per storia (ad esempio `feature/US-01-registrazione`); nessun commit diretto su `main` una volta iniziato lo sviluppo, ma una pull request e la revisione di un altro del gruppo.
- Il messaggio di commit cita l'ID della storia (ad esempio `US-01: form di inserimento`).
- Una storia è finita solo se compila, i suoi criteri di accettazione hanno un test che passa, qualcun altro ha letto il codice e funziona partendo da zero seguendo questo README.
- Dopo un `pull` che cambia il `pom.xml`: *Reload All Maven Projects*.
- Nessuna password reale nel repository.

## 6. Problemi comuni
| Sintomo | Cosa fare |
|---|---|
| `docker compose up` dice "failed to connect to the docker API" o "daemon is not running" | Avviare Docker Desktop e aspettare che sia pronto. |
| Errore "ports are not available" sulla 3306 | Un altro MySQL usa la porta: creare `.env` con `MYSQL_PORT=3307` (vedi sezione Database). |
| L'app scrive "Database non raggiungibile" | Il database è spento (`docker compose up -d --wait`) oppure la porta in `.env` non coincide con quella di Docker. |
| JUnit termina con `-1` prima di eseguire US03 | Ricaricare Maven e usare le classi aggiornate: i test ora preparano autonomamente il database dedicato. |
| I test non trovano `mysqld` | Installare MySQL Server oppure avviare il Compose di test, come descritto nella guida US-03/US-04. |
| "JavaFX runtime components are missing" | Si è avviato con il Play standard: usare `javafx:run` dal pannello Maven. |
| `mvnw` dice che non trova `JAVA_HOME` | Impostare la variabile d'ambiente `JAVA_HOME` sulla cartella della JDK 21 e riavviare IntelliJ. |
| Dopo un `pull` mancano classi o dipendenze | Pannello Maven, *Reload All Maven Projects*. |
| Ho cambiato lo schema ma il database non cambia | Gli script partono solo alla creazione: `docker compose down -v` e poi `docker compose up -d --wait`. |

---

By IDisagili.
