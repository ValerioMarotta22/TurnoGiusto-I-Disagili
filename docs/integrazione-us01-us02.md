# US01 e US02: consegna per l'integrazione

Il branch di lavoro è `feature/US01-US02`. Il team ha sospeso le interfacce:
la consegna riguarda repository, service e test. Per i file grafici conservati
e il loro eventuale ripristino vedere [interfacce-sospese.md](interfacce-sospese.md).

## Funzionalità disponibili

| Operazione | Punto di ingresso | Comportamento |
| --- | --- | --- |
| Registrazione US01 | `DipendenteService.registra(Dipendente)` | Valida anagrafica e contratto, inserisce entrambi nella stessa transazione e assegna gli ID agli oggetti dopo il commit. Gli ID iniziali devono essere zero. |
| Elenco condiviso | `DipendenteRepository.trovaTutti()` | Restituisce tutti i dipendenti, full-time e part-time, con il contratto valido oggi oppure `null`. Ordina per cognome, nome e ID; segnala contratti contemporaneamente validi anziché duplicare un dipendente o sceglierne uno arbitrariamente. |
| Aggiornamento US02 | `ContrattoService.aggiorna(dipendenteId, contrattoAttesoId, nuoviDati)` | Rilegge e blocca il contratto nel database, valida la proposta, salva nella stessa transazione e restituisce un `Esito` dopo il commit. |

I service sono i punti di ingresso per le operazioni complete. I metodi di scrittura
dei repository eseguono singoli comandi e non sostituiscono le validazioni dei service.
I repository che ricevono una `Connection` non la chiudono, non cambiano `autoCommit`
e non eseguono commit/rollback. `trovaPerAggiornamento` richiede `autoCommit=false`
per mantenere i blocchi fino alla fine della transazione.

## Regola delle date US02

- La decorrenza è oggi, nel fuso orario predefinito dell'applicazione.
  `nuoviDati.dataInizio` deve essere oggi; la fine può mancare oppure essere oggi o successiva.
- Se il contratto precedente è iniziato prima di oggi, viene chiuso ieri e viene
  inserita una nuova riga da oggi. Il dipendente collegato rimane lo stesso.
- Se è iniziato oggi, si corregge la stessa riga, mantenendo ID e inizio.
  È l'eccezione concordata: le versioni della stessa giornata non vengono conservate.
- Le date hanno estremi inclusivi. Si rifiutano sovrapposizioni con altri contratti,
  anche futuri e di tipo diverso; un errore annulla tutte le scritture della transazione.

`contrattoAttesoId` è l'ID letto prima di preparare la modifica. Se il contratto è stato
sostituito nel frattempo, il service chiede una nuova lettura. Questo controllo non rileva
una modifica concorrente che mantenga lo stesso ID nella correzione odierna.
Il service non modifica `nuoviDati` né un eventuale `Dipendente` già in memoria:
dopo il successo il chiamante deve usare `esito.contratto()` oppure rileggere i dati.

## Collegamenti ancora da integrare

La pianificazione non è implementata nel progetto locale. Il costruttore predefinito
di `ContrattoService` restituisce lo stato `NON_DISPONIBILE` quando le ore diminuiscono:
il salvataggio riesce, ma non viene dichiarata l'assenza di turni.

Quando la pianificazione sarà disponibile, fornire un'implementazione reale di
`VerificaTurniPianificati` al costruttore `ContrattoService(verificatore)`.
Deve cercare i turni del dipendente dalla decorrenza compresa, usando solo la
connessione ricevuta e lasciando al service la transazione. Una query fallita deve
propagare `SQLException`, non restituire `ASSENTI`.
Solo dopo il ritorno normale di `aggiorna`, `esito.richiedeRicontrolloTurni()` indica
se comunicare l'avviso: è vero soltanto con ore ridotte e stato `PRESENTI`.
Non è prevista la rigenerazione automatica dei turni.
Restano da verificare sui dati reali sia la ricerca dei turni sia l'uso dei nuovi
dati contrattuali nella pianificazione: questi collegamenti non sono dichiarati completati.

`AccessoDipendentiDisponibilita` è già nella parte del compagno e non viene duplicata
qui. All'unione dei rami va collegata con l'adattatore concordato: `trovaTutti()` può
delegare al repository; `verificaPartTime(Connection, int, LocalDate)` deve controllare
la data ricevuta e le sovrapposizioni tra tutti i tipi, prima di accettare un part-time.
La compatibilità di firme e regole non equivale a un test dell'integrazione tra i rami.

## Verifiche ripetibili

Con il MySQL di sviluppo avviato e configurato come nel README, eseguire dalla radice:

```powershell
.\mvnw.cmd -B clean test
```

I test di repository e dei service con database eseguono vere letture, scritture,
commit e rollback MySQL. Preparano dati propri e li eliminano al termine; non
richiedono di cancellare o ricreare il database. Un errore nell'apertura della
connessione li fa saltare: `BUILD SUCCESS` da solo non basta, controllare gli skip.

La logica dell'avviso US02 è provata con risposte simulate del verificatore dei turni,
anche nei test che usano MySQL per i contratti. Non è un test della pianificazione reale.
I test grafici sono intenzionalmente disabilitati insieme alle schermate.
