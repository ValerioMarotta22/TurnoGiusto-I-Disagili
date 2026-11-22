package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Disponibilita;
import it.disagili.turnogiusto.model.Fabbisogno;
import it.disagili.turnogiusto.model.OrarioServizio;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.model.TipoGiorno;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.repository.DipendenteRepository;
import it.disagili.turnogiusto.repository.DisponibilitaRepository;
import it.disagili.turnogiusto.repository.FabbisognoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Verifica l'algoritmo e il passaggio della proposta a US07 senza dipendere da MySQL. */
class GenerazioneTurniServiceTest {
    private static final LocalDate LUNEDI = LocalDate.of(2026, 11, 23);
    private static final Map<Servizio, OrarioServizio> ORARI = Map.of(
            Servizio.PRANZO, orario(12, 15), Servizio.CENA, orario(18, 2));

    @Test
    void copreIlFabbisognoInOrdineDiIdAncheSeLeAnagraficheArrivanoInvertite() throws SQLException {
        GenerazioneTurniService service = service(List.of(fabbisogno(TipoGiorno.FESTIVO, Servizio.CENA, 2)),
                List.of(fullTime(9), fullTime(4), fullTime(2)), Map.of(), null);
        var esito = service.genera(LUNEDI, ORARI, Set.of(LUNEDI), List.of());

        Turno turno = esito.piano().turni().getFirst();
        assertEquals(List.of(2, 4), turno.assegnazioni().stream().map(AssegnazioneTurno::dipendenteId).toList());
        assertEquals(LUNEDI.plusDays(1).atTime(2, 0), turno.fine());
        assertEquals(0, esito.piano().postiScoperti());
        assertFalse(esito.controlloEseguito());
    }

    @Test
    void lasciaScopertoChiNonPuoEssereCopertoDaRuoloFasciaOContratto() throws SQLException {
        Dipendente cuoco = persona(1, Ruolo.CUOCO, TipoContratto.FULL_TIME, LUNEDI, null);
        Dipendente scaduto = persona(2, Ruolo.CAMERIERE, TipoContratto.FULL_TIME, LUNEDI.minusDays(5), LUNEDI.minusDays(1));
        Dipendente futuro = persona(3, Ruolo.CAMERIERE, TipoContratto.FULL_TIME, LUNEDI.plusDays(1), null);
        Dipendente partTime = persona(4, Ruolo.CAMERIERE, TipoContratto.PART_TIME, LUNEDI, null);
        Dipendente partTimeSenzaFasce = persona(5, Ruolo.CAMERIERE, TipoContratto.PART_TIME, LUNEDI, null);
        Dipendente validoSoloOggi = persona(6, Ruolo.CAMERIERE, TipoContratto.FULL_TIME, LUNEDI, LUNEDI);
        Map<Integer, List<Disponibilita>> finestre = Map.of(4, List.of(fascia(4, DayOfWeek.MONDAY, 18, 23)));
        List<Dipendente> nonIdonei = List.of(cuoco, scaduto, futuro, partTime, partTimeSenzaFasce);
        List<Fabbisogno> richiesta = List.of(fabbisogno(TipoGiorno.FESTIVO, Servizio.CENA, 3));

        var senzaIdonei = service(richiesta, nonIdonei, finestre, null).genera(LUNEDI, ORARI, Set.of(LUNEDI), List.of());
        assertEquals(3, senzaIdonei.piano().postiScoperti());
        assertTrue(senzaIdonei.piano().assegnazioni().isEmpty());

        List<Dipendente> candidati = new ArrayList<>(nonIdonei);
        candidati.add(validoSoloOggi);
        var parziale = service(richiesta, candidati, finestre, null).genera(LUNEDI, ORARI, Set.of(LUNEDI), List.of());
        assertEquals(List.of(6), parziale.piano().assegnazioni().stream().map(AssegnazioneTurno::dipendenteId).toList());
        assertEquals(2, parziale.piano().postiScoperti());
    }

    @Test
    void ilFestivoPrevaleSulWeekendEIgnoraLeRigheAZero() throws SQLException {
        LocalDate domenica = LUNEDI.plusDays(6);
        GenerazioneTurniService service = service(List.of(fabbisogno(TipoGiorno.WEEKEND, Servizio.CENA, 1),
                fabbisogno(TipoGiorno.FESTIVO, Servizio.PRANZO, 2), fabbisogno(TipoGiorno.FERIALE, Servizio.CENA, 0)),
                List.of(), Map.of(), null);

        List<Turno> turni = service.genera(LUNEDI, ORARI, Set.of(domenica), List.of()).piano().turni();
        assertEquals(2, turni.size());
        assertEquals(LUNEDI.plusDays(5).atTime(18, 0), turni.getFirst().inizio());
        assertEquals(domenica.atTime(12, 0), turni.getLast().inizio());
        assertEquals(2, turni.getLast().postiRichiesti());
    }

