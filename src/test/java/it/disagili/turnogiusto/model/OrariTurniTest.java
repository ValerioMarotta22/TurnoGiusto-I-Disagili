package it.disagili.turnogiusto.model;

import it.disagili.turnogiusto.service.vincoli.VerificaVincoliService;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Specifica degli otto orari concordati: nessun database o toolkit JavaFX. */
class OrariTurniTest {
    private record Atteso(Ruolo ruolo, Servizio servizio, String inizio, String fine, int minuti) { }
    private static final List<Atteso> CASI = List.of(
            new Atteso(Ruolo.CUOCO, Servizio.PRANZO, "10:00", "15:30", 330),
            new Atteso(Ruolo.AIUTO_CUOCO, Servizio.PRANZO, "10:00", "15:30", 330),
            new Atteso(Ruolo.CAMERIERE, Servizio.PRANZO, "11:30", "16:00", 270),
            new Atteso(Ruolo.LAVAPIATTI, Servizio.PRANZO, "12:00", "16:00", 240),
            new Atteso(Ruolo.CUOCO, Servizio.CENA, "17:00", "00:00", 420),
            new Atteso(Ruolo.AIUTO_CUOCO, Servizio.CENA, "17:00", "00:00", 420),
            new Atteso(Ruolo.CAMERIERE, Servizio.CENA, "18:00", "00:30", 390),
            new Atteso(Ruolo.LAVAPIATTI, Servizio.CENA, "19:00", "00:30", 330));

    @Test
    void tutteLeFasceHannoOrariDurataEGiornoFinaleConcordati() {
        // Giorno feriale, sabato, domenica e Natale: le fasce restano identiche.
        for (var giorno : List.of(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 28),
                LocalDate.of(2026, 11, 29), LocalDate.of(2026, 12, 25))) {
            for (var atteso : CASI) {
                var fascia = OrariTurni.per(atteso.ruolo(), atteso.servizio());
                String caso = atteso.ruolo() + " " + atteso.servizio() + " " + giorno;
                assertEquals(LocalTime.parse(atteso.inizio()), fascia.inizio(), caso);
                assertEquals(LocalTime.parse(atteso.fine()), fascia.fine(), caso);
                var fine = fascia.fineDelServizio(giorno);
                assertEquals(atteso.servizio() == Servizio.CENA ? giorno.plusDays(1) : giorno,
                        fine.toLocalDate(), caso);
                assertEquals(atteso.minuti(), Duration.between(giorno.atTime(fascia.inizio()), fine).toMinutes(), caso);
            }
        }
    }

    @Test
    void ilRiposoCenaPranzoDipendeDagliOrariDelRuolo() {
        var domenica = LocalDate.of(2026, 11, 29);
        var vincoli = new VerificaVincoliService();
        for (Ruolo ruolo : Ruolo.values()) {
            var fineCena = OrariTurni.per(ruolo, Servizio.CENA).fineDelServizio(domenica);
            var inizioPranzo = domenica.plusDays(1).atTime(OrariTurni.per(ruolo, Servizio.PRANZO).inizio());
            int minutiAttesi = switch (ruolo) {
                case CUOCO, AIUTO_CUOCO -> 600;
                case CAMERIERE -> 660;
                case LAVAPIATTI -> 690;
            };
            assertEquals(minutiAttesi, vincoli.calcolaRiposo(fineCena, inizioPranzo).toMinutes());
            assertEquals(minutiAttesi >= 660, vincoli.rispettaRiposoMinimo(fineCena, inizioPranzo));
        }
    }

    @Test
    void nonSceglieUnaFasciaSeMancanoRuoloOServizio() {
        assertThrows(NullPointerException.class, () -> OrariTurni.per(null, Servizio.PRANZO));
        assertThrows(NullPointerException.class, () -> OrariTurni.per(Ruolo.CUOCO, null));
    }
}
