package it.disagili.turnogiusto.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Una riga del piano per servizio, ruolo e intervallo. Conserva anche i posti non assegnati. */
public record Turno(Servizio servizio, Ruolo ruolo, LocalDateTime inizio, LocalDateTime fine,
                    int postiRichiesti, List<AssegnazioneTurno> assegnazioni) {
    public Turno {
        Objects.requireNonNull(servizio);
        Objects.requireNonNull(ruolo);
        Objects.requireNonNull(inizio);
        Objects.requireNonNull(fine);
        assegnazioni = List.copyOf(assegnazioni);
        if (!fine.isAfter(inizio) || postiRichiesti < 0) {
            throw new IllegalArgumentException("Intervallo o numero di posti del turno non valido.");
        }
        Set<Integer> dipendenti = new HashSet<>();
        for (AssegnazioneTurno assegnazione : assegnazioni) {
            // Ogni assegnazione copre l'intera riga e occupa un solo posto per dipendente.
            if (!inizio.equals(assegnazione.inizio()) || !fine.equals(assegnazione.fine())
                    || !dipendenti.add(assegnazione.dipendenteId())) {
                throw new IllegalArgumentException("Assegnazione fuori orario o dipendente ripetuto nel turno.");
            }
        }
    }

    public int postiScoperti() {
        return Math.max(0, postiRichiesti - assegnazioni.size());
    }
}
