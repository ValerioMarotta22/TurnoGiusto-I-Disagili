package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.repository.DipendenteRepository;
import it.disagili.turnogiusto.service.DipendenteService;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.Modality;
import java.io.IOException;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import java.sql.SQLException;
import java.util.List;

/**
 * Gestisce la tabella dell'elenco e il suo aggiornamento dal database.
 * Schermata sospesa dal team: FXML e test grafici sono conservati per il ripristino
 * descritto in docs/interfacce-sospese.md. Il pulsante di modifica contratto ha
 * solo la gestione della selezione: l'apertura del modulo US02 resta da realizzare.
 */
public class ElencoDipendentiController {

    private final DipendenteRepository repository;
    private final DipendenteService service;
    private Stage moduloAperto;
    // Impedisce che più clic avviino letture simultanee dello stesso elenco.
    private boolean caricamentoInCorso;

    /** Costruttore usato normalmente da FXMLLoader. */
    public ElencoDipendentiController() {
        this(new DipendenteRepository());
    }

    // I test possono fornire dati controllati senza collegarsi a MySQL.
    ElencoDipendentiController(DipendenteRepository repository) {
        this(repository, new DipendenteService());
    }

    // Permette di provare il percorso elenco-modulo con un service controllato.
    ElencoDipendentiController(DipendenteRepository repository, DipendenteService service) {
        this.repository = repository;
        this.service = service;
    }

    // Ogni riga contiene un Dipendente; ogni colonna qui ne mostra un dato come testo.
    // I nomi dei campi devono coincidere con gli fx:id del FXML.
    @FXML
    private TableView<Dipendente> dipendentiTable;

    @FXML
    private TableColumn<Dipendente, String> nomeColumn;

    @FXML
    private TableColumn<Dipendente, String> cognomeColumn;

    @FXML
    private TableColumn<Dipendente, String> ruoloColumn;

    @FXML
    private TableColumn<Dipendente, String> tipoContrattoColumn;

    @FXML
    private TableColumn<Dipendente, String> oreColumn;

    @FXML
    private TableColumn<Dipendente, String> costoColumn;

    @FXML
    private Button nuovoDipendenteButton;

    @FXML
    private Button aggiornaButton;

    @FXML
    private Button modificaContrattoButton;

    @FXML
    private Label messaggioLabel;

    /**
     * Configura come leggere i valori degli oggetti Dipendente.
     * Dopo la configurazione avvia il primo caricamento dal database.
     */
    @FXML
    private void initialize() {
        // cella.getValue() è il dipendente della riga. ReadOnlyStringWrapper
        // adatta il testo al valore osservabile richiesto da JavaFX, senza cambiare il model.
        nomeColumn.setCellValueFactory(cella ->
                new ReadOnlyStringWrapper(cella.getValue().getNome()));

        cognomeColumn.setCellValueFactory(cella ->
                new ReadOnlyStringWrapper(cella.getValue().getCognome()));

        // toString() delle enum restituisce l'etichetta leggibile, per esempio Aiuto cuoco.
        ruoloColumn.setCellValueFactory(cella ->
                new ReadOnlyStringWrapper(
                        cella.getValue().getRuolo().toString()
                ));

        // trovaTutti include anche dipendenti senza contratto valido oggi.
        // Controlliamo null prima di leggere i dati contrattuali.
        tipoContrattoColumn.setCellValueFactory(cella -> {
            Dipendente dipendente = cella.getValue();

            String testo = dipendente.getContratto() == null
                    ? "Nessun contratto attuale"
                    : dipendente.getContratto().getTipoContratto().toString();

            return new ReadOnlyStringWrapper(testo);
        });

        oreColumn.setCellValueFactory(cella -> {
            Dipendente dipendente = cella.getValue();

            String testo = dipendente.getContratto() == null
                    ? "—"
                    : Integer.toString(
                            dipendente.getContratto().getOreSettimanali()
                    );

            return new ReadOnlyStringWrapper(testo);
        });

        // Il costo rimane BigDecimal nel modello; solo il testo mostrato usa la virgola.
        costoColumn.setCellValueFactory(cella -> {
            Dipendente dipendente = cella.getValue();

            String testo = dipendente.getContratto() == null
                    ? "—"
                    : dipendente.getContratto().getCostoOrario()
                            .toPlainString().replace('.', ',');

            return new ReadOnlyStringWrapper(testo);
        });

        // Registriamo il listener prima del caricamento: qualunque cambio di selezione
        // (anche causato dall'aggiornamento delle righe) rivaluta il pulsante Modifica.
        dipendentiTable.getSelectionModel()
                .selectedItemProperty()
                .addListener((proprieta, precedente, selezionato) ->
                        aggiornaPulsanteModifica());
        caricaDipendenti();
    }
    /**
     * La modifica richiede una selezione e un contratto valido oggi.
     * Il service rileggerà comunque i dati al momento del salvataggio.
     */
    private void aggiornaPulsanteModifica() {
        Dipendente selezionato =
                dipendentiTable.getSelectionModel().getSelectedItem();

        boolean disabilita = caricamentoInCorso
                || selezionato == null
                || selezionato.getContratto() == null
                || !selezionato.getContratto().isInVigore();

        modificaContrattoButton.setDisable(disabilita);
    }