    @Test
    void unisceLeFasceContigueDaDomenicaSenzaCoprireLeInterruzioni() throws SQLException {
        Dipendente partTime = persona(4, Ruolo.CAMERIERE, TipoContratto.PART_TIME, LUNEDI, null);
        Map<Servizio, OrarioServizio> orari = Map.of(Servizio.PRANZO, orario(1, 4), Servizio.CENA, orario(18, 23));
        var richiesta = List.of(fabbisogno(TipoGiorno.FESTIVO, Servizio.PRANZO, 1));
        var domenica = fascia(4, DayOfWeek.SUNDAY, 21, 2);
        var lunedi = fascia(4, DayOfWeek.MONDAY, 2, 4);
        var coperto = service(richiesta, List.of(partTime), Map.of(4, List.of(lunedi, domenica)), null)
                .genera(LUNEDI, orari, Set.of(LUNEDI), List.of());
        assertEquals(0, coperto.piano().postiScoperti());

        var conInterruzione = service(richiesta, List.of(partTime),
                Map.of(4, List.of(domenica, fascia(4, DayOfWeek.MONDAY, 3, 4))), null)
                .genera(LUNEDI, orari, Set.of(LUNEDI), List.of());
        assertEquals(1, conInterruzione.piano().postiScoperti());
    }

    @Test
    void rifiutaSovrapposizioniConLoStoricoEConLeAssegnazioniAppenaGenerate() throws SQLException {
        Map<Servizio, OrarioServizio> orari = Map.of(Servizio.PRANZO, orario(12, 16), Servizio.CENA, orario(15, 19));
        var richieste = List.of(fabbisogno(TipoGiorno.FESTIVO, Servizio.PRANZO, 1),
                fabbisogno(TipoGiorno.FESTIVO, Servizio.CENA, 2));
        AssegnazioneTurno storico = assegnazione(1, LUNEDI.minusDays(1).atTime(22, 0), LUNEDI.atTime(13, 0));
        var esito = service(richieste, List.of(fullTime(1), fullTime(2)), Map.of(), null)
                .genera(LUNEDI, orari, Set.of(LUNEDI), List.of(storico));

        assertEquals(2, esito.piano().turni().getFirst().assegnazioni().getFirst().dipendenteId());
        assertEquals(1, esito.piano().turni().getLast().assegnazioni().getFirst().dipendenteId());
        assertEquals(1, esito.piano().postiScoperti());
    }

    @Test
    void consegnaAUs07LaPropostaProgressivaIContrattiDelleDateEITurniConfinanti() throws SQLException {
        // Simula il rifiuto US07 sul riposo al lunedì e sul monte ore dopo la prima assegnazione.
        AssegnazioneTurno domenica = assegnazione(1, LUNEDI.minusDays(1).atTime(22, 0), LUNEDI.atTime(2, 0));
        Dipendente precedente = persona(1, Ruolo.CAMERIERE, TipoContratto.FULL_TIME, LUNEDI.minusDays(7), LUNEDI.minusDays(1));
        Contratto contrattoPrecedente = precedente.getContratto();
        contrattoPrecedente.setId(100);
        var richieste = List.of(fabbisogno(TipoGiorno.FESTIVO, Servizio.PRANZO, 2));
        GenerazioneTurniService service = service(richieste,
                List.of(precedente, fullTime(1), fullTime(2), fullTime(3), fullTime(4)), Map.of(),
                (settimana, proposta, confinanti, contratti) -> {
                    assertEquals(LUNEDI, settimana);
                    assertEquals(List.of(domenica), confinanti);
                    assertEquals(List.of(100, 1, 2, 3, 4), contratti.stream().map(Contratto::getId).toList());
                    List<Integer> ids = proposta.stream().map(AssegnazioneTurno::dipendenteId).toList();
                    if (ids.equals(List.of(1))) return List.of("Riposo insufficiente");
                    if (ids.equals(List.of(2, 3))) return List.of("Monte ore superato");
                    return List.of();
                });

        var esito = service.genera(LUNEDI, ORARI, Set.of(LUNEDI), List.of(domenica));
        assertTrue(esito.controlloEseguito());
        assertEquals(List.of(2, 4), esito.piano().assegnazioni().stream().map(AssegnazioneTurno::dipendenteId).toList());
        assertEquals(0, esito.piano().postiScoperti());
        assertTrue(esito.violazioni().isEmpty());
    }

