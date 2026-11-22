package it.disagili.turnogiusto.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** Intervallo di lavoro di un dipendente, condiviso con il controllo dei vincoli. */
public record AssegnazioneTurno(int dipendenteId, LocalDateTime inizio, LocalDateTime fine) {
    public AssegnazioneTurno {
        Objects.requireNonNull(inizio, "L'inizio dell'assegnazione è obbligatorio");
        Objects.requireNonNull(fine, "La fine dell'assegnazione è obbligatoria");
        if (dipendenteId <= 0 || !fine.isAfter(inizio)) {
            throw new IllegalArgumentException("Servono un dipendente registrato e un intervallo valido.");
        }
    }

    /** Gli intervalli sono chiusi all'inizio e aperti alla fine. */
    public boolean siSovrappone(AssegnazioneTurno altra) {
        return inizio.isBefore(altra.fine()) && altra.inizio().isBefore(fine);
    }
}
