package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.repository.ContrattoRepository;
import it.disagili.turnogiusto.repository.Database;
import it.disagili.turnogiusto.repository.FornitoreConnessioni;
import it.disagili.turnogiusto.service.VerificaTurniPianificati.Stato;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;

/**
 * US02: programma il cambio dal primo lunedì del mese successivo al salvataggio.
 * Conserva la riga precedente, chiudendola alla domenica prima della decorrenza,
 * e inserisce una nuova riga anche quando il contratto precedente è iniziato oggi.
 * Tutte le letture e scritture usano la stessa transazione.
 */
public class ContrattoService {
    private final ContrattoRepository repository;
    private final VerificaTurniPianificati verificaTurni;
    // Clock fornisce data e fuso orario: nell'app usa l'orologio reale,
    // nei test può essere fermato per rendere stabile il significato di oggi.
    private final Clock clock;
    private final FornitoreConnessioni connessioni;

    /** La pianificazione non è ancora collegata: non fingiamo che non ci siano turni. */
    public ContrattoService() {
        this((connessione, id, data) -> Stato.NON_DISPONIBILE);
    }

    /** Il futuro componente di pianificazione può fornire il proprio verificatore. */
    public ContrattoService(VerificaTurniPianificati verificaTurni) {
        this(new ContrattoRepository(), verificaTurni, Clock.systemDefaultZone());
    }

    // Repository e orologio sostituibili nei test, senza cambiare le regole dei model.
    ContrattoService(ContrattoRepository repository, VerificaTurniPianificati verificaTurni, Clock clock) {
        this(repository, verificaTurni, clock, Database::getConnection);
    }

    /** Nei test la connessione può essere simulata, senza aprire MySQL. */
    ContrattoService(ContrattoRepository repository, VerificaTurniPianificati verificaTurni,
                     Clock clock, FornitoreConnessioni connessioni) {
        this.repository = Objects.requireNonNull(repository);
        this.verificaTurni = Objects.requireNonNull(verificaTurni);
        this.clock = Objects.requireNonNull(clock);
        this.connessioni = Objects.requireNonNull(connessioni);
    }

