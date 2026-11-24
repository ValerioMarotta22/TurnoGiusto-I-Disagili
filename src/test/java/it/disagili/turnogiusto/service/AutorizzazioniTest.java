package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.TipoUtente;
import it.disagili.turnogiusto.model.Utente;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Chi puo' fare cosa: la tabella dei permessi, scritta per intero cosi' una modifica per sbaglio si nota. */
class AutorizzazioniTest {

    private static final Utente TITOLARE = new Utente(1, "titolare", TipoUtente.TITOLARE, null, "Titolare", false);
    private static final Utente PART_TIME = new Utente(2, "sara.neri", TipoUtente.DIPENDENTE, 4, "Sara Neri", true);
    private static final Utente TEMPO_PIENO = new Utente(3, "anna.gialli", TipoUtente.DIPENDENTE, 5, "Anna Gialli", false);

    private static Set<Funzione> consentite(Utente utente) {
        Set<Funzione> risultato = EnumSet.noneOf(Funzione.class);
        for (Funzione funzione : Funzione.values()) {
            if (Autorizzazioni.consentita(utente, funzione)) {
                risultato.add(funzione);
            }
        }
        return risultato;
    }

    @Test
    void ilTitolareGestisceTuttoTranneLePagineDelDipendente() {
        assertEquals(EnumSet.of(Funzione.GESTIONE_DIPENDENTI, Funzione.DISPONIBILITA_PART_TIME, Funzione.FABBISOGNO,
                Funzione.GENERA_PIANO, Funzione.MODIFICA_PIANO), consentite(TITOLARE));
    }

    @Test
    void ilDipendentePartTimeVedeISuoiTurniELeSueDisponibilitaSoltanto() {
        assertEquals(EnumSet.of(Funzione.I_MIEI_TURNI, Funzione.LE_MIE_DISPONIBILITA), consentite(PART_TIME));
    }

    @Test
    void ilDipendenteATempoPienoVedeSoloITuoiTurni() {
        assertEquals(EnumSet.of(Funzione.I_MIEI_TURNI), consentite(TEMPO_PIENO));
    }

    @Test
    void unDipendenteNonPuoCreareNeModificareITurni() {
        // e' il caso da cui nasce il login: nessun dipendente entra nella sezione dei turni
        for (Utente dipendente : new Utente[]{PART_TIME, TEMPO_PIENO}) {
            assertFalse(Autorizzazioni.consentita(dipendente, Funzione.GENERA_PIANO));
            assertFalse(Autorizzazioni.consentita(dipendente, Funzione.MODIFICA_PIANO));
            assertFalse(Autorizzazioni.consentita(dipendente, Funzione.GESTIONE_DIPENDENTI));
            assertFalse(Autorizzazioni.consentita(dipendente, Funzione.FABBISOGNO));
        }
    }

    @Test
    void senzaAccessoNonSiPuoFareNiente() {
        for (Funzione funzione : Funzione.values()) {
            assertFalse(Autorizzazioni.consentita(null, funzione), funzione.name());
        }
        assertFalse(Autorizzazioni.consentita(TITOLARE, null));
    }
}
