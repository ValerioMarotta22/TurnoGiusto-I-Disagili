package it.disagili.turnogiusto.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Passa da una finestra all'altra: accesso -> applicazione, e ritorno all'accesso quando si esce.
 * Usa sempre lo stesso Stage (la finestra), cambiandone solo il contenuto.
 */
public final class Navigazione {

    private static final String CARTELLA = "/it/disagili/turnogiusto/";

    private Navigazione() {
    }

    /** Mostra la schermata di accesso. */
    public static void mostraLogin(Stage finestra) {
        finestra.setTitle("TurnoGiusto - Accesso");
        finestra.setScene(new Scene(carica("ui/login-view.fxml"), 460, 440));
        finestra.centerOnScreen();
        finestra.show();
    }

    /** Mostra l'applicazione vera e propria. Va chiamata solo dopo un accesso riuscito. */
    public static void mostraApplicazione(Stage finestra) {
        finestra.setTitle("TurnoGiusto");
        finestra.setScene(new Scene(carica("main-view.fxml"), 1000, 700));
        finestra.centerOnScreen();
        finestra.show();
    }

    private static Parent carica(String nomeFile) {
        try {
            return new FXMLLoader(Navigazione.class.getResource(CARTELLA + nomeFile)).load();
        } catch (IOException errore) {
            throw new UncheckedIOException("Impossibile aprire " + nomeFile, errore);
        }
    }
}