    /**
     * Restituisce il primo lunedì del mese successivo al salvataggio.
     * Non legge il database e non modifica alcun contratto.
     *
     * Riceve una data esplicita per poter verificare la regola
     * con test ripetibili, indipendenti dalla data reale.
     */
    public static LocalDate calcolaDecorrenza(LocalDate dataSalvataggio) {

        if (dataSalvataggio == null) {
            throw new IllegalArgumentException(
                    "La data di salvataggio è obbligatoria.");
        }

        // Individuiamo il giorno 1 del mese successivo.
        LocalDate primoDelMeseSuccessivo =
                dataSalvataggio.withDayOfMonth(1).plusMonths(1);

        // Se il giorno 1 è già lunedì, lo conserviamo.
        // Altrimenti avanziamo fino al primo lunedì disponibile.
        return primoDelMeseSuccessivo.with(
                TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Calcola l'ultimo giorno valido in caso di cessazione programmata.
     * Il contratto termina l'ultimo giorno del mese della richiesta, incluso.
     * Questa regola è distinta dalla decorrenza di un cambio contratto.
     *
     * Se esiste una scadenza precedente, la conserva:
     * una cessazione non deve prolungare il contratto.
     *
     * Questo metodo calcola soltanto una data, senza modificare
     * oggetti o salvare dati nel database.
     */
    public static LocalDate calcolaFineCessazione(
            LocalDate dataRichiesta, LocalDate scadenzaAttuale) {

        if (dataRichiesta == null) {
            throw new IllegalArgumentException("La data della richiesta è obbligatoria.");
        }
        LocalDate fineProgrammata = dataRichiesta.with(TemporalAdjusters.lastDayOfMonth());

        // null significa che il contratto non ha una scadenza.
        if (scadenzaAttuale != null
                && scadenzaAttuale.isBefore(fineProgrammata)) {
            return scadenzaAttuale;
        }

        return fineProgrammata;
    }

    /**
     * Risultato della cessazione, da restituire soltanto dopo il commit.
     * Conserva l'identità del contratto e il suo ultimo giorno valido.
     * Lo stato dei turni indica se occorre ricontrollare la pianificazione.
     */
    public record EsitoCessazione(
            int contrattoId,
            LocalDate ultimoGiornoValido,
            Stato statoTurni) {

        /** Esistono turni successivi alla fine del contratto da ricontrollare. */
        public boolean richiedeRicontrolloTurni() {
            return statoTurni == Stato.PRESENTI;
        }

        /** La verifica non è disponibile: non possiamo dichiarare il piano valido. */
        public boolean integrazionePianificazioneDaCompletare() {
            return statoTurni == Stato.NON_DISPONIBILE;
        }
    }

    /**
     * Risultato restituito solo dopo il commit. Chi chiama il service può leggere
     * i dati del contratto futuro e decidere come comunicare l'eventuale avviso sui turni.
     * Il service funziona anche senza schermate e non rigenera la pianificazione.
     */
    // Un record raccoglie i dati dell'esito e genera gli accessori (contratto(), ecc.).
    // Il Contratto contenuto rimane un oggetto del modello, quindi è ancora modificabile.
    // correzioneOdierna resta per compatibilità con i chiamanti: ora è sempre false.
    public record Esito(Contratto contratto, boolean oreRidotte, Stato statoTurni,
                        boolean correzioneOdierna) {
        /** Criterio 2: l'avviso è dovuto solo quando entrambe le condizioni sono vere. */
        public boolean richiedeRicontrolloTurni() {
            return oreRidotte && statoTurni == Stato.PRESENTI;
        }

        /** Distingue la verifica impossibile dalla verifica eseguita senza trovare turni. */
        public boolean integrazionePianificazioneDaCompletare() {
            return oreRidotte && statoTurni == Stato.NON_DISPONIBILE;
        }
    }

    /**
     * contrattoAttesoId identifica quello letto dal chiamante: la rilettura evita di
     * aggiornare silenziosamente un contratto già sostituito da un'altra operazione.
     * nuoviDati deve avere inizio nella decorrenza programmata; la fine è facoltativa
     * e non può precedere tale decorrenza.
     * L'oggetto ricevuto non viene modificato: l'esito contiene una nuova copia salvata.
     * Anche l'eventuale Dipendente già in memoria conserva il vecchio riferimento:
     * dopo il successo rileggere l'elenco mantiene visibile il contratto ancora vigente.
     * esito.contratto() descrive invece il nuovo contratto futuro, non quello attuale.
     *
     * @return dati salvati e decisione sull'avviso; nessun esito viene restituito su errore
     * @throws IllegalArgumentException se dati, decorrenza o selezione non sono validi
     * @throws IllegalStateException se il verificatore dei turni restituisce uno stato non ammesso
     * @throws SQLException se una delle operazioni sul database fallisce
     */
    public Esito aggiorna(int dipendenteId, int contrattoAttesoId, Contratto nuoviDati)
            throws SQLException {
        // Leggiamo oggi una volta sola: decorrenza e validazioni usano lo stesso riferimento.
        LocalDate oggi = LocalDate.now(clock);
        LocalDate decorrenza = calcolaDecorrenza(oggi);

        if (dipendenteId <= 0 || contrattoAttesoId <= 0) {
            throw new IllegalArgumentException("Seleziona un dipendente e un contratto registrati");
        }
        if (nuoviDati == null) {
            throw new IllegalArgumentException("I nuovi dati contrattuali sono obbligatori");
        }
        // Copiamo l'input: il chiamante non riceverà modifiche parziali in caso di errore.
        Contratto dati = new Contratto(0, dipendenteId, nuoviDati.getTipoContratto(),
                nuoviDati.getOreSettimanali(), nuoviDati.getCostoOrario(),
                nuoviDati.getDataInizio(), nuoviDati.getDataFine());
        // Le regole del modello restano condivise. La classe di supporto aggiunge soltanto
        // i limiti SQL sul costo, gli stessi già usati nella registrazione US01.
        List<String> errori = dati.valida();
        ValidazioneContratto.aggiungiLimitiPersistenza(dati, errori);

        if (!decorrenza.equals(dati.getDataInizio())) {
            errori.add("Il cambio contratto deve decorrere dal " + decorrenza);
        }
        // Lo zero è ammesso per un oggetto di proposta non ancora salvato.
        // Un collegamento già valorizzato deve invece corrispondere alla selezione.
        if (nuoviDati.getDipendenteId() != 0 && nuoviDati.getDipendenteId() != dipendenteId) {
            errori.add("Il contratto deve appartenere allo stesso dipendente");
        }
        if (nuoviDati.getId() != 0 && nuoviDati.getId() != contrattoAttesoId) {
            errori.add("I dati non corrispondono al contratto selezionato");
        }
        // Tutti i problemi dei dati compilati vengono comunicati prima di aprire il DB.
        if (!errori.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errori));
        }

        // Il service possiede questa connessione: il try la chiude automaticamente.
        // I repository e il verificatore la ricevono in prestito e non devono chiuderla.
        try (Connection connessione = Database.getConnection()) {
            // Nessuna modifica viene confermata singolarmente: decidiamo noi il commit.
            connessione.setAutoCommit(false);
            boolean correzioneOdierna;
            boolean oreRidotte;
            Stato statoTurni = Stato.NON_RICHIESTA;
            int idSalvato;
            try {
                // Rileggiamo il database, non ci fidiamo di un oggetto della schermata
                // caricato prima. Il repository blocca le righe fino a commit/rollback.
                List<Contratto> contratti = repository.trovaPerAggiornamento(connessione, dipendenteId);
                // isValidoIl include sia il giorno iniziale sia quello finale.
                List<Contratto> validi = contratti.stream().filter(c -> c.isValidoIl(oggi)).toList();
                if (validi.isEmpty()) {
                    throw new IllegalArgumentException("Il dipendente non ha un contratto valido oggi");
                }
                if (validi.size() > 1) {
                    throw new IllegalArgumentException("Esistono più contratti contemporaneamente validi");
                }
                // Arrivati qui sappiamo che esiste esattamente un contratto attuale.
                Contratto precedente = validi.getFirst();
                if (precedente.getId() != contrattoAttesoId) {
                    // L'ID selezionato deve essere ancora quello del contratto vigente.
                    // L'ID di un contratto futuro non può sostituirlo in questa operazione.
                    throw new IllegalArgumentException("Il contratto è cambiato: aggiorna l'elenco e riprova");
                }

                // Non prolunghiamo automaticamente un contratto che scade
                // prima della decorrenza prevista: quel caso richiede una decisione dedicata.
                if (precedente.getDataFine() != null
                        && precedente.getDataFine().isBefore(decorrenza.minusDays(1))) {
                    throw new IllegalArgumentException(
                            "Il contratto attuale scade prima della nuova decorrenza.");
                }

                // Controlliamo anche eventuali contratti futuri, senza filtrare per tipo.
                for (Contratto altro : contratti) {
                    if (altro.getId() != precedente.getId() && siSovrappongono(dati, altro)) {
                        throw new IllegalArgumentException("Il nuovo periodo si sovrappone a un altro contratto");
                    }
                }
                // Il confronto usa il valore riletto e bloccato nella transazione.
                // Se le ore restano uguali o aumentano, non interroghiamo i turni.
                oreRidotte = dati.getOreSettimanali() < precedente.getOreSettimanali();
                if (oreRidotte) {
                    // Usiamo i dati riletti, non valori delle ore provenienti dalla schermata.
                    // La riduzione interessa i turni dalla decorrenza del nuovo contratto.
                    statoTurni = verificaTurni.verifica(connessione, dipendenteId, decorrenza);
                    // Un verificatore deve distinguere presente, assente e non disponibile.
                    // NON_RICHIESTA è riservato al ramo in cui non viene chiamato.
                    if (statoTurni == null || statoTurni == Stato.NON_RICHIESTA) {
                        throw new IllegalStateException("Esito della verifica dei turni non valido");
                    }
                }

                // Non correggiamo più i dati della riga precedente.
                // Manteniamo temporaneamente questo campo dell'esito per compatibilità.
                correzioneOdierna = false;

                // Il contratto precedente conserva ID, tipo, ore, costo e data iniziale.
                // Cambia soltanto la fine: la domenica prima della nuova decorrenza.
                repository.chiudi(
                        connessione,
                        dipendenteId,
                        precedente.getId(),
                        decorrenza.minusDays(1));

                // Il nuovo contratto viene salvato come una nuova riga.
                // Chiusura e inserimento appartengono alla stessa transazione.
                idSalvato = repository.inserisci(connessione, dipendenteId, dati);

                // Solo qui entrambe le operazioni diventano definitive e visibili
                // alle altre connessioni, comprese quelle usate dalla pianificazione.
                connessione.commit();
            } catch (SQLException | RuntimeException errore) {
                // Annulliamo anche un UPDATE già riuscito se il successivo INSERT fallisce.
                try {
                    connessione.rollback();
                } catch (SQLException erroreRollback) {
                    // Non perdiamo l'errore originale se fallisce anche il rollback.
                    errore.addSuppressed(erroreRollback);
                }
                throw errore;
            }
            // L'esito di successo viene costruito dopo il commit; l'input è rimasto intatto.
            dati.setId(idSalvato);
            return new Esito(dati, oreRidotte, statoTurni, correzioneOdierna);
        }
    }

