package it.disagili.turnogiusto.repository;

import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Disponibilita;
import it.disagili.turnogiusto.service.DisponibilitaService;
import it.disagili.turnogiusto.service.RiepilogoDisponibilita;
import it.disagili.turnogiusto.support.DatabaseDisponibilitaSupport;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Integrazione reale MySQL, eseguibile anche direttamente da IntelliJ. */
class DisponibilitaRepositoryTest extends DatabaseDisponibilitaSupport {

    private final DisponibilitaRepository lettura = new DisponibilitaRepository(this::apriConnessione);
    private final DipendentiDisponibilitaJdbc dipendenti = new DipendentiDisponibilitaJdbc(this::apriConnessione);
    private final SalvataggioDisponibilitaRepository scrittura =
            new SalvataggioDisponibilitaRepository(dipendenti, this::apriConnessione);
    private final DisponibilitaService servizio = new DisponibilitaService(dipendenti, this::apriConnessione);

    private Disponibilita fascia(int id, DayOfWeek giorno, int inizio, int fine) {
        return new Disponibilita(id, giorno, LocalTime.of(inizio, 0), LocalTime.of(fine, 0));
    }

    @Test
    void salvaRileggeEOrdinaAncheOltreMezzanotte() throws SQLException {
        scrittura.sostituisciPerDipendente(giulia, List.of(
                fascia(giulia, DayOfWeek.SATURDAY, 18, 2),
                fascia(giulia, DayOfWeek.MONDAY, 12, 15),
                fascia(giulia, DayOfWeek.SATURDAY, 12, 15)));
        List<Disponibilita> lette = lettura.trovaPerDipendente(giulia);
        assertEquals(3, lette.size());
        assertEquals(DayOfWeek.MONDAY, lette.get(0).getGiornoSettimana());
        assertEquals(LocalTime.NOON, lette.get(1).getOraInizio());
        assertTrue(lette.get(2).attraversaMezzanotte());
        assertTrue(lette.get(0).getId() > 0);
    }

    @Test
    void sostituisceSenzaDuplicareENonToccaAltriDipendenti() throws SQLException {
        scrittura.sostituisciPerDipendente(sara, List.of(fascia(sara, DayOfWeek.FRIDAY, 18, 23)));
        int idSara = lettura.trovaPerDipendente(sara).get(0).getId();
        scrittura.sostituisciPerDipendente(giulia, List.of(fascia(giulia, DayOfWeek.MONDAY, 12, 15)));
        List<Disponibilita> nuove = List.of(fascia(giulia, DayOfWeek.TUESDAY, 18, 23));
        scrittura.sostituisciPerDipendente(giulia, nuove);
        scrittura.sostituisciPerDipendente(giulia, nuove);
        assertEquals(1, lettura.trovaPerDipendente(giulia).size());
        assertEquals(DayOfWeek.TUESDAY, lettura.trovaPerDipendente(giulia).get(0).getGiornoSettimana());
        assertEquals(idSara, lettura.trovaPerDipendente(sara).get(0).getId());
    }

    @Test
    void elencoVuotoRimuoveTutteLeFasce() throws SQLException {
        scrittura.sostituisciPerDipendente(giulia, List.of(fascia(giulia, DayOfWeek.MONDAY, 12, 15)));
        scrittura.sostituisciPerDipendente(giulia, List.of());
        assertTrue(lettura.trovaPerDipendente(giulia).isEmpty());
    }

    @Test
    void rollbackRipristinaAncheGliIdDopoErroreNelSecondoInserimento() throws SQLException {
        scrittura.sostituisciPerDipendente(giulia, List.of(fascia(giulia, DayOfWeek.MONDAY, 18, 23)));
        int idOriginale = lettura.trovaPerDipendente(giulia).get(0).getId();
        // Il trigger esiste solo nel database di test e fa fallire la seconda INSERT.
        esegui("""
                CREATE TRIGGER test_errore_disponibilita BEFORE INSERT ON disponibilita
                FOR EACH ROW BEGIN
                    IF NEW.ora_inizio = '09:00:00' THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Errore di prova';
                    END IF;
                END
                """);
        assertThrows(SQLException.class, () -> scrittura.sostituisciPerDipendente(giulia, List.of(
                fascia(giulia, DayOfWeek.TUESDAY, 8, 12), fascia(giulia, DayOfWeek.WEDNESDAY, 9, 13))));
        List<Disponibilita> dopo = lettura.trovaPerDipendente(giulia);
        assertEquals(1, dopo.size());
        assertEquals(idOriginale, dopo.get(0).getId());
        assertEquals(LocalTime.of(18, 0), dopo.get(0).getOraInizio());
    }

