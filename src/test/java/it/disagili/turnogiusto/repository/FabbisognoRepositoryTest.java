package it.disagili.turnogiusto.repository;

import it.disagili.turnogiusto.model.Fabbisogno;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.TipoGiorno;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FabbisognoRepositoryTest {

    private final FabbisognoRepository repository = new FabbisognoRepository();

    //PRIMA DI OGNI TEST
    @BeforeEach
    void preparaIlDatabase() throws SQLException {
        //controllo che il database risponda. se fallisce connessione assumeTrue(false,...) SALTA il test
        try (Connection c = Database.getConnection()) {
            //connessione riuscita
        } catch(SQLException e) {
            Assumptions.assumeTrue(false, "Database non raggiungibile: avvia 'docker compose up -d' per eseguire questo test");
        }

        //controllo ed elimino se test precedenti hanno lasciato la riga di prova
        eliminaFabbisognoDiProva();
    }

    //DOPO OGNI TEST
    @AfterEach
    void pulisciIlDatabase() throws SQLException {
        //elimino riga di prova, rimane database puro (solo i dati del seed)
        //ignoro il catch perchè se il database è spento il test già è stato saltato
        try {
            eliminaFabbisognoDiProva();
        } catch(SQLException e){
            //niente da fare
        }
    }

    //TEST 1: salvo e rileggo un fabbisogno nuovo

    @Test
    void unFabbisognoSalvatoPuoEssereRieletto() throws SQLException {

        repository.salva(new Fabbisogno(Ruolo.LAVAPIATTI, TipoGiorno.FERIALE, Servizio.PRANZO, 1));

        //ACT: rileggo tutto e cerco la mia riga
        Fabbisogno letto = cercaFabbisognoDiProva(repository.trovaTutti());

        //ASSERT: controllo i risultati
        assertNotNull(letto, "il fabbisogno salvato deve comparire in trovaTutti()");
        assertEquals(1, letto.getNumeroMinimoDipendenti());
        assertTrue(letto.getId() > 0, "il database deve aver assegnato un id");
    }

    //TEST 2: salvo due volte la stessa combinazione (si aggiorna, non si duplica)

    @Test
    void salvareDueVolteLaStessaCombinazioneAggiornaIlNumero() throws SQLException {
        repository.salva(new Fabbisogno(Ruolo.LAVAPIATTI, TipoGiorno.FERIALE, Servizio.PRANZO, 1));
        repository.salva(new Fabbisogno(Ruolo.LAVAPIATTI, TipoGiorno.FERIALE, Servizio.PRANZO, 3));

        //conto quante righe ha la combinazione e tengo l'ultima trovata
        int quante = 0;
        Fabbisogno letto = null;
        for (Fabbisogno f : repository.trovaTutti()) {
            if(eLaCombinazioneDiProva(f)) {
                quante++;
                letto = f;
            }
        }

        //deve essercene UNA sola e col numero nuovo
        assertEquals(1,quante, "la stessa combinazione non deve apparire due volte");
        assertEquals(3, letto.getNumeroMinimoDipendenti());
    }

    //METODI DI AIUTO (non sono test, li chiamano i test)

    //dice se un fabbisogno è quello della combinazione di prova
    private static boolean eLaCombinazioneDiProva(Fabbisogno f) {
        return f.getRuolo() == Ruolo.LAVAPIATTI
                && f.getTipoGiorno() == TipoGiorno.FERIALE
                && f.getServizio() == Servizio.PRANZO;
    }

    //cerca nell'elenco la riga di prova; restituisce null se non c'è
    private static Fabbisogno cercaFabbisognoDiProva(List<Fabbisogno> elenco) {
        for(Fabbisogno f : elenco) {
            if(eLaCombinazioneDiProva(f)) {
                return f;
            }
        }
        return null;
    }

    //cancella dal database la riga di prova
    //è una query DELETE, quindi usa PreparedStatement
    private static void eliminaFabbisognoDiProva() throws SQLException {
        String sql = "DELETE FROM fabbisogno WHERE ruolo = ? AND tipo_giorno = ? AND servizio = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, Ruolo.LAVAPIATTI.name());
            ps.setString(2, TipoGiorno.FERIALE.name());
            ps.setString(3, Servizio.PRANZO.name());
            ps.executeUpdate();
        }
    }
}