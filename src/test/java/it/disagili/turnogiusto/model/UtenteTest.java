package it.disagili.turnogiusto.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Un account e' coerente: il titolare non e' un dipendente, un dipendente e' sempre collegato alla sua anagrafica. */
class UtenteTest {

    @Test
    void unTitolareValidoNonHaUnDipendenteCollegato() {
        Utente titolare = new Utente(1, "titolare", TipoUtente.TITOLARE, null, "Titolare", false);

        assertTrue(titolare.isTitolare());
        assertNull(titolare.dipendenteId());
    }

    @Test
    void unTitolareNonPuoEssereCollegatoAUnDipendente() {
        assertThrows(IllegalArgumentException.class,
                () -> new Utente(1, "titolare", TipoUtente.TITOLARE, 5, "Titolare", false));
    }

    @Test
    void unDipendenteDeveEssereCollegatoAllaSuaAnagrafica() {
        assertThrows(IllegalArgumentException.class,
                () -> new Utente(2, "anna.gialli", TipoUtente.DIPENDENTE, null, "Anna Gialli", false));
        assertThrows(IllegalArgumentException.class,
                () -> new Utente(2, "anna.gialli", TipoUtente.DIPENDENTE, 0, "Anna Gialli", false));
    }

    @Test
    void soloUnDipendentePuoEssereIlPartTime() {
        assertThrows(IllegalArgumentException.class,
                () -> new Utente(1, "titolare", TipoUtente.TITOLARE, null, "Titolare", true));
    }

    @Test
    void campiObbligatori() {
        assertThrows(IllegalArgumentException.class, () -> new Utente(0, "x", TipoUtente.TITOLARE, null, "X", false));
        assertThrows(IllegalArgumentException.class, () -> new Utente(1, " ", TipoUtente.TITOLARE, null, "X", false));
        assertThrows(IllegalArgumentException.class, () -> new Utente(1, "x", TipoUtente.TITOLARE, null, "", false));
        assertThrows(NullPointerException.class, () -> new Utente(1, "x", null, null, "X", false));
    }

    @Test
    void conPartTimeRestituisceUnaCopiaEIlResto() {
        Utente prima = new Utente(2, "sara.neri", TipoUtente.DIPENDENTE, 4, "Sara Neri", false);

        Utente dopo = prima.conPartTime(true);

        assertTrue(dopo.partTime());
        assertFalse(prima.partTime()); // l'originale non cambia
        assertEquals("Sara Neri", dopo.nome());
    }

    @Test
    void leEtichetteDeiTipiSonoInItaliano() {
        assertEquals("Titolare", TipoUtente.TITOLARE.toString());
        assertEquals("Dipendente", TipoUtente.DIPENDENTE.toString());
        assertEquals("DIPENDENTE", TipoUtente.DIPENDENTE.name()); // quello salvato nel database
    }
}
