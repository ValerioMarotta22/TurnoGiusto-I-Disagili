package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.repository.ContrattoRepository;
import it.disagili.turnogiusto.repository.Database;
import it.disagili.turnogiusto.repository.DipendenteRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Coordina validazione e registrazione; i repository eseguono i singoli comandi SQL. */
public class DipendenteService {

    private final DipendenteRepository dipendenteRepository;
    private final ContrattoRepository contrattoRepository;

    /** Costruttore usato dall'applicazione: crea i repository ordinari. */
    public DipendenteService() {
        this(new DipendenteRepository(), new ContrattoRepository());
    }

    /**
     * Accessibile nello stesso pacchetto: i test possono fornire un repository
     * che genera un errore controllato, per verificare il rollback del service.
     */
    DipendenteService(DipendenteRepository dipendenteRepository,
                      ContrattoRepository contrattoRepository) {
        this.dipendenteRepository = dipendenteRepository;
        this.contrattoRepository = contrattoRepository;
    }

    /**
     * Registra un nuovo dipendente insieme al suo primo contratto.
     * Aggiorna gli ID negli oggetti soltanto dopo il commit.
     * Un ritorno normale conferma il salvataggio di entrambe le righe collegate.
     *
     * @throws IllegalArgumentException se i dati non sono validi o gli ID sono già assegnati
     * @throws SQLException se l'accesso al database o il salvataggio fallisce
     */

    public void registra(Dipendente dipendente) throws SQLException {
        // Prima di accedere al database, controlliamo presenza e validità dei dati.
        if (dipendente == null) {
            throw new IllegalArgumentException("Il dipendente è obbligatorio");
        }

        // Riutilizziamo le regole dei model, senza modificarli, e aggiungiamo qui
        // i limiti necessari alla persistenza nel nostro schema MySQL.
        List<String> errori = dipendente.valida();

        String nome = dipendente.getNome();
        String cognome = dipendente.getCognome();

        // VARCHAR(50) ammette 50 caratteri: codePointCount considera anche quelli
        // rappresentati da due char Java. && evita di contare una stringa null.
        if (nome != null && nome.codePointCount(0, nome.length()) > 50) {
            errori.add("Il nome non può superare 50 caratteri");
        }

        if (cognome != null && cognome.codePointCount(0, cognome.length()) > 50) {
            errori.add("Il cognome non può superare 50 caratteri");
        }

        // Stessi controlli del costo usati da US02, raccolti in un unico punto.
        ValidazioneContratto.aggiungiLimitiPersistenza(dipendente.getContratto(), errori);


        // Comunichiamo tutti gli errori raccolti, uno per riga, prima di aprire il DB.
        if (!errori.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", errori));
        }

        Contratto contratto = dipendente.getContratto();

        // Questo metodo serve alla registrazione di oggetti nuovi.
        if (dipendente.getId() != 0
                || contratto.getId() != 0
                || contratto.getDipendenteId() != 0) {
            throw new IllegalArgumentException(
                    "La registrazione richiede un dipendente e un contratto nuovi"
            );
        }

        try (Connection connessione = Database.getConnection()) {
            // Gli inserimenti saranno confermati solo dal nostro commit.
            connessione.setAutoCommit(false);

            int dipendenteId;
            int contrattoId;

            try {
                dipendenteId = dipendenteRepository.inserisci(
                        connessione, dipendente
                );

                contrattoId = contrattoRepository.inserisci(
                        connessione, dipendenteId, contratto
                );

                // Stessa connessione per entrambi gli INSERT: confermiamo tutto insieme.
                connessione.commit();
            } catch (SQLException | RuntimeException errore) {
                // Un errore SQL o Java annulla le modifiche non ancora confermate.
                try {
                    connessione.rollback();
                } catch (SQLException erroreRollback) {
                    // Conserviamo l'errore originale e gli associamo anche il fallimento
                    // del rollback: nessuna delle due informazioni va persa.
                    errore.addSuppressed(erroreRollback);
                }

                throw errore;
            }

            // Il salvataggio è stato confermato: aggiorniamo gli oggetti Java.
            dipendente.setId(dipendenteId);
            contratto.setId(contrattoId);
            contratto.setDipendenteId(dipendenteId);
        }

    }

}



