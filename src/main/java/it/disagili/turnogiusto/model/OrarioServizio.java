package it.disagili.turnogiusto.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/** Orari di un servizio. Se l'ora di fine è minore, il servizio termina il giorno dopo. */
public record OrarioServizio(LocalTime inizio, LocalTime fine) {
    public OrarioServizio {
        Objects.requireNonNull(inizio, "L'orario iniziale è obbligatorio");
        Objects.requireNonNull(fine, "L'orario finale è obbligatorio");
        if (inizio.equals(fine)) {
            throw new IllegalArgumentException("Inizio e fine di un servizio devono essere diversi.");
        }
    }

    /** Restituisce la data e l'ora di fine per il servizio della giornata indicata. */
    public LocalDateTime fineDelServizio(LocalDate giorno) {
        LocalDate giornoFine = fine.isBefore(inizio) ? giorno.plusDays(1) : giorno;
        return giornoFine.atTime(fine);
    }
}
