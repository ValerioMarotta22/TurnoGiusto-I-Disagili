package it.disagili.turnogiusto.service.vincoli;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.TipoContratto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Adattatore tra la generazione/modifica del piano (Edoardo, Valerio) e i controlli US07 (Antonio). */
class VerificatoreVincoliUS07Test {

    private static final LocalDate LUNEDI = LocalDate.of(2026, 11, 30);
    private static final int ANNA = 1;

    private final VerificatoreVincoliUS07 verificatore = new VerificatoreVincoliUS07();

    // Anna ha un contratto full-time valido per tutta la settimana
    private static List<Contratto> contratti() {
        return List.of(new Contratto(1, ANNA, TipoContratto.FULL_TIME, 40, new BigDecimal("10.00"),
                LocalDate.of(2026, 1, 1), null));
    }

    private static AssegnazioneTurno turno(LocalDateTime inizio, LocalDateTime fine) {
        return new AssegnazioneTurno(ANNA, inizio, fine);
    }

    @Test
    void unSoloTurnoNonViolaNiente() {
        LocalDateTime inizio = LUNEDI.plusDays(5).atTime(18, 0); // sabato a cena

        List<String> violazioni = verificatore.verifica(LUNEDI, List.of(turno(inizio, inizio.plusHours(6))),
                List.of(), contratti());

        assertTrue(violazioni.isEmpty());
    }

    @Test
    void menoDiUndiciOreDiRiposoVieneSegnalato() {
        // cena di sabato fino alle 2 di domenica, poi pranzo di domenica alle 11:30: 9 ore e mezza di riposo
        LocalDateTime cenaInizio = LUNEDI.plusDays(5).atTime(18, 0);
        LocalDateTime cenaFine = LUNEDI.plusDays(6).atTime(2, 0);
        LocalDateTime pranzoInizio = LUNEDI.plusDays(6).atTime(11, 30);

        List<String> violazioni = verificatore.verifica(LUNEDI,
                List.of(turno(cenaInizio, cenaFine), turno(pranzoInizio, pranzoInizio.plusHours(3).plusMinutes(30))),
                List.of(), contratti());

        assertFalse(violazioni.isEmpty());
        // ogni testo comincia con "Dipendente <id>: ", cosi' il service puo' sostituirlo con il nome
        for (String testo : violazioni) {
            assertTrue(VerificatoreVincoliUS07.PREFISSO_DIPENDENTE.matcher(testo).find(), testo);
            assertTrue(testo.startsWith("Dipendente " + ANNA + ": "), testo);
        }
    }

    @Test
    void pranzoECenaDelloStessoGiornoSonoTurniConsecutivi() {
        // il servizio si ricava dall'ora di inizio: 12:00 e' un pranzo, 18:00 una cena
        LocalDateTime pranzo = LUNEDI.plusDays(2).atTime(12, 0);
        LocalDateTime cena = LUNEDI.plusDays(2).atTime(18, 0);

        List<String> violazioni = verificatore.verifica(LUNEDI,
                List.of(turno(pranzo, pranzo.plusHours(3)), turno(cena, cena.plusHours(6))),
                List.of(), contratti());

        assertFalse(violazioni.isEmpty());
    }

    @Test
    void ilRiposoTraSettimanaPrecedenteEQuestaSiControllaConIConfinanti() {
        // cena di domenica scorsa fino alle 00:30 di lunedi', poi pranzo di lunedi' alle 10:00: 9 ore e mezza di riposo
        LocalDateTime cenaDomenica = LUNEDI.minusDays(1).atTime(18, 0);
        AssegnazioneTurno confinante = turno(cenaDomenica, LUNEDI.atTime(0, 30));
        LocalDateTime pranzoPresto = LUNEDI.atTime(10, 0);

        List<String> violazioni = verificatore.verifica(LUNEDI,
                List.of(turno(pranzoPresto, pranzoPresto.plusHours(3))), List.of(confinante), contratti());

        assertFalse(violazioni.isEmpty());
    }
}