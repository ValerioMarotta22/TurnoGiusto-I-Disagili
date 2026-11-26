package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.*;
import it.disagili.turnogiusto.repository.*;
import it.disagili.turnogiusto.service.vincoli.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Service e adattatore reali; solo le letture dei repository sono simulate. */
class QuotaPartTimeTest {
    private static final LocalDate LUNEDI = LocalDate.of(2026, 11, 23);
    private final VerificaVincoliService vincoli = new VerificaVincoliService();
    private Contratto contratto(int id, TipoContratto tipo) {
        return new Contratto(id, id, tipo, tipo == TipoContratto.PART_TIME ? 20 : 40,
                BigDecimal.TEN, LUNEDI.minusMonths(1), null);
    }
    private final List<Contratto> contratti = List.of(contratto(1, TipoContratto.PART_TIME),
            contratto(2, TipoContratto.PART_TIME), contratto(3, TipoContratto.FULL_TIME));

    private Turno turno(int posti, Ruolo ruolo, LocalDate giorno, int... ids) {
        var inizio = giorno.atTime(11, 30);
        var fine = giorno.atTime(16, 0);
        return new Turno(Servizio.PRANZO, ruolo, inizio, fine, posti,
                Arrays.stream(ids).mapToObj(id -> new AssegnazioneTurno(id, inizio, fine)).toList());
    }

    @Test
    void applicaIlFabbisognoAncheAUnaPropostaParziale() {
        assertTrue(vincoli.verificaQuotaPartTime(turno(4, Ruolo.CAMERIERE, LUNEDI, 1), contratti).isEmpty());
        assertTrue(vincoli.verificaQuotaPartTime(turno(4, Ruolo.CAMERIERE, LUNEDI, 1, 2), contratti).isEmpty());
        assertTrue(vincoli.verificaQuotaPartTime(turno(3, Ruolo.CAMERIERE, LUNEDI, 1, 3), contratti).isEmpty());
        assertEquals(1, vincoli.verificaQuotaPartTime(turno(3, Ruolo.CAMERIERE, LUNEDI, 1, 2), contratti).size());
        assertEquals(1, vincoli.verificaQuotaPartTime(turno(1, Ruolo.CAMERIERE, LUNEDI, 1), contratti).size());
        assertTrue(vincoli.verificaQuotaPartTime(turno(0, Ruolo.CAMERIERE, LUNEDI), contratti).isEmpty());
    }

    @Test
    void separaRuoliEGiorniERestituisceUnMessaggioComprensibile() {
        var piano = new PianoSettimanale(LUNEDI, List.of(
                turno(2, Ruolo.CAMERIERE, LUNEDI, 1),
                turno(2, Ruolo.CUOCO, LUNEDI, 2),
                turno(2, Ruolo.CAMERIERE, LUNEDI.plusDays(1), 1)));
        var adattatore = new VerificatoreVincoliUS07();
        assertTrue(adattatore.verificaPiano(piano, List.of(), contratti).isEmpty());
        var esito = adattatore.verificaPiano(new PianoSettimanale(LUNEDI,
                List.of(turno(2, Ruolo.CAMERIERE, LUNEDI, 1, 2))), List.of(), contratti);
        assertEquals(1, esito.size());
        String testo = esito.getFirst();
        assertTrue(testo.contains("Quota part-time superata"));
        assertTrue(testo.contains(Ruolo.CAMERIERE.toString()));
        assertTrue(testo.contains(LUNEDI.toString()));
        assertTrue(testo.contains("massimo 1"));
        assertTrue(testo.contains("[1, 2]"));
    }

    @Test
    void laQuotaValeSoloPerICamerieri() {
        // un turno da un posto coperto da un part-time: vietato al cameriere, permesso agli altri ruoli
        assertEquals(1, vincoli.verificaQuotaPartTime(turno(1, Ruolo.CAMERIERE, LUNEDI, 1), contratti).size());
        for (Ruolo ruolo : List.of(Ruolo.CUOCO, Ruolo.AIUTO_CUOCO, Ruolo.LAVAPIATTI)) {
            assertTrue(vincoli.verificaQuotaPartTime(turno(1, ruolo, LUNEDI, 1), contratti).isEmpty(), ruolo.name());
            assertTrue(vincoli.verificaQuotaPartTime(turno(2, ruolo, LUNEDI, 1, 2), contratti).isEmpty(), ruolo.name());
        }
    }

