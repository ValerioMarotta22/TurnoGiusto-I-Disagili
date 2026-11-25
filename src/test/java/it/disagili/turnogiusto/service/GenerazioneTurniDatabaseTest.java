package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Fabbisogno;
import it.disagili.turnogiusto.model.OrarioServizio;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.TipoGiorno;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.repository.DipendenteRepository;
import it.disagili.turnogiusto.repository.FabbisognoRepository;
import it.disagili.turnogiusto.support.DatabaseDisponibilitaSupport;
import it.disagili.turnogiusto.ui.GenerazioneTurniController;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Verifica le letture usate dalla US06A con MySQL di Docker Compose. */
class GenerazioneTurniDatabaseTest extends DatabaseDisponibilitaSupport {
    @Test
    void caricaUnContrattoFuturoNelGiornoDelTurnoInclusoIlGiornoDiScadenza() throws SQLException {
        LocalDate inizio = LocalDate.now().plusDays(10);
        LocalDate fine = inizio.plusDays(2);
        aggiungiContratto(senzaContratto, "FULL_TIME", inizio, fine);
        DipendenteRepository repository = new DipendenteRepository();

        assertNull(dipendente(repository.trovaTutti()).getContratto());
        assertNull(dipendente(repository.trovaTutti(inizio.minusDays(1))).getContratto());
        assertEquals(20, dipendente(repository.trovaTutti(inizio)).getContratto().getOreSettimanali());
        assertEquals(fine, dipendente(repository.trovaTutti(fine)).getContratto().getDataFine());
        assertNull(dipendente(repository.trovaTutti(fine.plusDays(1))).getContratto());
    }

    @Test
    void generaRigheConLeQuantitaSalvatePerTipoDiGiornoServizioERuolo() throws SQLException {
        // Usa le righe esistenti (in CI quelle del seed), senza alterare le configurazioni del titolare.
        List<Fabbisogno> configurati = new FabbisognoRepository().trovaTutti();
        LocalDate lunedi = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Map<Servizio, OrarioServizio> orari = Map.of(
                Servizio.PRANZO, new OrarioServizio(LocalTime.of(12, 0), LocalTime.of(15, 0)),
                Servizio.CENA, new OrarioServizio(LocalTime.of(18, 0), LocalTime.of(2, 0)));
        var piano = new GenerazioneTurniService().genera(lunedi, orari, Set.of(lunedi.plusDays(6)), List.of()).piano();

        for (int indice = 0; indice < 7; indice++) {
            LocalDate giorno = lunedi.plusDays(indice);
            TipoGiorno tipo = switch (indice) {
                case 5 -> TipoGiorno.WEEKEND;
                case 6 -> TipoGiorno.FESTIVO;
                default -> TipoGiorno.FERIALE;
            };
            List<Fabbisogno> attesi = configurati.stream()
                    .filter(fabbisogno -> fabbisogno.getTipoGiorno() == tipo && fabbisogno.getNumeroMinimoDipendenti() > 0)
                    .toList();
            List<Turno> generati = piano.turni().stream().filter(turno -> turno.inizio().toLocalDate().equals(giorno)).toList();
            assertEquals(attesi.size(), generati.size(), "Numero di righe per " + giorno);
            for (Fabbisogno fabbisogno : attesi) {
                Turno trovato = generati.stream().filter(turno -> turno.servizio() == fabbisogno.getServizio()
                        && turno.ruolo() == fabbisogno.getRuolo()).findFirst().orElseThrow();
                assertEquals(fabbisogno.getNumeroMinimoDipendenti(), trovato.postiRichiesti());
                assertEquals(trovato.postiRichiesti(), trovato.assegnazioni().size() + trovato.postiScoperti());
            }
        }
    }

    /**
     * La schermata "Genera turni" deve usare i controlli di US07. Prima non li usava: il suo servizio veniva
     * creato senza verificatore e la bozza assegnava la stessa persona a pranzo e cena nello stesso giorno.
     */
    @Test
    void laSchermataDiGenerazioneApplicaIVincoli() throws Exception {
        var campo = GenerazioneTurniController.class.getDeclaredField("servizio");
        campo.setAccessible(true);
        var servizio = (GenerazioneTurniService) campo.get(new GenerazioneTurniController());
        LocalDate lunedi = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Map<Servizio, OrarioServizio> orari = Map.of(
                Servizio.PRANZO, new OrarioServizio(LocalTime.of(11, 30), LocalTime.of(16, 0)),
                Servizio.CENA, new OrarioServizio(LocalTime.of(18, 0), LocalTime.of(0, 30)));

        var esito = servizio.genera(lunedi, orari, Set.of(), List.of());

        assertTrue(esito.controlloEseguito(), "I controlli sui vincoli devono essere collegati");
        assertEquals(List.of(), esito.violazioni());
        // Regola "un solo servizio al giorno": nessuno ha pranzo e cena nello stesso giorno.
        Set<String> giaVisti = new java.util.HashSet<>();
        for (var assegnazione : esito.piano().assegnazioni()) {
            String chiave = assegnazione.dipendenteId() + "@" + assegnazione.inizio().toLocalDate();
            assertTrue(giaVisti.add(chiave), "Doppio servizio nello stesso giorno: " + chiave);
        }
    }

    /** Cerca solo l'anagrafica creata dal test fra quelle presenti nel database. */
    private Dipendente dipendente(List<Dipendente> persone) {
        return persone.stream().filter(persona -> persona.getId() == senzaContratto).findFirst().orElseThrow();
    }
}
