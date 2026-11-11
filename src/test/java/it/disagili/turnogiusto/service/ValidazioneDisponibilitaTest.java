package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Disponibilita;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidazioneDisponibilitaTest {

    private Disponibilita fascia(LocalTime inizio, LocalTime fine) {
        return new Disponibilita(1, DayOfWeek.MONDAY, inizio, fine);
    }

    @Test void leggeOrariConMinutiESecondi() {
        assertEquals(LocalTime.of(18, 30), ValidazioneDisponibilita.leggiOra(" 18:30 "));
        assertEquals(LocalTime.of(18, 30, 15), ValidazioneDisponibilita.leggiOra("18:30:15"));
    }

    @Test void rifiutaOrariInvalidiSenzaCorreggerliSilenziosamente() {
        for (String testo : new String[]{"24:00", "18:60", "-1:00", "ciao", "", "12:00:60"}) {
            assertThrows(IllegalArgumentException.class, () -> ValidazioneDisponibilita.leggiOra(testo));
        }
        assertThrows(IllegalArgumentException.class, () -> ValidazioneDisponibilita.leggiOra(null));
    }

    @Test void rifiutaElencoNulloFinestraNullaEIdErrato() {
        assertThrows(IllegalArgumentException.class, () -> ValidazioneDisponibilita.verifica(0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> ValidazioneDisponibilita.verifica(1, null));
        assertThrows(IllegalArgumentException.class,
                () -> ValidazioneDisponibilita.verifica(1, Arrays.asList((Disponibilita) null)));
        assertThrows(IllegalArgumentException.class, () -> ValidazioneDisponibilita.verifica(2,
                List.of(fascia(LocalTime.NOON, LocalTime.of(15, 0)))));
    }

    @Test void duplicheSiConfrontanoPerValoriAncheConIdDiversi() {
        Disponibilita prima = fascia(LocalTime.NOON, LocalTime.of(15, 0));
        Disponibilita seconda = fascia(LocalTime.NOON, LocalTime.of(15, 0));
        prima.setId(12);
        seconda.setId(13);
        assertThrows(IllegalArgumentException.class,
                () -> ValidazioneDisponibilita.verifica(1, List.of(prima, seconda)));
    }

    @Test void ammetteListaVuotaMezzanotteEFasceDistinteSovrapposte() {
        assertDoesNotThrow(() -> ValidazioneDisponibilita.verifica(1, List.of()));
        assertDoesNotThrow(() -> ValidazioneDisponibilita.verifica(1, List.of(
                fascia(LocalTime.of(18, 0), LocalTime.of(2, 0)),
                fascia(LocalTime.of(19, 0), LocalTime.of(23, 0)))));
    }

    @Test void applicaIlModelloERifiutaFrazioniDiSecondo() {
        assertThrows(IllegalArgumentException.class, () -> ValidazioneDisponibilita.verifica(1,
                List.of(fascia(LocalTime.NOON, LocalTime.NOON))));
        assertThrows(IllegalArgumentException.class, () -> ValidazioneDisponibilita.verifica(1,
                List.of(fascia(LocalTime.NOON.plusNanos(1), LocalTime.of(15, 0)))));
    }
}
