package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.Utente;
import it.disagili.turnogiusto.service.AutenticazioneService;
import it.disagili.turnogiusto.service.PasswordHasher;
import it.disagili.turnogiusto.service.SessioneUtente;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Schermata di accesso. Non contiene regole: legge i due campi, chiede al service se le credenziali
 * sono giuste e, se si', ricorda chi e' entrato (SessioneUtente) e apre l'applicazione.
 */
public class LoginController {

    @FXML private TextField username;
    @FXML private PasswordField password;
    @FXML private Button accedi;
    @FXML private Label messaggio;

    private final AutenticazioneService servizio;

    // Cosa fare dopo un accesso riuscito. Di norma apre la finestra principale; nei test si sostituisce.
    private Consumer<Utente> dopoAccesso = utente -> Navigazione.mostraApplicazione((Stage) accedi.getScene().getWindow());

    public LoginController() {
        this(new AutenticazioneService());
    }

    // Permette ai test di passare un service finto.
    LoginController(AutenticazioneService servizio) {
        this.servizio = servizio;
    }

    void impostaDopoAccesso(Consumer<Utente> azione) {
        this.dopoAccesso = azione;
    }

    @FXML
    private void initialize() {
        // Invio nel campo del nome sposta sulla password; Invio nella password accede.
        username.setOnAction(evento -> password.requestFocus());
        password.setOnAction(evento -> onAccedi());
    }

    @FXML
    private void onAccedi() {
        String nome = username.getText() == null ? "" : username.getText().trim();
        // La password si legge come array di caratteri, che il service cancella dopo averla usata.
        char[] chiave = password.getText() == null ? new char[0] : password.getText().toCharArray();
        password.clear(); // il campo si svuota subito, in ogni caso

        if (nome.isEmpty() || chiave.length == 0) {
            PasswordHasher.cancella(chiave);
            errore("Inserisci il nome utente e la password.");
            return;
        }

        accedi.setDisable(true); // evita doppi clic mentre si controlla
        try {
            Optional<Utente> utente = servizio.accedi(nome, chiave);
            if (utente.isPresent()) {
                SessioneUtente.accedi(utente.get());
                dopoAccesso.accept(utente.get());
            } else {
                // Stesso messaggio per "nome inesistente" e "password sbagliata": non si rivela quale dei due.
                errore("Nome utente o password non corretti.");
            }
        } catch (SQLException eccezione) {
            errore("Impossibile contattare il database. Controlla che sia acceso (docker compose up -d).");
        } finally {
            accedi.setDisable(false);
        }
    }

    private void errore(String testo) {
        messaggio.setText(testo);
        if (!messaggio.getStyleClass().contains("errore")) {
            messaggio.getStyleClass().add("errore"); // sfondo rosso tenue, definito nel CSS
        }
    }
}
