package it.disagili.turnogiusto.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import it.disagili.turnogiusto.service.DisponibilitaService;
import it.disagili.turnogiusto.ui.ConsultazioneDisponibilitaController;
import it.disagili.turnogiusto.ui.GestioneDisponibilitaController;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

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

    @FXML
    private void apriDisponibilita() {
        try {
            // PASSO 1 - un FXMLLoader per ciascuna delle due schermate.
            FXMLLoader gestioneLoader = new FXMLLoader(MainController.class.getResource(
                    CARTELLA_SCHERMATE + "ui/gestione-disponibilita-view.fxml"));
            FXMLLoader consultazioneLoader = new FXMLLoader(MainController.class.getResource(
                    CARTELLA_SCHERMATE + "ui/consultazione-disponibilita-view.fxml"));

            // PASSO 2 - load() costruisce la schermata e, in automatico, il suo controller.
            Parent gestioneVista = gestioneLoader.load();
            Parent consultazioneVista = consultazioneLoader.load();
            GestioneDisponibilitaController gestione = gestioneLoader.getController();
            ConsultazioneDisponibilitaController consultazione = consultazioneLoader.getController();

            // PASSO 3 - i due controller usano lo STESSO service. Dopo ogni
            // salvataggio, la gestione chiama consultazione::aggiorna.
            DisponibilitaService servizio = new DisponibilitaService();
            consultazione.configura(servizio);
            gestione.configura(servizio, consultazione::aggiorna);

            // PASSO 4 - due schede in un TabPane; "non chiudibili" toglie la X.
            Tab gestioneTab = new Tab("Gestione — dipendente", gestioneVista);
            Tab consultazioneTab = new Tab("Consultazione — titolare", consultazioneVista);
            gestioneTab.setClosable(false);
            consultazioneTab.setClosable(false);
            TabPane schede = new TabPane(gestioneTab, consultazioneTab);
            // Ogni volta che si apre la scheda del titolare, i dati si ricaricano.
            consultazioneTab.setOnSelectionChanged(evento -> {
                if (consultazioneTab.isSelected()) consultazione.aggiorna();
            });

            // PASSO 5 - le schede vanno al centro della finestra, poi si
            // caricano i dipendenti nel menu a tendina della gestione.
            contenuto.getChildren().setAll(schede);
            gestione.caricaDipendenti();
        } catch (IOException e) {
            // FXML mancante o con un refuso: lo dico a schermo.
            mostraMessaggio("Impossibile aprire le disponibilità: " + e.getMessage());
        }
    }

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