    /**
     * Programma la cessazione conservando la riga del contratto nello storico.
     * Rilegge i dati sotto blocco e rifiuta cambi futuri già programmati.
     * L'esito è restituito solo dopo il commit; gli errori annullano la modifica.
     */
    public EsitoCessazione cessa(int dipendenteId, int contrattoAttesoId) throws SQLException {
        if (dipendenteId <= 0 || contrattoAttesoId <= 0) {
            throw new IllegalArgumentException("Seleziona un dipendente e un contratto registrati.");
        }
        LocalDate oggi = LocalDate.now(clock);
        try (Connection connessione = connessioni.apri()) {
            connessione.setAutoCommit(false);
            try {
                List<Contratto> contratti = repository.trovaPerAggiornamento(connessione, dipendenteId);
                Contratto attuale = selezionaContrattoDaCessare(contratti, contrattoAttesoId, oggi);
                for (Contratto altro : contratti) {
                    // Non annulliamo automaticamente un accordo futuro già registrato.
                    if (altro.getDataInizio().isAfter(oggi)) {
                        throw new IllegalArgumentException(
                                "Esiste un contratto futuro: risolvi prima il cambio programmato.");
                    }
                }
                LocalDate fine = calcolaFineCessazione(oggi, attuale.getDataFine());
                // La cena dell'ultimo giorno rimane ammessa. Il collegamento ai turni
                // deve cercare assegnazioni che iniziano dal giorno successivo.
                Stato stato = verificaTurni.verifica(connessione, dipendenteId, fine.plusDays(1));
                if (stato == null || stato == Stato.NON_RICHIESTA) {
                    throw new IllegalStateException("Esito della verifica dei turni non valido");
                }
                // Se la data è già quella corretta, non serve ripetere l'UPDATE.
                // Nessun INSERT o DELETE: ID e dati storici restano conservati.
                if (!fine.equals(attuale.getDataFine())) {
                    repository.chiudi(connessione, dipendenteId, attuale.getId(), fine);
                }
                connessione.commit();
                return new EsitoCessazione(attuale.getId(), fine, stato);
            } catch (SQLException | RuntimeException errore) {
                try {
                    connessione.rollback();
                } catch (SQLException erroreRollback) {
                    errore.addSuppressed(erroreRollback);
                }
                throw errore;
            }
        }
    }