    /** Usato all'apertura della schermata e dal pulsante Aggiorna elenco. */
    @FXML
    private void caricaDipendenti() {
        if (caricamentoInCorso) {
            return;
        }

        impostaCaricamento(true);
        mostraMessaggio("Caricamento dipendenti…");

        // call gira sul thread di lavoro: legge i dati ma non tocca i controlli grafici.
        // Il tipo List<Dipendente> indica il risultato che Task renderà disponibile.
        Task<List<Dipendente>> caricamento = new Task<>() {
            @Override
            protected List<Dipendente> call() throws SQLException {
                return repository.trovaTutti();
            }
        };

        // I gestori del Task vengono eseguiti sul thread JavaFX: qui è sicuro
        // modificare tabella, messaggi e pulsanti.
        caricamento.setOnSucceeded(event -> {
            // setAll sostituisce le righe: aggiorna senza accumulare duplicati.
            dipendentiTable.getItems().setAll(caricamento.getValue());
            // Se l'utente ha ordinato una colonna, riapplichiamo quell'ordinamento.
            dipendentiTable.sort();
            messaggioLabel.setVisible(false);
            messaggioLabel.setManaged(false);
            impostaCaricamento(false);
        });

        caricamento.setOnFailed(event -> {
            // Non svuotiamo la tabella: una lettura fallita non significa elenco vuoto.
            // L'errore potrebbe dipendere dalla connessione o da contratti sovrapposti.
            mostraMessaggio("Impossibile aggiornare l'elenco. "
                    + "Verifica la connessione al database e i dati contrattuali. "
                    + "L'eventuale elenco precedente è stato conservato.");
            impostaCaricamento(false);
        });

        // La query non blocca il thread grafico. Il daemon non tiene in vita
        // il processo dopo la chiusura dell'applicazione.
        Thread thread = new Thread(caricamento, "caricamento-dipendenti");
        thread.setDaemon(true);
        thread.start();
    }

    /** Apre il modulo dedicato senza modificare la finestra principale. */
    @FXML
    private void onNuovoDipendente() {
        if (caricamentoInCorso) {
            return;
        }
        if (moduloAperto != null) {
            moduloAperto.requestFocus();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("dipendente-form.fxml"));
            loader.setControllerFactory(tipo -> new DipendenteFormController(service));
            Parent modulo = loader.load();
            DipendenteFormController controller = loader.getController();

            Stage finestra = new Stage();
            Window proprietario = nuovoDipendenteButton.getScene() == null
                    ? null : nuovoDipendenteButton.getScene().getWindow();
            // La modalità impedisce di lavorare sull'elenco mentre il modulo è aperto.
            if (proprietario != null) {
                finestra.initOwner(proprietario);
                finestra.initModality(Modality.WINDOW_MODAL);
            } else {
                finestra.initModality(Modality.APPLICATION_MODAL);
            }
            finestra.setTitle("Nuovo dipendente");
            finestra.setScene(new Scene(modulo));
            controller.setOnAnnulla(finestra::close);
            controller.setOnRegistrato(this::caricaDipendenti);
            // Anche la X deve rispettare la transazione in corso: nascondere il
            // modulo non annullerebbe la scrittura già avviata nel thread di lavoro.
            finestra.setOnCloseRequest(event -> {
                if (!controller.puoChiudere()) {
                    event.consume();
                }
            });
            finestra.setOnHidden(event -> moduloAperto = null);
            moduloAperto = finestra;
            finestra.show();
        } catch (IOException errore) {
            mostraMessaggio("Impossibile aprire il modulo di registrazione.");
        }
    }

    /** Blocca temporaneamente le azioni mentre è in corso una lettura. */
    private void impostaCaricamento(boolean inCorso) {
        caricamentoInCorso = inCorso;
        aggiornaButton.setDisable(inCorso);
        nuovoDipendenteButton.setDisable(inCorso);

        aggiornaPulsanteModifica();
    }

    /** visible mostra il testo; managed gli riserva spazio nel layout. */
    private void mostraMessaggio(String testo) {
        messaggioLabel.setText(testo);
        messaggioLabel.setVisible(true);
        messaggioLabel.setManaged(true);
    }
}
