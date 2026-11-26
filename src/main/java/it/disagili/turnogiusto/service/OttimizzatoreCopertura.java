package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Turno;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * US06A: recupera le scoperture lasciate dalla prima assegnazione cronologica.
 * I candidati rispettano gia ruolo, contratto, disponibilita e storico.
 * Ogni cambiamento viene validato sul piano risultante, senza allentare US07.
 * Ricerca locale: non garantisce l'ottimo globale per combinazioni arbitrarie.
 */
final class OttimizzatoreCopertura {
    private final List<List<Integer>> candidati;
    private final Predicate<PianoSettimanale> valido;

    OttimizzatoreCopertura(List<List<Integer>> candidati, Predicate<PianoSettimanale> valido) {
        this.candidati = candidati;
        this.valido = valido;
    }

    PianoSettimanale ottimizza(PianoSettimanale piano) {
        while (true) {
            PianoSettimanale migliore = copriUnPosto(piano);
            if (migliore == null) migliore = aumentaOre(piano);
            if (migliore == null) return piano;
            // Ogni passo aumenta i posti coperti, oppure le ore a parita di posti.
            // Non ci sono mosse neutre: la ricerca termina senza cicli.
            piano = migliore;
        }
    }

    private PianoSettimanale copriUnPosto(PianoSettimanale piano) {
        for (int destinazione = 0; destinazione < piano.turni().size(); destinazione++) {
            Turno scoperto = piano.turni().get(destinazione);
            if (scoperto.postiScoperti() == 0) continue;
            for (int id : candidati.get(destinazione)) {
                if (contiene(scoperto, id)) continue;
                List<Turno> aggiunta = new ArrayList<>(piano.turni());
                aggiunta.set(destinazione, aggiungi(scoperto, id));
                PianoSettimanale tentativo = new PianoSettimanale(piano.inizioSettimana(), aggiunta);
                if (valido.test(tentativo)) return tentativo;

                // Libera il candidato da un turno precedente o successivo, coprendolo
                // con un'altra persona. Si verifica la sostituzione insieme all'aggiunta:
                // lo stato temporaneo potrebbe superare ore, riposi o quota part-time.
                for (int origine = 0; origine < piano.turni().size(); origine++) {
                    if (origine == destinazione) continue;
                    Turno occupato = piano.turni().get(origine);
                    if (!contiene(occupato, id)) continue;
                    for (int sostituto : candidati.get(origine)) {
                        if (contiene(occupato, sostituto)) continue;
                        List<Turno> scambio = new ArrayList<>(aggiunta);
                        scambio.set(origine, aggiungi(rimuovi(occupato, id), sostituto));
                        tentativo = new PianoSettimanale(piano.inizioSettimana(), scambio);
                        if (valido.test(tentativo)) return tentativo;
                    }
                }
            }
        }
        return null;
    }

    private PianoSettimanale aumentaOre(PianoSettimanale piano) {
        for (int destinazione = 0; destinazione < piano.turni().size(); destinazione++) {
            Turno scoperto = piano.turni().get(destinazione);
            if (scoperto.postiScoperti() == 0) continue;
            for (int id : candidati.get(destinazione)) {
                if (contiene(scoperto, id)) continue;
                for (int origine = 0; origine < piano.turni().size(); origine++) {
                    Turno occupato = piano.turni().get(origine);
                    if (!contiene(occupato, id) || minuti(occupato) >= minuti(scoperto)) continue;
                    List<Turno> spostamento = new ArrayList<>(piano.turni());
                    spostamento.set(origine, rimuovi(occupato, id));
                    spostamento.set(destinazione, aggiungi(scoperto, id));
                    PianoSettimanale tentativo = new PianoSettimanale(piano.inizioSettimana(), spostamento);
                    if (valido.test(tentativo)) return tentativo;
                }
            }
        }
        return null;
    }

    private static long minuti(Turno turno) {
        return Duration.between(turno.inizio(), turno.fine()).toMinutes();
    }

    private static boolean contiene(Turno turno, int id) {
        return turno.assegnazioni().stream().anyMatch(a -> a.dipendenteId() == id);
    }

    private static Turno aggiungi(Turno turno, int id) {
        List<AssegnazioneTurno> assegnate = new ArrayList<>(turno.assegnazioni());
        assegnate.add(new AssegnazioneTurno(id, turno.inizio(), turno.fine()));
        return conAssegnazioni(turno, assegnate);
    }

    private static Turno rimuovi(Turno turno, int id) {
        return conAssegnazioni(turno, turno.assegnazioni().stream().filter(a -> a.dipendenteId() != id).toList());
    }

    private static Turno conAssegnazioni(Turno turno, List<AssegnazioneTurno> assegnate) {
        return new Turno(turno.servizio(), turno.ruolo(), turno.inizio(), turno.fine(), turno.postiRichiesti(), assegnate);
    }
}
