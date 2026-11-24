package it.disagili.turnogiusto;

import it.disagili.turnogiusto.ui.Navigazione;
import javafx.application.Application;
import javafx.stage.Stage;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) {
        // All'avvio si apre l'ACCESSO. La finestra principale con il menu si apre solo dopo un accesso
        // riuscito (lo fa LoginController tramite Navigazione), e il menu dipende dal tipo di utente.
        Navigazione.mostraLogin(stage);
    }
}
