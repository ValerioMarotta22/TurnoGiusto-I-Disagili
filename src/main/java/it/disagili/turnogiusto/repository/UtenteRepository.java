package it.disagili.turnogiusto.repository;

import it.disagili.turnogiusto.model.TipoUtente;
import it.disagili.turnogiusto.model.Utente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * Legge gli account dalla tabella utente (solo SQL, nessuna regola). Serve all'accesso.
 */
public class UtenteRepository {

    /** L'utente insieme all'hash della sua password: il solo punto in cui l'hash esce dal database. */
    public record Credenziali(Utente utente, String hashPassword) {
    }

    private final FornitoreConnessioni connessioni;

    public UtenteRepository() {
        this(Database::getConnection);
    }

    public UtenteRepository(FornitoreConnessioni connessioni) {
        this.connessioni = Objects.requireNonNull(connessioni);
    }

    /**
     * Cerca un account dal nome utente. Optional.empty() se non esiste.
     * Il nome utente non distingue maiuscole e minuscole (lo decide la collation del database).
     */
    public Optional<Credenziali> trovaPerUsername(String username) throws SQLException {
        // LEFT JOIN: il titolare non ha un dipendente collegato, la sua riga deve comparire lo stesso.
        // Il nome e' preso dall'anagrafica del dipendente, cosi' non si duplica in due tabelle.
        // Il nome utente e' un parametro (?): MAI incollato nella query (SQL injection).
        String sql = """
                SELECT u.id, u.username, u.password_hash, u.tipo, u.dipendente_id,
                       CONCAT(d.nome, ' ', d.cognome) AS nome_dipendente
                FROM utente u
                LEFT JOIN dipendente d ON d.id = u.dipendente_id
                WHERE u.username = ?
                """;
        try (Connection connessione = connessioni.apri();
             PreparedStatement cerca = connessione.prepareStatement(sql)) {
            cerca.setString(1, username);
            try (ResultSet rs = cerca.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                TipoUtente tipo = TipoUtente.valueOf(rs.getString("tipo"));
                int dipendenteId = rs.getInt("dipendente_id");
                boolean senzaDipendente = rs.wasNull(); // il titolare ha dipendente_id NULL
                String nome = tipo == TipoUtente.TITOLARE ? "Titolare" : rs.getString("nome_dipendente");
                // partTime parte da false: lo calcola il service con i contratti.
                Utente utente = new Utente(rs.getInt("id"), rs.getString("username"), tipo,
                        senzaDipendente ? null : dipendenteId, nome, false);
                return Optional.of(new Credenziali(utente, rs.getString("password_hash")));
            }
        }
    }
}
