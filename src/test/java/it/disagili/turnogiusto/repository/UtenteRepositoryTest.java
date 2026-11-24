package it.disagili.turnogiusto.repository;

import it.disagili.turnogiusto.model.TipoUtente;
import it.disagili.turnogiusto.service.PasswordHasher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Legge gli account dal MySQL di Docker, cioe' quelli inseriti dal file dei dati di prova (02_seed.sql).
 * Controlla anche che gli hash scritti nel seed siano davvero validi per le password documentate.
 */
@Tag("database")
class UtenteRepositoryTest {

    private final UtenteRepository repository = new UtenteRepository();

    @Test
    void ilTitolareEsisteEIlSuoHashVerificaLaPasswordDiProva() throws SQLException {
        UtenteRepository.Credenziali credenziali = repository.trovaPerUsername("titolare").orElseThrow();

        assertEquals(TipoUtente.TITOLARE, credenziali.utente().tipo());
        assertNull(credenziali.utente().dipendenteId());
        assertEquals("Titolare", credenziali.utente().nome());
        assertTrue(PasswordHasher.verifica("titolare123".toCharArray(), credenziali.hashPassword()));
        assertFalse(PasswordHasher.verifica("dipendente123".toCharArray(), credenziali.hashPassword()));
    }

    @Test
    void unDipendenteELegatoAllaSuaAnagraficaEPrendeIlNomeDaLi() throws SQLException {
        UtenteRepository.Credenziali credenziali = repository.trovaPerUsername("mario.rossi").orElseThrow();

        assertEquals(TipoUtente.DIPENDENTE, credenziali.utente().tipo());
        assertEquals(1, credenziali.utente().dipendenteId());
        assertEquals("Mario Rossi", credenziali.utente().nome());
        assertTrue(PasswordHasher.verifica("dipendente123".toCharArray(), credenziali.hashPassword()));
    }

    @Test
    void ognunoDeiDiciottoDipendentiHaIlSuoAccount() throws SQLException {
        // nomi con l'accento nell'anagrafica ma senza nel nome utente
        assertTrue(repository.trovaPerUsername("nicolo.ferri").isPresent());
        assertTrue(repository.trovaPerUsername("karim.haddad").isPresent());
        assertTrue(repository.trovaPerUsername("giulia.bianchi").isPresent());
    }

    @Test
    void ilNomeUtenteNonDistingueMaiuscoleEMinuscole() throws SQLException {
        assertTrue(repository.trovaPerUsername("TITOLARE").isPresent());
    }

    @Test
    void unNomeInesistenteNonTrovaNiente() throws SQLException {
        assertEquals(Optional.empty(), repository.trovaPerUsername("nessuno"));
    }

    @Test
    void unTentativoDiSqlInjectionNonTrovaNienteENonRompeLaQuery() throws SQLException {
        // il nome utente e' un parametro della query: il testo malevolo e' solo un nome che non esiste
        assertTrue(repository.trovaPerUsername("' OR '1'='1").isEmpty());
        assertTrue(repository.trovaPerUsername("titolare' --").isEmpty());
    }
}
