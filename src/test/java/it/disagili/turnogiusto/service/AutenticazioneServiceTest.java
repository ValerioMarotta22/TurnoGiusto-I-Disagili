package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.model.TipoUtente;
import it.disagili.turnogiusto.model.Utente;
import it.disagili.turnogiusto.repository.DipendenteRepository;
import it.disagili.turnogiusto.repository.UtenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** L'accesso: non serve il database, repository e anagrafiche sono finti. */
class AutenticazioneServiceTest {

    // Un hash solo, calcolato una volta (e' lento di proposito).
    private static final String HASH = PasswordHasher.hash("segreta123".toCharArray());

    private static final Utente TITOLARE = new Utente(1, "titolare", TipoUtente.TITOLARE, null, "Titolare", false);
    private static final Utente ANNA = new Utente(2, "anna.gialli", TipoUtente.DIPENDENTE, 5, "Anna Gialli", false);
    private static final Utente SARA = new Utente(3, "sara.neri", TipoUtente.DIPENDENTE, 4, "Sara Neri", false);

    private List<UtenteRepository.Credenziali> archivio;
    private int ricerche;
    private boolean databaseSpento;
    private AutenticazioneService service;

    @BeforeEach
    void prepara() {
        archivio = List.of(
                new UtenteRepository.Credenziali(TITOLARE, HASH),
                new UtenteRepository.Credenziali(ANNA, HASH),
                new UtenteRepository.Credenziali(SARA, HASH));
        ricerche = 0;
        databaseSpento = false;

        UtenteRepository utenti = new UtenteRepository() {
            @Override
            public Optional<Credenziali> trovaPerUsername(String username) throws SQLException {
                ricerche++;
                if (databaseSpento) throw new SQLException("spento");
                return archivio.stream().filter(c -> c.utente().username().equalsIgnoreCase(username)).findFirst();
            }
        };
        // Anna e' a tempo pieno, Sara e' part-time
        DipendenteRepository dipendenti = new DipendenteRepository() {
            @Override
            public List<Dipendente> trovaTutti() {
                return List.of(
                        new Dipendente(5, "Anna", "Gialli", Ruolo.CAMERIERE, contratto(5, TipoContratto.FULL_TIME)),
                        new Dipendente(4, "Sara", "Neri", Ruolo.CAMERIERE, contratto(4, TipoContratto.PART_TIME)));
            }
        };
        service = new AutenticazioneService(utenti, dipendenti);
    }

    private static Contratto contratto(int dipendenteId, TipoContratto tipo) {
        return new Contratto(dipendenteId, tipo, 20, new BigDecimal("10.00"), LocalDate.of(2025, 1, 1));
    }

    @Test
    void conLeCredenzialiGiusteIlTitolareEntra() throws Exception {
        Optional<Utente> utente = service.accedi("titolare", "segreta123".toCharArray());

        assertTrue(utente.isPresent());
        assertTrue(utente.get().isTitolare());
        assertFalse(utente.get().partTime());
    }

    @Test
    void ilDipendenteATempoPienoEntraSenzaIlFlagPartTime() throws Exception {
        Utente utente = service.accedi("anna.gialli", "segreta123".toCharArray()).orElseThrow();

        assertEquals(TipoUtente.DIPENDENTE, utente.tipo());
        assertEquals(5, utente.dipendenteId());
        assertFalse(utente.partTime());
    }

    @Test
    void ilDipendentePartTimeEntraConIlFlagPartTime() throws Exception {
        Utente utente = service.accedi("sara.neri", "segreta123".toCharArray()).orElseThrow();

        assertTrue(utente.partTime());
    }

    @Test
    void unaPasswordSbagliataNonFaEntrare() throws Exception {
        assertTrue(service.accedi("titolare", "sbagliata".toCharArray()).isEmpty());
    }

    @Test
    void unNomeUtenteInesistenteDaLoStessoRisultatoDellaPasswordSbagliata() throws Exception {
        // chi sbaglia non deve poter capire quali nomi esistono
        Optional<Utente> inesistente = service.accedi("nessuno", "segreta123".toCharArray());
        Optional<Utente> passwordErrata = service.accedi("titolare", "sbagliata".toCharArray());

        assertEquals(passwordErrata, inesistente);
        assertTrue(inesistente.isEmpty());
    }

    @Test
    void campiVuotiNonFannoNemmenoUnaRicerca() throws Exception {
        assertTrue(service.accedi("", "segreta123".toCharArray()).isEmpty());
        assertTrue(service.accedi("   ", "segreta123".toCharArray()).isEmpty());
        assertTrue(service.accedi("titolare", new char[0]).isEmpty());
        assertTrue(service.accedi(null, "segreta123".toCharArray()).isEmpty());
        assertTrue(service.accedi("titolare", null).isEmpty());

        assertEquals(0, ricerche);
    }

    @Test
    void glispaziIntornoAlNomeUtenteVengonoIgnorati() throws Exception {
        assertTrue(service.accedi("  titolare  ", "segreta123".toCharArray()).isPresent());
    }

    @Test
    void laPasswordRicevutaVieneCancellataDallaMemoria() throws Exception {
        char[] password = "segreta123".toCharArray();

        service.accedi("titolare", password);

        for (char carattere : password) {
            assertEquals('\0', carattere);
        }
    }

    @Test
    void laPasswordVieneCancellataAncheSeLAccessoFallisce() throws Exception {
        char[] password = "sbagliata".toCharArray();

        service.accedi("titolare", password);

        for (char carattere : password) {
            assertEquals('\0', carattere);
        }
    }

    @Test
    void seIlDatabaseESpentoSiDistingueDallaPasswordSbagliata() {
        databaseSpento = true;

        assertThrows(SQLException.class, () -> service.accedi("titolare", "segreta123".toCharArray()));
    }
}
