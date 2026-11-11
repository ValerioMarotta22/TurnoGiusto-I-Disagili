package it.disagili.turnogiusto;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        // All'avvio si apre la finestra principale con il menu (main-view.fxml).
        // Le schermate delle storie si aprono da li', non da qui.
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 900, 600);
        stage.setTitle("TurnoGiusto");
        stage.setScene(scene);
        stage.show();
    }
}
