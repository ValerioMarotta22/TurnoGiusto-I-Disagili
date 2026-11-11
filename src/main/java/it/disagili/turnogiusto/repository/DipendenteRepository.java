package it.disagili.turnogiusto.repository;

import it.disagili.turnogiusto.model.Dipendente;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.sql.ResultSet;
import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.TipoContratto;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.sql.Statement;


/**
 * Legge i dipendenti dal database e traduce le righe SQL negli oggetti del modello.
 * Il repository si occupa dell'accesso ai dati: chi lo chiama riceve oggetti Java
 * e non deve conoscere la query o il funzionamento di JDBC.
 * JDBC è l'insieme delle API Java usate qui per comunicare con MySQL.
 */
public class DipendenteRepository {
    /**
     * Restituisce tutti i dipendenti, ordinati per cognome, nome e ID.
     * Ogni dipendente contiene il contratto valido oggi, oppure null se non ne ha.
     * Le date di inizio e fine sono comprese nel periodo di validità.
     *
     * @return lista dei dipendenti; lista vuota se la tabella dipendente è vuota
     * @throws SQLException se la lettura fallisce oppure un dipendente ha più
     *                      contratti contemporaneamente validi
     */
    public List<Dipendente> trovaTutti() throws SQLException {
        // Le triple virgolette delimitano una stringa Java su più righe (text block).
        // Questa assegnazione prepara il testo SQL: non esegue ancora la query.
        // d e c sono abbreviazioni dei nomi delle tabelle. AS assegna nomi distinti
        // ai due ID, così nel ResultSet potremo distinguere dipendente e contratto.
        // LEFT JOIN mantiene anche i dipendenti senza un contratto valido:
        // in quel caso le colonne del contratto nel risultato saranno NULL.
        // I filtri sulle date sono nella ON per limitare i contratti associati
        // senza eliminare dall'elenco i dipendenti che non hanno corrispondenze.
        // I due ? sono parametri: saranno entrambi sostituiti dalla data odierna.
        // ORDER BY usa l'ID come ultimo criterio per ordinare anche gli omonimi.
        String sql = """
                SELECT d.id AS dipendente_id,
                       d.nome,
                       d.cognome,
                       d.ruolo,
                       c.id AS contratto_id,
                       c.tipo,
                       c.ore_settimanali,
                       c.costo_orario,
                       c.data_inizio,
                       c.data_fine
                FROM dipendente d
                LEFT JOIN contratto c
                     ON c.dipendente_id = d.id
                     AND c.data_inizio <= ?
                     AND (c.data_fine IS NULL OR c.data_fine >= ?)
                ORDER BY d.cognome, d.nome, d.id
                """;
        // Calcoliamo oggi una sola volta, usando il fuso orario predefinito di Java.
        // I due parametri avranno così la stessa data anche a cavallo della mezzanotte.
        LocalDate oggi = LocalDate.now();

        // try-with-resources chiude automaticamente statement e connessione,
        // anche se si verifica un errore o si esegue il return dentro il blocco.
        // Database.getConnection() usa le impostazioni di connessione del progetto.
        // PreparedStatement permette di fornire separatamente SQL e parametri.
        try (Connection connessione = Database.getConnection();
             PreparedStatement statement = connessione.prepareStatement(sql)) {

            // Gli indici JDBC partono da 1: il primo ? controlla l'inizio,
            // il secondo la fine. Date.valueOf converte LocalDate in java.sql.Date.
            statement.setDate(1, Date.valueOf(oggi));
            statement.setDate(2, Date.valueOf(oggi));

            // executeQuery invia la SELECT al database e restituisce le righe
            // tramite un ResultSet, che verrà chiuso dal suo try-with-resources.
            try (ResultSet risultati = statement.executeQuery()) {
                // La lista conserva l'ordine restituito dalla query.
                List<Dipendente> dipendenti = new ArrayList<>();
                // Il Set memorizza gli ID già incontrati e permette di rilevare doppioni.
                Set<Integer> idLetti = new HashSet<>();

                // All'inizio il cursore è prima della prima riga. Ogni next() avanza
                // alla riga successiva; restituisce false quando non ce ne sono più.
                while (risultati.next()) {
                    int dipendenteId = risultati.getInt("dipendente_id");

                    // Set.add restituisce false se l'ID era già presente; ! inverte
                    // il risultato. Qui una seconda riga per lo stesso ID significa
                    // che la JOIN ha trovato più contratti validi per quel dipendente.
                    // Poiché il modello ne contiene uno solo, segnaliamo l'incoerenza.
                    if (!idLetti.add(dipendenteId)) {
                        throw new SQLException(
                                "Più contratti validi per il dipendente " + dipendenteId

                        );
                    }

                    // Valore iniziale per un dipendente senza contratto valido oggi.
                    Contratto contratto = null;

                    // getObject mantiene il NULL SQL come null Java. Un getInt da solo
                    // restituirebbe 0 per NULL e richiederebbe un ulteriore wasNull().
                    if (risultati.getObject("contratto_id") != null) {
                        // Una fine null indica un contratto senza scadenza.
                        Date dataFine = risultati.getDate("data_fine");

                        // Ricostruiamo il contratto usando gli ID già assegnati da MySQL.
                        // valueOf converte, ad esempio, "PART_TIME" nell'enum corrispondente:
                        // i nomi memorizzati nel database devono coincidere con le enum Java.
                        // getBigDecimal conserva il costo DECIMAL senza passare da double.
                        // toLocalDate converte le date JDBC nel tipo usato dal modello.
                        // L'espressione condizione ? valoreSeVero : valoreSeFalso evita
                        // di chiamare toLocalDate() su una data di fine assente.
                        contratto = new Contratto(
                                risultati.getInt("contratto_id"),
                                dipendenteId,
                                TipoContratto.valueOf(risultati.getString("tipo")),
                                risultati.getInt("ore_settimanali"),
                                risultati.getBigDecimal("costo_orario"),
                                risultati.getDate("data_inizio").toLocalDate(),
                                dataFine == null ? null : dataFine.toLocalDate()

                        );
                    }

                    // Costruiamo l'anagrafica e associamo il contratto appena letto.
                    // Se non c'era un contratto valido, l'ultimo argomento rimane null.
                    Dipendente dipendente = new Dipendente(
                            dipendenteId,
                            risultati.getString("nome"),
                            risultati.getString("cognome"),
                            Ruolo.valueOf(risultati.getString("ruolo")),
                            contratto
                    );

                    // Aggiungiamo un oggetto per ogni dipendente trovato.
                    dipendenti.add(dipendente);
                }

                // Se non ci sono righe, il ciclo non viene eseguito e la lista è vuota.
                // Prima di uscire, i try chiudono le risorse: gli oggetti della lista
                // restano utilizzabili anche dopo la chiusura della connessione.
                return dipendenti;

            }

        }

    }

