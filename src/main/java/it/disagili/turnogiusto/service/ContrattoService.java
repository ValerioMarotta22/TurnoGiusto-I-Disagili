package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.repository.ContrattoRepository;
import it.disagili.turnogiusto.repository.Database;
import it.disagili.turnogiusto.service.VerificaTurniPianificati.Stato;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * US02: aggiorna con decorrenza da oggi. Un contratto iniziato prima viene chiuso
 * ieri e sostituito; uno iniziato oggi viene corretto sulla stessa riga.
 * Tutte le letture e scritture usano la stessa transazione.
 */
public class ContrattoService {
    private final ContrattoRepository repository;
    private final VerificaTurniPianificati verificaTurni;
    // Clock fornisce data e fuso orario: nell'app usa l'orologio reale,
    // nei test può essere fermato per rendere stabile il significato di oggi.
    private final Clock clock;

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
        this.repository = Objects.requireNonNull(repository);
        this.verificaTurni = Objects.requireNonNull(verificaTurni);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Risultato restituito solo dopo il commit. Chi chiama il service può leggere
     * i dati salvati e decidere come comunicare l'eventuale avviso sui turni.
     * Il service funziona anche senza schermate e non rigenera la pianificazione.
     */
    // Un record raccoglie i dati dell'esito e genera gli accessori (contratto(), ecc.).
    // Il Contratto contenuto rimane un oggetto del modello, quindi è ancora modificabile.
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
     * nuoviDati deve avere inizio oggi; la fine è facoltativa e non può precedere oggi.
     * L'oggetto ricevuto non viene modificato: l'esito contiene una nuova copia salvata.
     * Anche l'eventuale Dipendente già in memoria conserva il vecchio riferimento:
     * dopo il successo il chiamante può usare esito.contratto() oppure rileggere l'elenco.
     *
     * @return dati salvati e decisione sull'avviso; nessun esito viene restituito su errore
     * @throws IllegalArgumentException se dati, decorrenza o selezione non sono validi
     * @throws IllegalStateException se il verificatore dei turni restituisce uno stato non ammesso
     * @throws SQLException se una delle operazioni sul database fallisce
     */
    public Esito aggiorna(int dipendenteId, int contrattoAttesoId, Contratto nuoviDati)
            throws SQLException {
        // La decorrenza viene stabilita una volta all'inizio dell'operazione.
        // Non sono ammesse modifiche retroattive o programmate per giorni futuri.
        LocalDate oggi = LocalDate.now(clock);
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
        if (!oggi.equals(dati.getDataInizio())) {
            errori.add("Il cambio contratto deve decorrere dalla data odierna");
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
                    // Rileva una sostituzione con un altro ID. Una correzione sulla
                    // stessa riga conserva l'ID: questo non è un controllo di versione
                    // che rileva qualunque modifica contemporanea dei singoli campi.
                    throw new IllegalArgumentException("Il contratto è cambiato: aggiorna l'elenco e riprova");
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
                    statoTurni = verificaTurni.verifica(connessione, dipendenteId, oggi);
                    // Un verificatore deve distinguere presente, assente e non disponibile.
                    // NON_RICHIESTA è riservato al ramo in cui non viene chiamato.
                    if (statoTurni == null || statoTurni == Stato.NON_RICHIESTA) {
                        throw new IllegalStateException("Esito della verifica dei turni non valido");
                    }
                }
                correzioneOdierna = precedente.getDataInizio().equals(oggi);
                if (correzioneOdierna) {
                    // Con sole date giornaliere non possiamo creare due versioni valide
                    // nello stesso giorno: per accordo correggiamo la riga esistente.
                    // Non conserviamo quindi le sue versioni infragiornaliere.
                    repository.correggiOdierno(connessione, dipendenteId, precedente.getId(), dati);
                    idSalvato = precedente.getId();
                } else {
                    // Esempio: il nuovo parte l'11, il precedente termina il 10.
                    // Chiuderlo l'11 creerebbe una sovrapposizione per le date inclusive.
                    repository.chiudi(connessione, dipendenteId, precedente.getId(), oggi.minusDays(1));
                    idSalvato = repository.inserisci(connessione, dipendenteId, dati);
                }
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

    // Due intervalli inclusivi si intersecano se ciascuno inizia prima della fine
    // dell'altro (o nello stesso giorno). Una fine null equivale a nessuna scadenza.
    private boolean siSovrappongono(Contratto primo, Contratto secondo) {
        return (primo.getDataFine() == null || !secondo.getDataInizio().isAfter(primo.getDataFine()))
                && (secondo.getDataFine() == null || !primo.getDataInizio().isAfter(secondo.getDataFine()));
    }
}
