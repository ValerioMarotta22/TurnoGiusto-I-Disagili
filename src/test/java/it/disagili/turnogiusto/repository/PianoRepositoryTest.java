package it.disagili.turnogiusto.repository;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.support.DatabaseDisponibilitaSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * US-06B: salvare e rileggere il piano. Usa il MySQL di Docker, come gli altri test del database
 * (la classe di supporto crea e poi cancella quattro dipendenti di prova).
 */
class PianoRepositoryTest extends DatabaseDisponibilitaSupport {

    // Un lunedi' lontano nel futuro, cosi' i test non toccano mai i piani veri.
    private static final LocalDate LUNEDI = LocalDate.of(2030, 1, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    private static final LocalDate LUNEDI_DOPO = LUNEDI.plusWeeks(1);

    private final PianoRepository repository = new PianoRepository();

    // Dopo ogni test tolgo i piani di prova delle tre settimane usate (le assegnazioni vanno via in cascata).
    @AfterEach
    void pulisciPiani() throws SQLException {
        esegui("DELETE FROM piano WHERE inizio_settimana IN ('" + LUNEDI.minusWeeks(1) + "', '"
                + LUNEDI + "', '" + LUNEDI_DOPO + "')");
    }

    // sabato a cena: 3 camerieri richiesti, quanti ne passi assegnati (gli altri restano scoperti)
    private Turno cenaDelSabato(int... camerieri) {
        LocalDateTime inizio = LUNEDI.plusDays(5).atTime(18, 0);
        return new Turno(Servizio.CENA, Ruolo.CAMERIERE, inizio, inizio.plusHours(6), 3,
                assegnazioni(inizio, inizio.plusHours(6), camerieri));
    }

    // sabato a pranzo: 1 cuoco richiesto
    private Turno pranzoDelSabato(int cuoco) {
        LocalDateTime inizio = LUNEDI.plusDays(5).atTime(12, 0);
        return new Turno(Servizio.PRANZO, Ruolo.CUOCO, inizio, inizio.plusHours(3), 1,
                assegnazioni(inizio, inizio.plusHours(3), cuoco));
    }

    private static List<AssegnazioneTurno> assegnazioni(LocalDateTime inizio, LocalDateTime fine, int... ids) {
        return java.util.Arrays.stream(ids).sorted().mapToObj(id -> new AssegnazioneTurno(id, inizio, fine)).toList();
    }

    @Test
    void unPianoSalvatoPuoEssereRiletto() throws SQLException {
        // il piano e' ordinato come lo restituisce il repository: prima il pranzo, poi la cena
        PianoSettimanale piano = new PianoSettimanale(LUNEDI,
                List.of(pranzoDelSabato(mario), cenaDelSabato(giulia, sara)));

        repository.salva(piano);
        Optional<PianoSettimanale> riletto = repository.trova(LUNEDI);

        assertTrue(riletto.isPresent());
        assertEquals(piano, riletto.get());
        assertEquals(1, riletto.get().postiScoperti()); // 3 camerieri richiesti, 2 assegnati
    }

    // ================================================================ FESTIVI DEL PIANO

    @Test
    void iFestiviSiSalvanoESiRileggonoConIlPiano() throws SQLException {
        PianoSettimanale piano = new PianoSettimanale(LUNEDI, List.of(cenaDelSabato(giulia)));
        java.util.Set<LocalDate> festivi = java.util.Set.of(LUNEDI.plusDays(3), LUNEDI.plusDays(4)); // gio e ven

        repository.salva(piano, festivi);

        assertEquals(festivi, repository.trovaFestivi(LUNEDI));
        assertEquals(piano, repository.trova(LUNEDI).orElseThrow()); // il piano resta uguale
    }

    @Test
    void risalvareSenzaFestiviLiToglie() throws SQLException {
        PianoSettimanale piano = new PianoSettimanale(LUNEDI, List.of(cenaDelSabato(giulia)));
        repository.salva(piano, java.util.Set.of(LUNEDI.plusDays(3)));

        repository.salva(piano, java.util.Set.of());

        assertTrue(repository.trovaFestivi(LUNEDI).isEmpty());
    }

    @Test
    void ilVecchioSalvaTieneIFestiviGiaSalvati() throws SQLException {
        PianoSettimanale piano = new PianoSettimanale(LUNEDI, List.of(cenaDelSabato(giulia)));
        repository.salva(piano, java.util.Set.of(LUNEDI.plusDays(4)));

        repository.salva(new PianoSettimanale(LUNEDI, List.of(cenaDelSabato(giulia, sara))));

        assertEquals(java.util.Set.of(LUNEDI.plusDays(4)), repository.trovaFestivi(LUNEDI));
    }

    @Test
    void unFestivoFuoriDallaSettimanaVieneRifiutatoENonSiSalvaNiente() {
        PianoSettimanale piano = new PianoSettimanale(LUNEDI, List.of(cenaDelSabato(giulia)));

        assertThrows(IllegalArgumentException.class,
                () -> repository.salva(piano, java.util.Set.of(LUNEDI.plusWeeks(1))));
        assertDoesNotThrow(() -> assertTrue(repository.trova(LUNEDI).isEmpty()));
    }

    @Test
    void unaSettimanaSenzaPianoNonHaFestivi() throws SQLException {
        assertTrue(repository.trovaFestivi(LUNEDI).isEmpty());
    }

    @Test
    void unaSettimanaSenzaPianoNonHaNiente() throws SQLException {
        assertTrue(repository.trova(LUNEDI).isEmpty());
    }

    @Test
    void salvareDiNuovoLaStessaSettimanaSostituisceIlPiano() throws SQLException {
        repository.salva(new PianoSettimanale(LUNEDI, List.of(cenaDelSabato(giulia, sara))));

        // il titolare sostituisce Giulia con Paolo e salva
        repository.salva(new PianoSettimanale(LUNEDI, List.of(cenaDelSabato(paolo, sara))));
        PianoSettimanale riletto = repository.trova(LUNEDI).orElseThrow();

        List<Integer> assegnati = riletto.assegnazioni().stream().map(AssegnazioneTurno::dipendenteId).toList();
        assertTrue(assegnati.contains(paolo));
        assertFalse(assegnati.contains(giulia));
        assertEquals(1, riletto.turni().size()); // non si e' duplicato niente
    }

    @Test
    void unaRigaTuttaScopertaVieneRilettaConZeroAssegnati() throws SQLException {
        repository.salva(new PianoSettimanale(LUNEDI, List.of(cenaDelSabato())));

        Turno riga = repository.trova(LUNEDI).orElseThrow().turni().get(0);

        assertEquals(3, riga.postiRichiesti());
        assertTrue(riga.assegnazioni().isEmpty());
        assertEquals(3, riga.postiScoperti());
    }

    @Test
    void seUnDipendenteNonEsisteNonSalvaNienteENonRestaNulla() throws SQLException {
        repository.salva(new PianoSettimanale(LUNEDI, List.of(cenaDelSabato(giulia))));

        // 999999999 non e' un dipendente: l'inserimento fallisce e la transazione annulla tutto
        PianoSettimanale rotto = new PianoSettimanale(LUNEDI, List.of(cenaDelSabato(999_999_999)));
        assertThrows(SQLException.class, () -> repository.salva(rotto));

        // il piano di prima e' ancora intatto
        PianoSettimanale riletto = repository.trova(LUNEDI).orElseThrow();
        assertEquals(giulia, riletto.assegnazioni().get(0).dipendenteId());
    }

    @Test
    void leAssegnazioniConfinantiSonoQuelleDelGiornoPrimaEDopo() throws SQLException {
        // settimana precedente: cena di domenica 18:00 -> lunedi' 00:30 (a cavallo del lunedi')
        LocalDate domenica = LUNEDI.minusDays(1);
        LocalDateTime inizio = domenica.atTime(18, 0);
        LocalDateTime fine = LUNEDI.atTime(0, 30);
        Turno cenaDomenica = new Turno(Servizio.CENA, Ruolo.CAMERIERE, inizio, fine, 1,
                List.of(new AssegnazioneTurno(sara, inizio, fine)));
        repository.salva(new PianoSettimanale(LUNEDI.minusWeeks(1), List.of(cenaDomenica)));

        // la settimana che stiamo guardando ha un suo turno: NON deve comparire tra i confinanti
        repository.salva(new PianoSettimanale(LUNEDI, List.of(cenaDelSabato(giulia))));

        List<AssegnazioneTurno> confinanti = repository.trovaConfinanti(LUNEDI);

        assertEquals(1, confinanti.size());
        assertEquals(sara, confinanti.get(0).dipendenteId());
        assertEquals(inizio, confinanti.get(0).inizio());
        assertEquals(fine, confinanti.get(0).fine());
    }
}