    @Test
    void ancheGliAltriRuoliRichiedonoUnContrattoValido() {
        var riga = turno(1, Ruolo.LAVAPIATTI, LUNEDI, 1);
        assertThrows(IllegalArgumentException.class, () -> vincoli.verificaQuotaPartTime(riga, List.of()));
    }

    @Test
    void nonNascondeContrattiMancantiScadutiOSovrapposti() {
        var riga = turno(2, Ruolo.CAMERIERE, LUNEDI, 1);
        assertThrows(IllegalArgumentException.class, () -> vincoli.verificaQuotaPartTime(riga, List.of()));
        var scaduto = contratto(1, TipoContratto.PART_TIME);
        scaduto.setDataFine(LUNEDI.minusDays(1));
        assertThrows(IllegalArgumentException.class, () -> vincoli.verificaQuotaPartTime(riga, List.of(scaduto)));
        assertThrows(IllegalArgumentException.class, () -> vincoli.verificaQuotaPartTime(riga,
                List.of(contratti.getFirst(), contratto(1, TipoContratto.FULL_TIME))));
    }

    private DipendenteRepository dipendenti() {
        return new DipendenteRepository() {
            @Override public List<Dipendente> trovaTutti(LocalDate data) {
                return contratti.stream().map(c -> new Dipendente(c.getDipendenteId(), "Nome", "Cognome",
                        Ruolo.CAMERIERE, c)).toList();
            }
        };
    }
    private GenerazioneTurniService generatore(int posti) {
        return new GenerazioneTurniService(new FabbisognoRepository() {
            @Override public List<Fabbisogno> trovaTutti() {
                return List.of(new Fabbisogno(Ruolo.CAMERIERE, TipoGiorno.FESTIVO, Servizio.PRANZO, posti));
            }
        }, dipendenti(), new DisponibilitaRepository() {
            @Override public List<Disponibilita> trovaPerDipendente(int id) {
                return List.of(new Disponibilita(id, DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(17, 0)));
            }
        }, new VerificatoreVincoliUS07());
    }

    @Test
    void generatoreScartaIlSecondoPartTimeESceglieUnFullTime() throws Exception {
        var orari = Map.of(Servizio.PRANZO, new OrarioServizio(LocalTime.of(11, 30), LocalTime.of(16, 0)),
                Servizio.CENA, new OrarioServizio(LocalTime.of(18, 0), LocalTime.of(0, 30)));
        var esito = generatore(2).genera(LUNEDI, orari, Set.of(LUNEDI), List.of());
        assertEquals(List.of(1, 3), esito.piano().assegnazioni().stream().map(AssegnazioneTurno::dipendenteId).toList());
        assertTrue(esito.controlloEseguito());
        assertTrue(esito.violazioni().isEmpty());
        var singolo = generatore(1).genera(LUNEDI, orari, Set.of(LUNEDI), List.of());
        assertEquals(List.of(3), singolo.piano().assegnazioni().stream().map(AssegnazioneTurno::dipendenteId).toList());
    }

    @Test
    void modificaManualeVerificaLaSolaAssegnazioneSostitutiva() throws Exception {
        var servizio = new ModificaPianoService(new PianoRepository() {
            @Override public List<AssegnazioneTurno> trovaConfinanti(LocalDate data) { return List.of(); }
        }, dipendenti(), generatore(2)::verificaProposta);
        var nonValido = new PianoSettimanale(LUNEDI, List.of(turno(2, Ruolo.CAMERIERE, LUNEDI, 1, 2)));
        var valido = new PianoSettimanale(LUNEDI, List.of(turno(2, Ruolo.CAMERIERE, LUNEDI, 1, 3)));
        assertEquals(1, servizio.verifica(nonValido).violazioni().size());
        assertTrue(servizio.verifica(valido).violazioni().isEmpty());
        assertEquals(2, valido.assegnazioni().size());
        assertEquals(1, servizio.verifica(nonValido).violazioni().size());
    }
}
