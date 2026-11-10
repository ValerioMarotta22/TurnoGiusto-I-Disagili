package it.disagili.turnogiusto.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** US-01 e US-02: dati e regole del contratto. */
class ContrattoTest {

    private static final LocalDate INIZIO = LocalDate.of(2026, 1, 1);

    private static Contratto contratto(int ore, String costo) {
        return new Contratto(0, TipoContratto.FULL_TIME, ore, new BigDecimal(costo), INIZIO);
    }

    @Test
    void unContrattoCompletoNonHaErrori() {
        assertTrue(contratto(40, "10.50").valida().isEmpty());
    }

    @Test
    void leOreNonPossonoSuperareQuaranta() {
        assertTrue(contratto(41, "10.50").valida().contains("Le ore settimanali non possono superare 40"));
        assertTrue(contratto(40, "10.50").valida().isEmpty());
    }

    @Test
    void leOreDevonoEssereMaggioriDiZero() {
        assertTrue(contratto(0, "10.50").valida().contains("Le ore settimanali devono essere maggiori di zero"));
        assertTrue(contratto(-5, "10.50").valida().contains("Le ore settimanali devono essere maggiori di zero"));
    }

    @Test
    void ilCostoOrarioDeveEssereMaggioreDiZero() {
        assertTrue(contratto(20, "0").valida().contains("Il costo orario deve essere maggiore di zero"));
        assertTrue(contratto(20, "-1.00").valida().contains("Il costo orario deve essere maggiore di zero"));
    }

    @Test
    void ilTipoEDataDiInizioSonoObbligatori() {
        Contratto c = new Contratto(0, null, 20, new BigDecimal("9.00"), null);

        assertTrue(c.valida().contains("Il tipo di contratto è obbligatorio"));
        assertTrue(c.valida().contains("La data di inizio è obbligatoria"));
    }

    @Test
    void laDataDiFineNonPuoPrecedereQuellaDiInizio() {
        Contratto c = new Contratto(0, 1, TipoContratto.PART_TIME, 20, new BigDecimal("9.00"),
                INIZIO, INIZIO.minusDays(1));

        assertTrue(c.valida().contains("La data di fine non può precedere la data di inizio"));
    }

    @Test
    void ilContrattoVaLeAlleDateIndicateEstremiCompresi() {
        Contratto c = new Contratto(0, 1, TipoContratto.PART_TIME, 20, new BigDecimal("9.00"),
                INIZIO, LocalDate.of(2026, 6, 30));

        assertFalse(c.isValidoIl(INIZIO.minusDays(1)));
        assertTrue(c.isValidoIl(INIZIO));
        assertTrue(c.isValidoIl(LocalDate.of(2026, 6, 30)));
        assertFalse(c.isValidoIl(LocalDate.of(2026, 7, 1)));
    }

    @Test
    void senzaDataDiFineIlContrattoVaLePerSempre() {
        Contratto c = contratto(40, "10.50");

        assertTrue(c.isValidoIl(LocalDate.of(2040, 1, 1)));
        assertFalse(c.isValidoIl(INIZIO.minusDays(1)));
    }
}
