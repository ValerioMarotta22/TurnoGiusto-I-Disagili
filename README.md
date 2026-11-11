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
Sprint 1 in corso (consegna il 12 novembre): ambiente di sviluppo pronto, schermate e storie da sviluppare.

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

5. Avviare l'applicazione: pannello Maven, `Plugins > javafx > javafx:run` (oppure da terminale PowerShell):

```bash
.\mvnw javafx:run
```

Premendo il bottone **Hello!** deve comparire "Database collegato: 6 dipendenti". Non avviare la classe con il pulsante Play standard di IntelliJ: JavaFX da solo non parte, usare sempre `javafx:run`.

6. Eseguire i test:

```bash
.\mvnw test
```

Il test di collegamento al database viene **saltato** (non fallisce) se il database non è acceso.
Attenzione: `DatabaseTest` e i test di US-03/04 non usano il database di Docker, ma avviano un MySQL temporaneo e richiedono un MySQL Server installato sul PC (`mysqld`). Se non c'è, lanciare i test con `.\mvnw test -DexcludedGroups=database`.

## 3. Struttura del progetto
```
docker-compose.yml          database MySQL per lo sviluppo
.env.example                modello delle impostazioni personali (vedi sezione Database)
db/init/01_schema.sql       tabelle del database
db/init/02_seed.sql         dati di prova
pom.xml                     dipendenze e build (Maven)
src/main/java/it/disagili/turnogiusto/
    Launcher.java, HelloApplication.java    avvio dell'applicazione
    HelloController.java                    schermata di prova (da sostituire)
    model/                                  classi del dominio (già presenti)
    repository/                             accesso al database (per ora solo Database.java)
    service/                                operazioni che mettono insieme più cose (da creare quando servono)
    controller/                             controller JavaFX delle schermate (da creare)
src/main/resources/...                      file FXML delle schermate
src/test/java/...                           test JUnit 5
```

### Architettura del codice
Il codice è diviso in quattro livelli. Ognuno parla solo con quello sotto di sé:

```
controller  ->  service  ->  repository  ->  database
                  (tutti usano le classi di model)
```

| Livello | Cosa contiene | Cosa NON deve contenere |
|---|---|---|
| `model` | Le classi del dominio (`Dipendente`, `Contratto`, `Disponibilita`, `Fabbisogno`, le enum) con le loro regole di validazione (`valida()`). | SQL, JavaFX. |
| `repository` | Solo l'accesso al database (JDBC): una classe per tabella o gruppo di tabelle, ad esempio `DipendenteRepository` con `trovaTutti()`, `salva()`, `trovaPerId()`. | Regole di business, JavaFX. |
| `service` | Le operazioni che coinvolgono più repository o più regole: ad esempio cambiare un contratto (US-02) significa chiudere il vecchio e inserire il nuovo **in una sola transazione**. | SQL scritto a mano, JavaFX. |
| `controller` | I controller JavaFX: leggono i campi della schermata, chiamano il service (o il repository), mostrano i risultati e gli errori. | SQL, regole di business. |

Regole pratiche:
- **Il service si crea solo se serve.** Se una schermata deve solo leggere o salvare un dato semplice, il controller può chiamare direttamente il repository. Non si scrivono service vuoti "per ordine".
- **Ogni storia è una fetta completa**: chi prende una storia scrive repository, eventuale service, controller, FXML e test per quella storia.
- **Gli errori di validazione** arrivano dal `valida()` del model come elenco di messaggi: la schermata li mostra, non li riscrive.
- **Enum e database**: nel database si salva `name()` (ad esempio `AIUTO_CUOCO`), nella schermata si mostra `toString()` (ad esempio "Aiuto cuoco").
- **Il caposala non è un ruolo** ma un incarico assegnato a un cameriere full-time per un singolo turno (`Dipendente.puoEssereCaposala()`, storia US-22). Per questo non compare nell'enum `Ruolo` né nel database dei dipendenti.
- **Test**: `model` e `service` con test unitari che non hanno bisogno del database; `repository` con test che usano il database acceso (vengono saltati se è spento).

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
- Lo schema è allineato alle classi del pacchetto `model`: i valori degli ENUM del database sono i nomi delle enum Java (il test `DatabaseTest` controlla che coincidano). Se si aggiunge un valore a un'enum, va aggiunto anche allo schema, e viceversa.

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
| `mvnw test` mostra `Skipped: 30` | I test del database non l'hanno trovato: controllare che sia acceso. |
| `DatabaseTest` fallisce con "Schema vecchio" o "non coincide con l'enum Ruolo" | Il database locale è di una versione precedente: `docker compose down -v` e poi `docker compose up -d --wait`. |
| "JavaFX runtime components are missing" | Si è avviato con il Play standard: usare `javafx:run` dal pannello Maven. |
| `mvnw` dice che non trova `JAVA_HOME` | Impostare la variabile d'ambiente `JAVA_HOME` sulla cartella della JDK 21 e riavviare IntelliJ. |
| Dopo un `pull` mancano classi o dipendenze | Pannello Maven, *Reload All Maven Projects*. |
| Ho cambiato lo schema ma il database non cambia | Gli script partono solo alla creazione: `docker compose down -v` e poi `docker compose up -d --wait`. |
| `mvnw test` fallisce con "MySQL Server locale non trovato" | I test del database cercano un MySQL Server installato sul PC. Se non c'è, escluderli con `.\mvnw test -DexcludedGroups=database`. |
---

By IDisagili.
