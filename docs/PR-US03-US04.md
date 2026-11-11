# Titolo proposto

US03/US04: disponibilità, test JDBC autonomi e sospensione della UI

# Descrizione

Implementa lettura, validazione, salvataggio transazionale e consultazione delle
disponibilità dei dipendenti part-time. Gestisce finestre oltre mezzanotte,
elenchi vuoti, contratti non idonei e rollback in caso di errore.

I test JDBC preparano un database separato anche quando avviati direttamente
da IntelliJ, senza dipendere da profili Maven o variabili d'ambiente. Repository
e service accettano un fornitore di connessioni per i test; i costruttori
ordinari continuano a usare la configurazione dell'applicazione. Il catalogo
di prova viene verificato prima della preparazione e della pulizia dei dati.

La UI JavaFX è sospesa in attesa del CSS condiviso del team. Il lavoro grafico
resta nei commenti; il relativo test è archiviato fuori dalle sorgenti eseguibili.
Sono incluse configurazioni di avvio condivise, istruzioni per il ripristino e
un workflow GitHub Actions con JDK 21 e MySQL 8.4 tramite Compose.

## Validazione locale

- `mvnw.cmd clean verify`: 55 test eseguiti, nessun fallimento, errore o test saltato.
- IntelliJ, `Tutti i test (JUnit)`: 55 test superati, uscita 0.
- IntelliJ, `DisponibilitaRepositoryTest`: 11 test superati, uscita 0.
- Singolo metodo JDBC avviato direttamente, anche con `MYSQL_*` errate: superato.
- Configurazione `US03-US04 - Test senza database`: esecuzione riuscita.
- JAR verificato: non contiene le vecchie viste, il vecchio CSS o classi di test.

Le verifiche JDBC locali usano MySQL 26.7.0, disponibile sul PC. Il controllo
Docker/MySQL 8.4 deve completarsi nel workflow dopo il push. I test grafici
restano sospesi; l'integrazione con navigazione, CSS e repository del team
va completata insieme ai rispettivi branch.
