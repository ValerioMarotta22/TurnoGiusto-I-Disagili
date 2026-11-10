package it.disagili.turnogiusto.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** US-03 e US-04: finestre di disponibilita' settimanali dei part-time. */
class DisponibilitaTest {

    @Test
    void unaFinestraCompletaNonHaErrori() {
        Disponibilita d = new Disponibilita(1, DayOfWeek.THURSDAY,
                LocalTime.of(18, 0), LocalTime.of(23, 0));

        assertTrue(d.valida().isEmpty());
    }

    @Test
    void giornoEOrariSonoObbligatori() {
        Disponibilita d = new Disponibilita(1, null, null, null);

        assertEquals(3, d.valida().size());
    }

    @Test
    void oraDiInizioEFineNonPossonoCoincidere() {
        Disponibilita d = new Disponibilita(1, DayOfWeek.FRIDAY,
                LocalTime.of(18, 0), LocalTime.of(18, 0));

        assertTrue(d.valida().contains("L'ora di inizio e quella di fine non possono coincidere"));
    }

    @Test
    void riconoscePerLeFinestreCheFinisconoDopoMezzanotte() {
        Disponibilita serale = new Disponibilita(1, DayOfWeek.SATURDAY,
                LocalTime.of(18, 0), LocalTime.of(2, 0));
        Disponibilita pranzo = new Disponibilita(1, DayOfWeek.SATURDAY,
                LocalTime.of(12, 0), LocalTime.of(15, 0));

        assertTrue(serale.attraversaMezzanotte());
        assertTrue(serale.valida().isEmpty()); // una finestra serale fino alle 2 e' ammessa
        assertFalse(pranzo.attraversaMezzanotte());
    }

    @Test
    void ilGiornoSiMostraInItaliano() {
        Disponibilita d = new Disponibilita(1, DayOfWeek.MONDAY,
                LocalTime.of(12, 0), LocalTime.of(15, 0));

        assertTrue(d.toString().startsWith("lunedì"));
    }
}
