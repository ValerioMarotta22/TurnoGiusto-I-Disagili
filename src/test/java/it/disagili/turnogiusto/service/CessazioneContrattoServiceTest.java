package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.repository.ContrattoRepository;
import it.disagili.turnogiusto.service.VerificaTurniPianificati.Stato;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Prove del service con connessione e repository simulati: nessun database.
 * Verificano le decisioni e il protocollo transazionale, non il rollback SQL reale.
 */
class CessazioneContrattoServiceTest {
    private final LocalDate oggi = LocalDate.of(2026, 11, 24);
    private final LocalDate fine = LocalDate.of(2026, 12, 6);
    private final Contratto attuale = new Contratto(12, 5, TipoContratto.FULL_TIME,
            40, BigDecimal.TEN, oggi.minusMonths(1), null);

    /** Tiene separate la modifica provvisoria e quella confermata dal commit. */
    private class Scenario extends ContrattoRepository {
        List<Contratto> contratti = List.of(attuale);
        LocalDate provvisoria, salvata, verificaDal;
        boolean transazione, commit, rollback, chiusa, fallisciScrittura, fallisciCommit;
        int scritture;
        Stato stato = Stato.ASSENTI;
        final Connection connessione = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, metodo, argomenti) -> {
                    switch (metodo.getName()) {
                        case "setAutoCommit" -> transazione = !(boolean) argomenti[0];
                        case "commit" -> {
                            if (fallisciCommit) throw new SQLException("Commit fallito");
                            commit = true;
                            salvata = provvisoria;
                        }
                        case "rollback" -> { rollback = true; provvisoria = null; }
                        case "close" -> chiusa = true;
                        default -> throw new AssertionError("Chiamata inattesa: " + metodo.getName());
                    }
                    return null;
                });

        @Override
        public List<Contratto> trovaPerAggiornamento(Connection c, int id) {
            assertSame(connessione, c);
            assertTrue(transazione);
            assertEquals(5, id);
            return contratti;
        }

        @Override
        public void chiudi(Connection c, int dipendenteId, int contrattoId, LocalDate data)
                throws SQLException {
            assertSame(connessione, c);
            assertEquals(5, dipendenteId);
            assertEquals(12, contrattoId);
            scritture++;
            if (fallisciScrittura) throw new SQLException("Scrittura fallita");
            provvisoria = data;
        }

        ContrattoService service() {
            return new ContrattoService(this, (c, id, data) -> {
                assertSame(connessione, c);
                assertEquals(5, id);
                verificaDal = data;
                return stato;
            }, Clock.fixed(oggi.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC),
                    () -> connessione);
        }
    }

    @Test
    void salvaLaFineConservandoIlContrattoESegnalaITurni() throws Exception {
        var s = new Scenario();
        s.stato = Stato.PRESENTI;
        var esito = s.service().cessa(5, 12);
        assertEquals(fine, s.salvata);
        assertEquals(fine, esito.ultimoGiornoValido());
        assertEquals(12, esito.contrattoId());
        assertEquals(fine.plusDays(1), s.verificaDal);
        assertTrue(esito.richiedeRicontrolloTurni());
        assertTrue(s.commit && s.chiusa);
        assertFalse(s.rollback);
        // Il service non modifica l'oggetto letto né ne cambia l'identità.
        assertNull(attuale.getDataFine());
        assertEquals(40, attuale.getOreSettimanali());
    }

    @Test
    void conservaScadenzeAnterioriOCoincidentiSenzaUpdate() throws Exception {
        for (LocalDate data : List.of(oggi.plusDays(2), fine)) {
            attuale.setDataFine(data);
            var s = new Scenario();
            var esito = s.service().cessa(5, 12);
            assertEquals(data, esito.ultimoGiornoValido());
            assertEquals(data.plusDays(1), s.verificaDal);
            assertEquals(0, s.scritture);
            assertTrue(s.commit);
            assertFalse(esito.richiedeRicontrolloTurni());
        }
    }

    @Test
    void distingueVerificaNonDisponibileDaTurniAssenti() throws Exception {
        var s = new Scenario();
        s.stato = Stato.NON_DISPONIBILE;
        var esito = s.service().cessa(5, 12);
        assertTrue(esito.integrazionePianificazioneDaCompletare());
        assertFalse(esito.richiedeRicontrolloTurni());
    }

    @Test
    void bloccaUnContrattoFuturoSenzaCancellarlo() {
        var s = new Scenario();
        var futuro = new Contratto(13, 5, TipoContratto.FULL_TIME,
                40, BigDecimal.TEN, fine.plusDays(1), null);
        s.contratti = List.of(attuale, futuro);
        var errore = assertThrows(IllegalArgumentException.class, () -> s.service().cessa(5, 12));
        assertTrue(errore.getMessage().contains("contratto futuro"));
        assertEquals(0, s.scritture);
        assertTrue(s.rollback && s.chiusa);
        assertFalse(s.commit);
        assertNull(futuro.getDataFine());
    }

    @Test
    void rifiutaContrattiAssentiSovrappostiOSelezioneObsoleta() {
        for (List<Contratto> dati : List.of(List.<Contratto>of(), List.of(attuale, attuale))) {
            var s = new Scenario();
            s.contratti = dati;
            assertThrows(IllegalArgumentException.class, () -> s.service().cessa(5, 12));
            assertTrue(s.rollback);
            assertEquals(0, s.scritture);
        }
        var s = new Scenario();
        assertThrows(IllegalArgumentException.class, () -> s.service().cessa(5, 99));
        assertTrue(s.rollback);
    }

    @Test
    void annullaSeFallisconoScritturaOCommit() {
        for (boolean erroreScrittura : List.of(true, false)) {
            var s = new Scenario();
            s.fallisciScrittura = erroreScrittura;
            s.fallisciCommit = !erroreScrittura;
            assertThrows(SQLException.class, () -> s.service().cessa(5, 12));
            assertTrue(s.rollback && s.chiusa);
            assertFalse(s.commit);
            assertNull(s.salvata);
            assertNull(s.provvisoria);
        }
    }

    @Test
    void rifiutaEsitiTurniNonValidiPrimaDellaScrittura() {
        for (Stato stato : new Stato[]{null, Stato.NON_RICHIESTA}) {
            var s = new Scenario();
            s.stato = stato;
            assertThrows(IllegalStateException.class, () -> s.service().cessa(5, 12));
            assertTrue(s.rollback);
            assertEquals(0, s.scritture);
        }
    }

    @Test
    void rifiutaIdentificativiNonValidiPrimaDiAprireLaConnessione() {
        var s = new Scenario();
        assertThrows(IllegalArgumentException.class, () -> s.service().cessa(0, 12));
        assertThrows(IllegalArgumentException.class, () -> s.service().cessa(5, 0));
        assertFalse(s.transazione);
    }
}