    @Test
    void datiInvalidiNonCancellanoLeFascePrecedenti() throws SQLException {
        scrittura.sostituisciPerDipendente(giulia, List.of(fascia(giulia, DayOfWeek.MONDAY, 18, 23)));
        Disponibilita duplicata = fascia(giulia, DayOfWeek.TUESDAY, 12, 15);
        assertThrows(IllegalArgumentException.class,
                () -> scrittura.sostituisciPerDipendente(giulia, List.of(duplicata, duplicata)));
        assertThrows(IllegalArgumentException.class, () -> scrittura.sostituisciPerDipendente(
                giulia, List.of(fascia(sara, DayOfWeek.MONDAY, 12, 15))));
        assertEquals(DayOfWeek.MONDAY, lettura.trovaPerDipendente(giulia).get(0).getGiornoSettimana());
    }

    @Test
    void rifiutaFullTimeSenzaContrattoEDipendenteInesistente() {
        for (int id : new int[]{mario, senzaContratto, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> scrittura.sostituisciPerDipendente(id, List.of()));
        }
    }

    @Test
    void controllaEntrambeLeDateInclusiGliEstremi() throws SQLException {
        esegui("DELETE FROM contratto WHERE dipendente_id = " + paolo);
        aggiungiContratto(paolo, "PART_TIME", LocalDate.now().plusDays(1), null);
        assertThrows(IllegalArgumentException.class,
                () -> scrittura.sostituisciPerDipendente(paolo, List.of()));
        esegui("DELETE FROM contratto WHERE dipendente_id = " + paolo);
        aggiungiContratto(paolo, "PART_TIME", LocalDate.now().minusDays(5), LocalDate.now().minusDays(1));
        assertThrows(IllegalArgumentException.class,
                () -> scrittura.sostituisciPerDipendente(paolo, List.of()));
        aggiungiContratto(paolo, "PART_TIME", LocalDate.now(), LocalDate.now());
        scrittura.sostituisciPerDipendente(paolo, List.of(fascia(paolo, DayOfWeek.MONDAY, 12, 15)));
        assertEquals(1, lettura.trovaPerDipendente(paolo).size());
    }

    @Test
    void rileggeIlContrattoCambiatoDopoIlCaricamento() throws SQLException {
        servizio.trovaPartTime();
        scrittura.sostituisciPerDipendente(giulia, List.of(fascia(giulia, DayOfWeek.MONDAY, 18, 23)));
        esegui("UPDATE contratto SET tipo = 'FULL_TIME' WHERE dipendente_id = " + giulia);
        assertThrows(IllegalArgumentException.class,
                () -> scrittura.sostituisciPerDipendente(giulia, List.of()));
        assertEquals(1, lettura.trovaPerDipendente(giulia).size());
    }

    @Test
    void contrattiSovrappostiSonoUnErroreInLetturaEScrittura() throws SQLException {
        aggiungiContratto(giulia, "FULL_TIME", LocalDate.now(), null);
        assertThrows(SQLException.class, dipendenti::trovaTutti);
        assertThrows(IllegalArgumentException.class,
                () -> scrittura.sostituisciPerDipendente(giulia, List.of()));
    }

    @Test
    void trovaTuttiRispettaIlContrattoConcordatoConAntonio() throws SQLException {
        aggiungiContratto(sara, "FULL_TIME", LocalDate.now().minusMonths(2), LocalDate.now().minusMonths(1));
        List<Dipendente> tutti = dipendenti.trovaTutti();
        assertEquals(5, tutti.size());
        assertEquals(giulia, tutti.get(0).getId());
        assertEquals(paolo, tutti.get(1).getId());
        assertEquals(senzaContratto, tutti.get(2).getId());
        assertNull(tutti.get(2).getContratto());
        assertTrue(tutti.get(3).isPartTime());
    }

    @Test
    void consultazioneIncludePaoloSenzaFasce() throws SQLException {
        scrittura.sostituisciPerDipendente(giulia, List.of(fascia(giulia, DayOfWeek.MONDAY, 18, 23)));
        List<RiepilogoDisponibilita> righe = servizio.consulta();
        assertEquals(3, righe.size());
        assertEquals(paolo, righe.get(1).getDipendente().getId());
        assertEquals("senza disponibilità inserite", righe.get(1).getTestoDisponibilita());
        assertTrue(righe.get(0).getTestoDisponibilita().contains("18:00-23:00"));
    }
}