    /**
     * Inserisce soltanto l'anagrafica e restituisce l'ID assegnato dal database.
     * Il service deve avere già validato i dati e aperto la transazione: userà
     * questo ID per collegare il primo contratto, prima di confermare tutto insieme.
     * Non modifica l'oggetto ricevuto e non chiude o configura la connessione.
     * Non esegue commit/rollback: un INSERT riuscito può ancora essere annullato.
     */
    public int inserisci(Connection connessione, Dipendente dipendente)
            throws SQLException {

        // L'ID non è tra le colonne: lo genera MySQL con AUTO_INCREMENT.
        // I punti interrogativi separano i valori dal testo del comando SQL.
        String sql = """
                INSERT INTO dipendente (nome, cognome, ruolo)
                VALUES(?, ?, ?)
                """;

        // Chiudiamo soltanto le risorse create qui. La connessione ricevuta resta
        // disponibile al service per inserire anche il contratto nella stessa transazione.
        try (PreparedStatement statement = connessione.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {

            // name() scrive il valore ENUM del database (es. AIUTO_CUOCO);
            // toString() produrrebbe invece l'etichetta leggibile per una schermata.
            statement.setString(1, dipendente.getNome());
            statement.setString(2, dipendente.getCognome());
            statement.setString(3, dipendente.getRuolo().name());

            // executeUpdate restituisce il numero di righe interessate, non l'ID.
            int righeInserite = statement.executeUpdate();

            if (righeInserite != 1) {
                throw new SQLException(
                        "inserimento del dipendente non riuscito"
                );
            }
            // RETURN_GENERATED_KEYS, richiesto sopra, rende disponibile l'ID.
            // Il service lo assegnerà all'oggetto Java solo dopo il commit finale.
            try (ResultSet chiavi = statement.getGeneratedKeys()) {
                if (!chiavi.next()) {
                    throw new SQLException(
                            "ID del nuovo dipendente non disponibile"
                    );
                }

                return chiavi.getInt(1);
            }

        }
    }
}

