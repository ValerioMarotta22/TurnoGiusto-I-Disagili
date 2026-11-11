package it.disagili.turnogiusto.repository;

import it.disagili.turnogiusto.model.Disponibilita;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DisponibilitaRepository {

    private final FornitoreConnessioni connessioni;

    public DisponibilitaRepository() {
        this(Database::getConnection);
    }

    public DisponibilitaRepository(FornitoreConnessioni connessioni) {
        this.connessioni = Objects.requireNonNull(connessioni);
    }

    /**
     * Legge le disponibilita del dipendente, ordinate per giorno e orario.
     * Restituisce una lista vuota se non trova righe per l'ID indicato.
     * Non verifica l'esistenza del dipendente o il suo contratto.
     */
    public List<Disponibilita> trovaPerDipendente(int dipendenteId)
            throws SQLException {

        if (dipendenteId <= 0) {
            throw new IllegalArgumentException(
                    "L'ID del dipendente deve essere maggiore di zero");
        }

        String sql = """
                SELECT id, dipendente_id, giorno_settimana, ora_inizio, ora_fine
                FROM disponibilita
                WHERE dipendente_id = ?
                ORDER BY giorno_settimana, ora_inizio, ora_fine, id
                """;

        List<Disponibilita> disponibilita = new ArrayList<>();

        try (Connection connessione = connessioni.apri();
             PreparedStatement statement = connessione.prepareStatement(sql)) {

            statement.setInt(1, dipendenteId);

            try (ResultSet risultati = statement.executeQuery()) {
                while (risultati.next()) {
                    Disponibilita finestra = new Disponibilita(
                            risultati.getInt("id"),
                            risultati.getInt("dipendente_id"),
                            DayOfWeek.of(risultati.getInt("giorno_settimana")),
                            risultati.getTime("ora_inizio").toLocalTime(),
                            risultati.getTime("ora_fine").toLocalTime()
                    );

                    disponibilita.add(finestra);
                }
            }
        }

        return disponibilita;
    }
}
