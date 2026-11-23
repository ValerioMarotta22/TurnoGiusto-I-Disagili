# US06A — Modifiche al codice esistente e merge

La US06A aggiunge i propri Model, Service, Controller, FXML e test. I file esistenti toccati sono tre.

- `DipendenteRepository.java` aggiunge `trovaTutti(LocalDate)`. Il metodo senza argomenti usa la stessa query passando la data di oggi, come in precedenza. Il Service riusa i repository di fabbisogni e disponibilità.
- `MainController.java` e `main-view.fxml` aggiungono la voce **Turni → Genera piano settimanale** con lo stesso caricamento usato dalle altre schermate.

Applicazione e test JDBC usano `Database.getConnection()` e la configurazione MySQL del `docker-compose.yml` condiviso. La generazione legge il database e produce la bozza in memoria. Schema, dati iniziali e configurazione Docker corrispondono a `main`. I test che creano contratti di prova li rimuovono al termine insieme alle relative anagrafiche di prova.

## Integrazione con Antonio e Valerio

1. **Modello condiviso.** `PianoSettimanale` contiene le righe `Turno` per servizio e ruolo, comprese quelle interamente scoperte. `AssegnazioneTurno` espone ID dipendente, inizio e fine come `LocalDateTime`. US06B può mostrare, modificare e salvare questo stesso piano tramite la propria persistenza.
2. **Controlli US07.** Collegare l'implementazione di Antonio tramite `VerificatorePropostaTurni` e passarla al costruttore di `GenerazioneTurniService`. Il metodo riceve il lunedì, le assegnazioni complete della proposta corrente, le assegnazioni confinanti e i contratti del periodo. Il verificatore può leggere questi dati e restituire l'elenco delle violazioni. Durante la generazione il Service passa la proposta già accettata più il candidato e ripete la verifica sul risultato complessivo.
3. **Storico e modifiche US06B.** Passare a `genera` le assegnazioni confinanti lette dalla persistenza, incluse quelle che attraversano la domenica e il lunedì ai due estremi. Dopo uno spostamento ricostruire il piano completo con la versione sostitutiva, quindi invocare `verificaProposta(piano, confinanti)` prima di salvare. Un'assegnazione che inizia nella settimana appartiene alla proposta. Il Service rifiuta di riceverla anche tra i confinanti.
4. **Menu e raccordi.** Se US06B modifica il menu, unificare la voce **Turni** e riusare il Service di generazione. Quando esisterà il contesto utente, collegare al punto di ingresso l'autorizzazione del titolare. La persistenza US06B sarà anche la fonte per il verificatore già previsto da US02, `VerificaTurniPianificati`, che cerca la presenza di turni attraverso la connessione ricevuta.

Nel branch attuale il controller usa il Service senza il verificatore US07 e apre una bozza senza storico persistito. I test del passaggio dati simulano gli esiti di US07. Dopo il merge, eseguire `clean verify` con MySQL in Docker e riprovare il flusso con il verificatore reale, con una cena che termina di lunedì e con una sostituzione manuale verificata prima del salvataggio.
