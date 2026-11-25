package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.repository.PianoRepository;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * La pagina del dipendente: i SUOI turni di una settimana, letti dal piano salvato dal titolare.
 * Il dipendente vede solo se stesso: l'ID gli arriva dal chiamante (la sessione), non da una scelta libera.
 */
public class MieiTurniService {

    /**
     * pianoPresente = esiste un piano salvato per quella settimana (se no, il titolare non l'ha ancora fatto);
     * turni         = le righe del piano in cui il dipendente e' assegnato;
     * durata        = le ore totali di quei turni.
     */
    public record Settimana(boolean pianoPresente, List<Turno> turni, Duration durata) {
        public Settimana {
            turni = List.copyOf(turni);
        }
    }

    private final PianoRepository piani;

    public MieiTurniService() {
        this(new PianoRepository());
    }

    // Per i test: permette di passare un repository finto.
    MieiTurniService(PianoRepository piani) {
        this.piani = Objects.requireNonNull(piani);
    }

    public Settimana turniDi(int dipendenteId, LocalDate lunedi) throws SQLException {
        Optional<PianoSettimanale> piano = piani.trova(lunedi);
        if (piano.isEmpty()) {
            return new Settimana(false, List.of(), Duration.ZERO);
        }
        // Tengo solo le righe in cui questa persona e' tra gli assegnati.
        List<Turno> miei = piano.get().turni().stream()
                .filter(turno -> turno.assegnazioni().stream().anyMatch(a -> a.dipendenteId() == dipendenteId))
                .toList();
        Duration durata = Duration.ZERO;
        for (Turno turno : miei) {
            for (AssegnazioneTurno assegnazione : turno.assegnazioni()) {
                if (assegnazione.dipendenteId() == dipendenteId) {
                    durata = durata.plus(Duration.between(assegnazione.inizio(), assegnazione.fine()));
                }
            }
        }
        return new Settimana(true, miei, durata);
    }
}