    @Test
    void verificaLaModificaSullInteroPianoConIlSoloTurnoSostitutivo() throws SQLException {
        AssegnazioneTurno vecchia = assegnazione(3, LUNEDI.atTime(12, 0), LUNEDI.atTime(15, 0));
        AssegnazioneTurno nuova = assegnazione(3, LUNEDI.plusDays(1).atTime(12, 0), LUNEDI.plusDays(1).atTime(15, 0));
        AssegnazioneTurno conservata = assegnazione(2, LUNEDI.atTime(12, 0), LUNEDI.atTime(15, 0));
        PianoSettimanale proposta = new PianoSettimanale(LUNEDI, List.of(
                new Turno(Servizio.PRANZO, Ruolo.CAMERIERE, conservata.inizio(), conservata.fine(), 2, List.of(conservata)),
                new Turno(Servizio.PRANZO, Ruolo.CAMERIERE, nuova.inizio(), nuova.fine(), 1, List.of(nuova))));
        var service = service(List.of(), List.of(fullTime(2), fullTime(3)), Map.of(), (inizio, turni, confinanti, contratti) -> {
            assertEquals(List.of(conservata, nuova), turni);
            assertFalse(turni.contains(vecchia));
            return List.of("Riposo settimanale da rivedere");
        });

        assertEquals(List.of("Riposo settimanale da rivedere"), service.verificaProposta(proposta, List.of()));
        // Un vecchio turno della settimana proposto anche come confinante produrrebbe un doppio conteggio.
        assertThrows(IllegalArgumentException.class, () -> service.verificaProposta(proposta, List.of(vecchia)));
    }

    @Test
    void rifiutaUnaSettimanaNonValidaPrimaDiConsultareIlDatabase() {
        var service = new GenerazioneTurniService();
        assertThrows(IllegalArgumentException.class, () -> service.genera(LUNEDI.plusDays(1), ORARI, Set.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.genera(LUNEDI, Map.of(), Set.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.genera(LUNEDI, ORARI, Set.of(LUNEDI.minusDays(1)), List.of()));
    }

    /** I repository di prova restituiscono gli stessi dati che arrivano normalmente dal database. */
    private GenerazioneTurniService service(List<Fabbisogno> richiesti, List<Dipendente> persone,
                                           Map<Integer, List<Disponibilita>> finestre, VerificatorePropostaTurni verifica) {
        return new GenerazioneTurniService(new FabbisognoRepository() {
            @Override public List<Fabbisogno> trovaTutti() { return richiesti; }
        }, new DipendenteRepository() {
            @Override public List<Dipendente> trovaTutti(LocalDate data) {
                return persone.stream().filter(p -> p.getContratto().isValidoIl(data)).toList();
            }
        }, new DisponibilitaRepository() {
            @Override public List<Disponibilita> trovaPerDipendente(int id) { return finestre.getOrDefault(id, List.of()); }
        }, verifica);
    }

    private Dipendente fullTime(int id) {
        return persona(id, Ruolo.CAMERIERE, TipoContratto.FULL_TIME, LUNEDI, null);
    }

    private Dipendente persona(int id, Ruolo ruolo, TipoContratto tipo, LocalDate inizio, LocalDate fine) {
        return new Dipendente(id, "Nome", "Prova", ruolo, new Contratto(id, id, tipo, 20, BigDecimal.TEN, inizio, fine));
    }

    private Fabbisogno fabbisogno(TipoGiorno tipo, Servizio servizio, int numero) {
        return new Fabbisogno(Ruolo.CAMERIERE, tipo, servizio, numero);
    }

    private static OrarioServizio orario(int inizio, int fine) {
        return new OrarioServizio(LocalTime.of(inizio, 0), LocalTime.of(fine, 0));
    }

    private Disponibilita fascia(int id, DayOfWeek giorno, int inizio, int fine) {
        return new Disponibilita(id, giorno, LocalTime.of(inizio, 0), LocalTime.of(fine, 0));
    }

    private AssegnazioneTurno assegnazione(int id, LocalDateTime inizio, LocalDateTime fine) {
        return new AssegnazioneTurno(id, inizio, fine);
    }
}
