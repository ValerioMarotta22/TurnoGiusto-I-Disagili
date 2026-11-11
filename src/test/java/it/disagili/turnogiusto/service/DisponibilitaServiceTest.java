package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.repository.DipendentiDisponibilitaJdbc;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DisponibilitaServiceTest {

    private Dipendente dipendente(int id, TipoContratto tipo, LocalDate inizio) {
        Contratto contratto = new Contratto(id, tipo, 20, BigDecimal.TEN, inizio);
        return new Dipendente(id, "Nome", "Cognome", Ruolo.CAMERIERE, contratto);
    }

    @Test void selezionaSoloPartTimeConContrattoValido() throws SQLException {
        LocalDate oggi = LocalDate.now();
        Dipendente valido = dipendente(1, TipoContratto.PART_TIME, oggi);
        Dipendente futuro = dipendente(2, TipoContratto.PART_TIME, oggi.plusDays(1));
        Dipendente fullTime = dipendente(3, TipoContratto.FULL_TIME, oggi);
        Dipendente senza = new Dipendente(4, "Nome", "Cognome", Ruolo.CAMERIERE, null);
        assertEquals(List.of(valido), new DisponibilitaService().selezionaPartTime(
                List.of(valido, futuro, fullTime, senza), oggi));
    }

    @Test void rifiutaDipendentiDuplicatiDallIntegrazione() {
        Dipendente persona = dipendente(1, TipoContratto.PART_TIME, LocalDate.now());
        assertThrows(SQLException.class, () -> new DisponibilitaService().selezionaPartTime(
                List.of(persona, persona), LocalDate.now()));
    }

    @Test void erroreDelRepositoryDelCompagnoNonDiventaListaVuota() {
        DisponibilitaService servizio = new DisponibilitaService(new DipendentiDisponibilitaJdbc() {
            @Override public List<Dipendente> trovaTutti() throws SQLException {
                throw new SQLException("Contratti incoerenti");
            }
        });
        assertThrows(SQLException.class, servizio::consulta);
    }
}
