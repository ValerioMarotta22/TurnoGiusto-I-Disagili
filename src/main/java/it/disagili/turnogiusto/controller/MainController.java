package it.disagili.turnogiusto.controller;

import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Utente;
import it.disagili.turnogiusto.service.Autorizzazioni;
import it.disagili.turnogiusto.service.DisponibilitaService;
import it.disagili.turnogiusto.service.Funzione;
import it.disagili.turnogiusto.service.SessioneUtente;
import it.disagili.turnogiusto.ui.ConsultazioneDisponibilitaController;
import it.disagili.turnogiusto.ui.GestioneDisponibilitaController;
import it.disagili.turnogiusto.ui.Navigazione;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Controller della finestra principale: gestisce il menu e mostra, nell'area
 * centrale, la schermata scelta. Non contiene regole ne' SQL: apre solo le
 * schermate, ognuna delle quali ha il suo controller.
 *
 * ACCESSO E PERMESSI: il menu mostra solo le voci consentite all'utente che ha fatto l'accesso, e prima di
 * aprire qualunque schermata si controlla DI NUOVO il permesso (richiedi): nascondere una voce non basta.
 * Le regole su chi puo' fare cosa stanno in Autorizzazioni, non qui.
 */
public class MainController {

    // La cartella dove stanno i file FXML (in src/main/resources). Il "/"
    // iniziale significa "parti dalla radice delle risorse".
    private static final String CARTELLA_SCHERMATE = "/it/disagili/turnogiusto/";

    // L'area centrale della finestra: ha fx:id="contenuto" in main-view.fxml.
    @FXML private StackPane contenuto;
    @FXML private Label benvenuto;

    // La barra e i suoi menu: servono per togliere quelli vietati.
    @FXML private MenuBar barra;
    @FXML private Menu menuDipendenti;
    @FXML private Menu menuDisponibilita;
    @FXML private Menu menuFabbisogno;
    @FXML private Menu menuTurni;
    @FXML private Menu menuMieiTurni;
    @FXML private Menu menuLeMieDisponibilita;
    @FXML private Menu menuAccount;
    @FXML private MenuItem voceGenera;
    @FXML private MenuItem voceModifica;
    @FXML private MenuItem voceUtente;

    // Chi ha fatto l'accesso (null se nessuno: in quel caso non si puo' aprire niente).
    private Utente utente;

    // ======================================================================
    // INIZIALIZZAZIONE: parte da sola quando JavaFX ha caricato il FXML
    // ======================================================================
    @FXML
    private void initialize() {
        utente = SessioneUtente.corrente().orElse(null);

        // PASSO 1 - tolgo dalla barra i menu che questo utente non puo' usare.
        togliSeVietato(menuDipendenti, Funzione.GESTIONE_DIPENDENTI);
        togliSeVietato(menuDisponibilita, Funzione.DISPONIBILITA_PART_TIME);
        togliSeVietato(menuFabbisogno, Funzione.FABBISOGNO);
        togliSeVietato(menuMieiTurni, Funzione.I_MIEI_TURNI);
        togliSeVietato(menuLeMieDisponibilita, Funzione.LE_MIE_DISPONIBILITA);
        // Il menu Turni ha due voci con permessi propri: si tolgono singolarmente, e il menu se resta vuoto.
        if (!consentita(Funzione.GENERA_PIANO)) {
            menuTurni.getItems().remove(voceGenera);
        }
        if (!consentita(Funzione.MODIFICA_PIANO)) {
            menuTurni.getItems().remove(voceModifica);
        }
        if (menuTurni.getItems().isEmpty()) {
            barra.getMenus().remove(menuTurni);
        }

        // PASSO 2 - il menu Account dice chi e' collegato (la voce e' disattivata: e' solo un'etichetta).
        if (utente == null) {
            voceUtente.setText("Nessun utente collegato");
            benvenuto.setText("Accesso non effettuato: esci e fai l'accesso.");
        } else {
            voceUtente.setText(utente.isTitolare()
                    ? "Collegato come Titolare"
                    : "Collegato come " + utente.nome() + " (" + utente.tipo() + ")");
            benvenuto.setText("Benvenuto, " + utente.nome() + ". Scegli una voce dal menu.");
        }
    }

    private boolean consentita(Funzione funzione) {
        return Autorizzazioni.consentita(utente, funzione);
    }

    private void togliSeVietato(Menu menu, Funzione funzione) {
        if (!consentita(funzione)) {
            barra.getMenus().remove(menu);
        }
    }

