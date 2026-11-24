package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.repository.PianoRepository;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** La pagina del dipendente: vede solo i SUOI turni. Non serve il database. */
class MieiTurniServiceTest {

    private static final LocalDate LUNEDI = LocalDate.of(2026, 11, 30);
    private static final int ANNA = 5, SARA = 4, LUCA = 3;

    private static Turno riga(Servizio servizio, Ruolo ruolo, LocalDateTime inizio, int ore, int... assegnati) {
        LocalDateTime fine = inizio.plusHours(ore);
        List<AssegnazioneTurno> assegnazioni = new ArrayList<>();
        for (int id : assegnati) assegnazioni.add(new AssegnazioneTurno(id, inizio, fine));
        return new Turno(servizio, ruolo, inizio, fine, 3, assegnazioni);
    }

    private static MieiTurniService conPiano(PianoSettimanale piano) {
        return new MieiTurniService(new PianoRepository() {
            @Override
            public Optional<PianoSettimanale> trova(LocalDate lunedi) throws SQLException {
                return Optional.ofNullable(piano);
            }
        });
    }

    @Test
    void unaSettimanaSenzaPianoLoDice() throws SQLException {
        MieiTurniService.Settimana settimana = conPiano(null).turniDi(ANNA, LUNEDI);

        assertFalse(settimana.pianoPresente());
        assertTrue(settimana.turni().isEmpty());
    }

    @Test
    void ilDipendenteVedeSoloLeRigheDoveELavora() throws SQLException {
        Turno cenaAnna = riga(Servizio.CENA, Ruolo.CAMERIERE, LUNEDI.plusDays(5).atTime(18, 0), 6, ANNA, SARA);
        Turno pranzoSara = riga(Servizio.PRANZO, Ruolo.CAMERIERE, LUNEDI.plusDays(5).atTime(12, 0), 4, SARA);
        Turno cuochi = riga(Servizio.CENA, Ruolo.CUOCO, LUNEDI.plusDays(5).atTime(18, 0), 6, LUCA);
        PianoSettimanale piano = new PianoSettimanale(LUNEDI, List.of(pranzoSara, cenaAnna, cuochi));

        MieiTurniService.Settimana settimana = conPiano(piano).turniDi(ANNA, LUNEDI);

        assertTrue(settimana.pianoPresente());
        assertEquals(List.of(cenaAnna), settimana.turni()); // non vede i turni di Sara o dei cuochi
    }

    @Test
    void leOreSiSommanoSoloPerLaPersonaGiusta() throws SQLException {
        Turno pranzo = riga(Servizio.PRANZO, Ruolo.CAMERIERE, LUNEDI.atTime(12, 0), 4, ANNA);          // 4 ore
        Turno cena = riga(Servizio.CENA, Ruolo.CAMERIERE, LUNEDI.atTime(18, 0), 6, ANNA, SARA);        // 6 ore
        Turno altroGiorno = riga(Servizio.CENA, Ruolo.CAMERIERE, LUNEDI.plusDays(1).atTime(18, 0), 6, SARA); // non suo
        PianoSettimanale piano = new PianoSettimanale(LUNEDI, List.of(pranzo, cena, altroGiorno));

        assertEquals(Duration.ofHours(10), conPiano(piano).turniDi(ANNA, LUNEDI).durata());  // pranzo 4 + cena 6
        assertEquals(Duration.ofHours(12), conPiano(piano).turniDi(SARA, LUNEDI).durata());  // cena 6 + altro giorno 6
    }

    @Test
    void chiNonHaTurniInUnaSettimanaConPianoVedeUnElencoVuoto() throws SQLException {
        Turno cena = riga(Servizio.CENA, Ruolo.CAMERIERE, LUNEDI.atTime(18, 0), 6, SARA);
        PianoSettimanale piano = new PianoSettimanale(LUNEDI, List.of(cena));

        MieiTurniService.Settimana settimana = conPiano(piano).turniDi(ANNA, LUNEDI);

        assertTrue(settimana.pianoPresente());
        assertTrue(settimana.turni().isEmpty());
        assertEquals(Duration.ZERO, settimana.durata());
    }
}
