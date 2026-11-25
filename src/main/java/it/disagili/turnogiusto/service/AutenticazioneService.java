package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.TipoUtente;
import it.disagili.turnogiusto.model.Utente;
import it.disagili.turnogiusto.repository.DipendenteRepository;
import it.disagili.turnogiusto.repository.UtenteRepository;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/**
 * L'accesso: controlla nome utente e password.
 *
 * Scelte di sicurezza:
 *  - il risultato e' lo stesso sia se il nome utente non esiste sia se la password e' sbagliata
 *    (Optional.empty()): chi sbaglia non puo' capire quali nomi esistono;
 *  - se il nome utente non esiste si fa comunque un controllo "finto": il tempo di risposta e'
 *    simile e non rivela quali nomi esistono;
 *  - la password ricevuta (char[]) viene cancellata dalla memoria appena finito.
 */
public class AutenticazioneService {

    // Un hash qualsiasi, usato solo quando il nome utente non esiste, per fare lo stesso lavoro.
    private static final String HASH_FINTO = PasswordHasher.hash("password-che-non-esiste".toCharArray());

    private final UtenteRepository utenti;
    private final DipendenteRepository dipendenti;

    public AutenticazioneService() {
        this(new UtenteRepository(), new DipendenteRepository());
    }

    // Per i test: permette di passare repository finti.
    AutenticazioneService(UtenteRepository utenti, DipendenteRepository dipendenti) {
        this.utenti = Objects.requireNonNull(utenti);
        this.dipendenti = Objects.requireNonNull(dipendenti);
    }

    /**
     * @return l'utente se le credenziali sono giuste, altrimenti Optional.empty()
     * @throws SQLException se il database non risponde (e' un problema diverso da "password sbagliata")
     */
    public Optional<Utente> accedi(String username, char[] password) throws SQLException {
        try {
            if (username == null || username.isBlank() || password == null || password.length == 0) {
                return Optional.empty();
            }
            Optional<UtenteRepository.Credenziali> trovate = utenti.trovaPerUsername(username.trim());
            if (trovate.isEmpty()) {
                PasswordHasher.verifica(password, HASH_FINTO); // lavoro "a vuoto", il risultato si butta
                return Optional.empty();
            }
            UtenteRepository.Credenziali credenziali = trovate.get();
            if (!PasswordHasher.verifica(password, credenziali.hashPassword())) {
                return Optional.empty();
            }
            return Optional.of(completa(credenziali.utente()));
        } finally {
            PasswordHasher.cancella(password);
        }
    }

    // Per un dipendente si aggiunge se e' part-time oggi: serve a decidere se mostrargli "Le mie disponibilita'".
    private Utente completa(Utente utente) throws SQLException {
        if (utente.tipo() != TipoUtente.DIPENDENTE) {
            return utente;
        }
        boolean partTime = dipendenti.trovaTutti().stream()
                .anyMatch(d -> d.getId() == utente.dipendenteId() && d.isPartTime());
        return utente.conPartTime(partTime);
    }
}
