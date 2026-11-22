package it.disagili.turnogiusto.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/** Bozza settimanale condivisa tra generazione, visualizzazione e verifica. */
public record PianoSettimanale(LocalDate inizioSettimana, List<Turno> turni) {
    public PianoSettimanale {
        verificaInizioSettimana(inizioSettimana);
        turni = List.copyOf(turni);
        for (Turno turno : turni) {
            LocalDate giorno = turno.inizio().toLocalDate();
            if (giorno.isBefore(inizioSettimana) || !giorno.isBefore(inizioSettimana.plusWeeks(1))) {
                throw new IllegalArgumentException("Il turno deve iniziare nella settimana del piano.");
            }
        }
    }

    /** La pianificazione usa settimane da lunedì a domenica. */
    public static void verificaInizioSettimana(LocalDate data) {
        if (data == null || data.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("Seleziona il lunedì della settimana da pianificare.");
        }
    }

    /** Estrae tutte le assegnazioni dello stato proposto, indipendentemente dalla riga. */
    public List<AssegnazioneTurno> assegnazioni() {
        return turni.stream().flatMap(turno -> turno.assegnazioni().stream()).toList();
    }

    public int postiScoperti() {
        return turni.stream().mapToInt(Turno::postiScoperti).sum();
    }
}