    /**
     * Individua il contratto attivo alla data della richiesta.
     * La lista deve provenire dalla rilettura nella transazione.
     * Controlla anche che il contratto sia ancora quello selezionato
     * dal chiamante, evitando di cessarne un altro per errore.
     */
    private Contratto selezionaContrattoDaCessare(
            List<Contratto> contratti,
            int contrattoAttesoId,
            LocalDate oggi) {

        // Gli estremi sono inclusivi: un contratto che scade oggi
        // è ancora valido oggi.
        List<Contratto> validi = contratti.stream()
                .filter(contratto -> contratto.isValidoIl(oggi))
                .toList();

        if (validi.isEmpty()) {
            throw new IllegalArgumentException(
                    "Il dipendente non ha un contratto valido oggi.");
        }

        // Non scegliamo arbitrariamente tra contratti sovrapposti.
        if (validi.size() > 1) {
            throw new IllegalArgumentException(
                    "Esistono più contratti contemporaneamente validi.");
        }

        Contratto attuale = validi.getFirst();
        if (attuale.getId() != contrattoAttesoId) {
            throw new IllegalArgumentException(
                    "Il contratto è cambiato: aggiorna l'elenco e riprova.");
        }

        return attuale;
    }

    // Due intervalli inclusivi si intersecano se ciascuno inizia prima della fine
    // dell'altro (o nello stesso giorno). Una fine null equivale a nessuna scadenza.
    private boolean siSovrappongono(Contratto primo, Contratto secondo) {
        return (primo.getDataFine() == null || !secondo.getDataInizio().isAfter(primo.getDataFine()))
                && (secondo.getDataFine() == null || !primo.getDataInizio().isAfter(secondo.getDataFine()));
    }
}
