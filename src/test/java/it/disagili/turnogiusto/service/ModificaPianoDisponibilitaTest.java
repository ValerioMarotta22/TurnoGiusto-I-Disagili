package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.*;
import it.disagili.turnogiusto.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModificaPianoDisponibilitaTest {
    private static final LocalDate LUNEDI = LocalDate.of(2026, 11, 23);
    private final Dipendente sara = new Dipendente(4, "Sara", "Neri", Ruolo.CAMERIERE,
            new Contratto(4, 4, TipoContratto.PART_TIME, 20, BigDecimal.TEN, LUNEDI, null));
    private final Turno cena = new Turno(Servizio.CENA, Ruolo.CAMERIERE,
            LUNEDI.plusDays(6).atTime(18, 0), LUNEDI.plusDays(7).atTime(0, 30), 4, List.of());
    private final PianoSettimanale piano = new PianoSettimanale(LUNEDI, List.of(cena));

    @Test
    void laDisponibilitaDelSabatoNonConsenteLaCenaDellaDomenica() throws Exception {
        var service = service(List.of(fascia(DayOfWeek.SATURDAY, 18, 2)));
        assertTrue(service.candidati(piano, cena).isEmpty());
        var errore = assertThrows(ModificaPianoException.class,
                () -> service.assegnaAlPostoScoperto(piano, cena, sara.getId()));
        assertTrue(errore.getMessage().contains("disponibilita"));
        assertTrue(piano.assegnazioni().isEmpty());
    }

    @Test
    void ancheLaSostituzioneRichiedeDisponibilitaPerTuttoIlTurno() {
        Turno occupato = new Turno(cena.servizio(), cena.ruolo(), cena.inizio(), cena.fine(), 4,
                List.of(new AssegnazioneTurno(5, cena.inizio(), cena.fine())));
        var service = service(List.of(fascia(DayOfWeek.SUNDAY, 18, 23)));
        assertThrows(ModificaPianoException.class, () -> service.sostituisci(
                new PianoSettimanale(LUNEDI, List.of(occupato)), occupato, 5, sara.getId()));
    }

    @Test
    void accettaFasceContigueCheCopronoAncheIlLunediSuccessivo() throws Exception {
        var service = service(List.of(fascia(DayOfWeek.SUNDAY, 18, 0), fascia(DayOfWeek.MONDAY, 0, 1)));
        assertEquals(List.of(sara), service.candidati(piano, cena));
        var esito = service.assegnaAlPostoScoperto(piano, cena, sara.getId());
        assertEquals(4, esito.piano().assegnazioni().getFirst().dipendenteId());
    }

    private Disponibilita fascia(DayOfWeek giorno, int inizio, int fine) {
        return new Disponibilita(4, giorno, LocalTime.of(inizio, 0), LocalTime.of(fine, 0));
    }

    private ModificaPianoService service(List<Disponibilita> finestre) {
        var persone = new DipendenteRepository() {
            @Override public List<Dipendente> trovaTutti(LocalDate giorno) { return List.of(sara); }
        };
        var disponibilita = new DisponibilitaRepository() {
            @Override public List<Disponibilita> trovaPerDipendente(int id) { return finestre; }
        };
        var piani = new PianoRepository() {
            @Override public List<AssegnazioneTurno> trovaConfinanti(LocalDate lunedi) { return List.of(); }
        };
        var generazione = new GenerazioneTurniService(new FabbisognoRepository(), persone, disponibilita, null);
        return new ModificaPianoService(piani, persone, (p, confini) -> List.of(), generazione);
    }
}
