package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.TipoUtente;
import it.disagili.turnogiusto.model.Utente;
import it.disagili.turnogiusto.service.AutenticazioneService;
import it.disagili.turnogiusto.service.SessioneUtente;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static it.disagili.turnogiusto.ui.JavaFxTestSupport.suJavaFx;
import static org.junit.jupiter.api.Assertions.*;

/** La schermata di accesso, con un service finto: non serve il database. */
@ExtendWith(JavaFxTestSupport.class)
class LoginControllerTest {

    private static final Utente TITOLARE = new Utente(1, "titolare", TipoUtente.TITOLARE, null, "Titolare", false);

    private static class ServiceFinto extends AutenticazioneService {
        Optional<Utente> risposta = Optional.empty();
        boolean databaseSpento;
        int chiamate;
        String ultimoNome;

        @Override
        public Optional<Utente> accedi(String username, char[] password) throws SQLException {
            chiamate++;
            ultimoNome = username;
            if (databaseSpento) throw new SQLException("spento");
            return risposta;
        }
    }

    private ServiceFinto service;
    private List<Utente> accessi;
    private Parent radice;

    @BeforeEach
    void prepara() throws Exception {
        SessioneUtente.esci();
        service = new ServiceFinto();
        accessi = new ArrayList<>();
        LoginController controller = new LoginController(service);
        controller.impostaDopoAccesso(accessi::add); // nei test non si apre la finestra principale
        radice = suJavaFx(() -> {
            FXMLLoader caricatore = new FXMLLoader(getClass().getResource("/it/disagili/turnogiusto/ui/login-view.fxml"));
            caricatore.setControllerFactory(tipo -> controller);
            return caricatore.load();
        });
    }

    @AfterEach
    void esci() {
        SessioneUtente.esci();
    }

    @SuppressWarnings("unchecked")
    private <T> T nodo(String id) {
        return (T) radice.lookup("#" + id);
    }

    private void inserisci(String nome, String password) throws Exception {
        suJavaFx(() -> {
            ((TextField) nodo("username")).setText(nome);
            ((PasswordField) nodo("password")).setText(password);
            return null;
        });
    }

    private void premiAccedi() throws Exception {
        suJavaFx(() -> {
            ((Button) nodo("accedi")).fire();
            return null;
        });
    }

    private String messaggio() throws Exception {
        return suJavaFx(() -> ((Label) nodo("messaggio")).getText());
    }

    @Test
    void conLeCredenzialiGiusteSiEntraESiRicordaChiE() throws Exception {
        service.risposta = Optional.of(TITOLARE);
        inserisci("titolare", "titolare123");

        premiAccedi();

        assertEquals(List.of(TITOLARE), accessi);
        assertEquals(Optional.of(TITOLARE), SessioneUtente.corrente());
        assertEquals("titolare", service.ultimoNome);
    }

    @Test
    void laPasswordSiSvuotaSubitoDopoLInvio() throws Exception {
        service.risposta = Optional.empty();
        inserisci("titolare", "sbagliata");

        premiAccedi();

        assertEquals("", suJavaFx(() -> ((PasswordField) nodo("password")).getText()));
    }

    @Test
    void conCredenzialiSbagliateNonSiEntraEIlMessaggioNonDiceCosaEraSbagliato() throws Exception {
        service.risposta = Optional.empty();
        inserisci("titolare", "sbagliata");

        premiAccedi();

        assertTrue(accessi.isEmpty());
        assertTrue(SessioneUtente.corrente().isEmpty());
        assertEquals("Nome utente o password non corretti.", messaggio());
        assertTrue(suJavaFx(() -> ((Label) nodo("messaggio")).getStyleClass().contains("errore")));
    }

    @Test
    void conICampiVuotiNonSiInterrogaIlService() throws Exception {
        inserisci("", "");
        premiAccedi();
        assertTrue(messaggio().contains("Inserisci"));

        inserisci("titolare", "");
        premiAccedi();
        assertTrue(messaggio().contains("Inserisci"));

        inserisci("   ", "abc");
        premiAccedi();
        assertTrue(messaggio().contains("Inserisci"));

        assertEquals(0, service.chiamate);
        assertTrue(accessi.isEmpty());
    }

    @Test
    void seIlDatabaseESpentoSiDiceChiaramenteEnonSiEntra() throws Exception {
        service.databaseSpento = true;
        inserisci("titolare", "titolare123");

        premiAccedi();

        assertTrue(messaggio().contains("database"));
        assertTrue(accessi.isEmpty());
        assertTrue(SessioneUtente.corrente().isEmpty());
    }

    @Test
    void dopoUnTentativoIlPulsanteTornaAttivo() throws Exception {
        service.risposta = Optional.empty();
        inserisci("titolare", "sbagliata");

        premiAccedi();

        assertFalse(suJavaFx(() -> ((Button) nodo("accedi")).isDisabled()));
    }
}
