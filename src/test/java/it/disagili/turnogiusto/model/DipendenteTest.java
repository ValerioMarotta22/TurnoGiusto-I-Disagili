package it.disagili.turnogiusto.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** US-01: registrare un dipendente con ruolo e dati di contratto. */
class DipendenteTest {

    private static Contratto contrattoValido() {
        return new Contratto(0, TipoContratto.FULL_TIME, 40,
                new BigDecimal("10.50"), LocalDate.of(2026, 1, 1));
    }

    @Test
    void unDipendenteCompletoNonHaErrori() {
        Dipendente d = new Dipendente("Mario", "Rossi", Ruolo.CUOCO, contrattoValido());

        assertTrue(d.valida().isEmpty());
    }

    @Test
    void icampiMancantiVengonoElencati() {
        // criterio US-01: "il sistema non salva e mi dice quali campi mancano"
        Dipendente d = new Dipendente("", "Rossi", null, null);

        List<String> errori = d.valida();

        assertEquals(3, errori.size());
        assertTrue(errori.contains("Il nome è obbligatorio"));
        assertTrue(errori.contains("Il ruolo è obbligatorio"));
        assertTrue(errori.contains("I dati di contratto sono obbligatori"));
    }

    @Test
    void gliErroriDelContrattoSonoRiportatiNelDipendente() {
        Contratto contratto = new Contratto(0, TipoContratto.PART_TIME, 0,
                new BigDecimal("9.00"), LocalDate.of(2026, 1, 1));
        Dipendente d = new Dipendente("Sara", "Neri", Ruolo.CAMERIERE, contratto);

        assertTrue(d.valida().contains("Le ore settimanali devono essere maggiori di zero"));
    }

    @Test
    void nomeCompletoGestisceValoriMancanti() {
        assertEquals("Mario Rossi", new Dipendente("Mario", "Rossi", Ruolo.CUOCO, null).getNomeCompleto());
        assertEquals("Rossi", new Dipendente(null, "Rossi", Ruolo.CUOCO, null).getNomeCompleto());
    }

    @Test
    void ePartTimeSoloConContrattoPartTime() {
        Contratto partTime = new Contratto(0, TipoContratto.PART_TIME, 20,
                new BigDecimal("9.00"), LocalDate.of(2026, 1, 1));

        assertTrue(new Dipendente("Sara", "Neri", Ruolo.CAMERIERE, partTime).isPartTime());
        assertFalse(new Dipendente("Mario", "Rossi", Ruolo.CUOCO, contrattoValido()).isPartTime());
        assertFalse(new Dipendente("Anna", "Gialli", Ruolo.CAMERIERE, null).isPartTime());
    }

    @Test
    void dueDipendentiSalvatiConLoStessoIdSonoUguali() {
        Dipendente a = new Dipendente(7, "Mario", "Rossi", Ruolo.CUOCO, null);
        Dipendente b = new Dipendente(7, "Mario", "Rossi", Ruolo.CUOCO, null);
        Dipendente nuovo1 = new Dipendente("Luca", "Verdi", Ruolo.AIUTO_CUOCO, null);
        Dipendente nuovo2 = new Dipendente("Luca", "Verdi", Ruolo.AIUTO_CUOCO, null);

        assertEquals(a, b);
        assertNotEquals(nuovo1, nuovo2); // non ancora salvati: id 0, sono oggetti diversi
    }

    @Test
    void leEtichettePerLInterfacciaSonoLeggibiliEIlNomeResta() {
        assertEquals("Aiuto cuoco", Ruolo.AIUTO_CUOCO.toString());
        assertEquals("AIUTO_CUOCO", Ruolo.AIUTO_CUOCO.name()); // quello da salvare nel database
        assertEquals("Mario Rossi (Cuoco)",
                new Dipendente("Mario", "Rossi", Ruolo.CUOCO, null).toString());
    }
}