    // Secondo controllo, fatto prima di aprire una schermata: se non c'e' il permesso lo dice e non apre niente.
    private boolean richiedi(Funzione funzione) {
        if (consentita(funzione)) {
            return true;
        }
        mostraMessaggio("Non hai i permessi per aprire questa sezione.");
        return false;
    }

    // ======================================================================
    // LE VOCI DEL MENU
    // Ogni metodo corrisponde a un onAction="#nomeMetodo" di main-view.fxml.
    // ======================================================================

    @FXML
    private void apriFabbisogno() {
        if (richiedi(Funzione.FABBISOGNO)) {
            caricaSchermata("fabbisogno-view.fxml");
        }
    }

    /** Apre la pianificazione nella stessa area centrale delle altre schermate. */
    @FXML
    private void apriGenerazioneTurni() {
        if (richiedi(Funzione.GENERA_PIANO)) {
            caricaSchermata("ui/generazione-turni-view.fxml");
        }
    }

    /** US-06B: modifica del piano settimanale gia' salvato. */
    @FXML
    private void apriModificaPiano() {
        if (richiedi(Funzione.MODIFICA_PIANO)) {
            caricaSchermata("ui/modifica-piano-view.fxml");
        }
    }

    // Segnaposto: finche' la schermata non esiste mostrano un avviso.
    // Chi sviluppa US-01/US-02 sostituisce il corpo con
    //     caricaSchermata("dipendenti-view.fxml");
    @FXML
    private void apriDipendenti() {
        if (richiedi(Funzione.GESTIONE_DIPENDENTI)) {
            caricaSchermata("ui/elenco-dipendenti.fxml");
        }
    }

    /** Il dipendente vede i propri turni della settimana (sola lettura). */
    @FXML
    private void apriMieiTurni() {
        if (richiedi(Funzione.I_MIEI_TURNI)) {
            caricaSchermata("ui/miei-turni-view.fxml");
        }
    }

    /** Il titolare vede le disponibilita' di tutti i part-time (e puo' modificarle). */
    @FXML
    private void apriDisponibilita() {
        if (!richiedi(Funzione.DISPONIBILITA_PART_TIME)) {
            return;
        }
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

    /**
     * Il dipendente part-time gestisce le PROPRIE fasce: la schermata di gestione si apre gia' su di lui e
     * la scelta di un altro dipendente viene tolta (menu e pulsante "Aggiorna dipendenti").
     */
    @FXML
    private void apriLeMieDisponibilita() {
        if (!richiedi(Funzione.LE_MIE_DISPONIBILITA)) {
            return;
        }
        try {
            DisponibilitaService servizio = new DisponibilitaService();
            // Cerco la MIA anagrafica tra i part-time con contratto valido oggi.
            Optional<Dipendente> io = servizio.trovaPartTime().stream()
                    .filter(d -> d.getId() == utente.dipendenteId())
                    .findFirst();
            if (io.isEmpty()) {
                mostraMessaggio("Non risulti un dipendente part-time con contratto valido oggi.");
                return;
            }

            FXMLLoader caricatore = new FXMLLoader(MainController.class.getResource(
                    CARTELLA_SCHERMATE + "ui/gestione-disponibilita-view.fxml"));
            Parent vista = caricatore.load();
            GestioneDisponibilitaController gestione = caricatore.getController();
            gestione.configura(servizio, () -> { });

            // Nascondo la riga "Dipendente part-time: [menu] [Aggiorna dipendenti]": si lavora solo su se stessi.
            Node menu = vista.lookup("#dipendenteCombo");
            if (menu != null && menu.getParent() != null) {
                menu.getParent().setVisible(false);
                menu.getParent().setManaged(false);
            }
            contenuto.getChildren().setAll(vista);
            gestione.impostaDipendente(io.get());
        } catch (IOException e) {
            mostraMessaggio("Impossibile aprire le tue disponibilità: " + e.getMessage());
        } catch (SQLException e) {
            mostraMessaggio("Impossibile leggere i dati. Controlla che il database sia acceso.");
        }
    }

    /** Esce dall'account e torna alla schermata di accesso. */
    @FXML
    private void onEsci() {
        SessioneUtente.esci();
        if (barra.getScene() != null && barra.getScene().getWindow() instanceof Stage finestra) {
            Navigazione.mostraLogin(finestra);
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

    private void mostraMessaggio(String testo) {
        contenuto.getChildren().setAll(new Label(testo));
    }
}
