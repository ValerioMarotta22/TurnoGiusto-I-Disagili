package it.disagili.turnogiusto.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.io.IOException;

/**
 * Controller della finestra principale: gestisce il menu e mostra, nell'area
 * centrale, la schermata scelta. Non contiene regole ne' SQL: apre solo le
 * schermate, ognuna delle quali ha il suo controller.
 */
public class MainController {

    // La cartella dove stanno i file FXML (in src/main/resources). Il "/"
    // iniziale significa "parti dalla radice delle risorse".
    private static final String CARTELLA_SCHERMATE = "/it/disagili/turnogiusto/";

    // L'area centrale della finestra: ha fx:id="contenuto" in main-view.fxml.
    @FXML
    private StackPane contenuto;

    // ======================================================================
    // LE VOCI DEL MENU
    // Ogni metodo corrisponde a un onAction="#nomeMetodo" di main-view.fxml.
    // ======================================================================

    @FXML
    private void apriFabbisogno() {
        caricaSchermata("fabbisogno-view.fxml");
    }

    // Segnaposto: finche' la schermata non esiste mostrano un avviso.
    // Chi sviluppa US-01/US-02 sostituisce il corpo con
    //     caricaSchermata("dipendenti-view.fxml");
    @FXML
    private void apriDipendenti() {
        caricaSchermata("ui/elenco-dipendenti.fxml");
    }

    // Come sopra, per US-03 e US-04.
    @FXML
    private void apriDisponibilita() {
        mostraNonDisponibile("Disponibilità part-time (US-03, US-04)");
    }

    // ======================================================================
    // METODI DI AIUTO
    // ======================================================================

    // Carica un file FXML e lo mette al centro della finestra.
    private void caricaSchermata(String nomeFile) {
        try {
            // PASSO 1 - FXMLLoader legge il file FXML e crea la schermata (e il
            // suo controller, indicato da fx:controller dentro il file).
            Node schermata = new FXMLLoader(
                    MainController.class.getResource(CARTELLA_SCHERMATE + nomeFile)).load();

            // PASSO 2 - setAll toglie quello che c'era e mette la schermata nuova.
            contenuto.getChildren().setAll(schermata);
        } catch (IOException e) {
            // Il file manca o e' scritto male (es. un refuso in un attributo):
            // lo dico a schermo invece di lasciare la finestra vuota.
            mostraMessaggio("Impossibile aprire la schermata " + nomeFile + ": " + e.getMessage());
        }
    }

    private void mostraNonDisponibile(String nomeSchermata) {
        mostraMessaggio(nomeSchermata + ": schermata non ancora disponibile.");
    }

    private void mostraMessaggio(String testo) {
        contenuto.getChildren().setAll(new Label(testo));
    }
}
