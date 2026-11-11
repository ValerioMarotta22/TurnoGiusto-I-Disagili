package it.disagili.turnogiusto.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** US-05: fabbisogno di personale per ruolo, tipo di giorno e servizio. */
class FabbisognoTest {

    @Test
    void ilFabbisognoDelSabatoSeraDelCasoConcretoEValido() {
        // dal testo del progetto: sabato sera servono 5 camerieri
        Fabbisogno f = new Fabbisogno(Ruolo.CAMERIERE, TipoGiorno.WEEKEND, Servizio.CENA, 5);

        assertTrue(f.valida().isEmpty());
        assertEquals("Cameriere - Weekend - Cena: 5", f.toString());
    }

    @Test
    void zeroPersoneEUnValoreAmmesso() {
        assertTrue(new Fabbisogno(Ruolo.LAVAPIATTI, TipoGiorno.FERIALE, Servizio.PRANZO, 0)
                .valida().isEmpty());
    }

    @Test
    void ilNumeroNonPuoEssereNegativo() {
        Fabbisogno f = new Fabbisogno(Ruolo.CUOCO, TipoGiorno.FERIALE, Servizio.CENA, -1);

        assertTrue(f.valida().contains("Il numero di dipendenti non può essere negativo"));
    }

    @Test
    void ilNumeroNonPuoSuperareIlLimiteDelDatabase() {
        // il database salva il numero in una colonna che arriva a 255
        assertTrue(new Fabbisogno(Ruolo.CUOCO, TipoGiorno.FERIALE, Servizio.CENA, 255).valida().isEmpty());

        Fabbisogno f = new Fabbisogno(Ruolo.CUOCO, TipoGiorno.FERIALE, Servizio.CENA, 256);

        assertTrue(f.valida().contains("Il numero di dipendenti non può superare 255"));
    }

    @Test
    void ruoloTipoDiGiornoEServizioSonoObbligatori() {
        Fabbisogno f = new Fabbisogno(null, null, null, 2);

        assertEquals(3, f.valida().size());
    }
}
