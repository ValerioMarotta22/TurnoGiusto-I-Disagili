
 package it.disagili.turnogiusto.ui;

 import it.disagili.turnogiusto.model.Dipendente;
 import it.disagili.turnogiusto.service.DisponibilitaService;
 import it.disagili.turnogiusto.service.RiepilogoDisponibilita;
 import javafx.beans.binding.Bindings;
 import javafx.beans.property.SimpleStringProperty;
 import javafx.concurrent.Task;
 import javafx.fxml.FXML;
 import javafx.scene.control.ContentDisplay;
 import javafx.scene.control.Label;
 import javafx.scene.control.ScrollPane;
 import javafx.scene.control.TableCell;
 import javafx.scene.control.TableColumn;
 import javafx.scene.control.TableView;
 import javafx.scene.layout.Region;
 import javafx.scene.layout.VBox;

 import java.util.ArrayList;
 import java.util.List;

 /** US-04: ogni part-time ha una riga, anche quando non ha inserito fasce. */
 public class ConsultazioneDisponibilitaController {

     private static final double ALTEZZA_MASSIMA_FASCE = 144;

     @FXML private VBox contenuto;
     @FXML private TableView<RiepilogoDisponibilita> tabella;
     @FXML private TableColumn<RiepilogoDisponibilita, String> dipendenteColonna;
     @FXML private TableColumn<RiepilogoDisponibilita, String> ruoloColonna;
     @FXML private TableColumn<RiepilogoDisponibilita, String> disponibilitaColonna;
     @FXML private Label messaggio;

     private DisponibilitaService servizio = new DisponibilitaService();
     private List<Dipendente> elencoEsterno;
     private boolean occupato;
     private boolean ricaricaRichiesta;

     @FXML
     private void initialize() {
         dipendenteColonna.setCellValueFactory(riga ->
                 new SimpleStringProperty(riga.getValue().getDipendente().getNomeCompleto()));
         ruoloColonna.setCellValueFactory(riga ->
                 new SimpleStringProperty(riga.getValue().getDipendente().getRuolo().toString()));
         disponibilitaColonna.setCellValueFactory(riga ->
                 new SimpleStringProperty(riga.getValue().getTestoDisponibilita()));
         disponibilitaColonna.setCellFactory(colonna -> creaCellaDisponibilita());
         tabella.setPlaceholder(new Label("Premi Aggiorna per caricare le disponibilità."));
     }

     /** Adatta la riga alle fasce e permette di scorrere gli elenchi più lunghi. */
     private TableCell<RiepilogoDisponibilita, String> creaCellaDisponibilita() {
         Label testo = new Label();
         testo.setWrapText(true);
         testo.setMaxWidth(Double.MAX_VALUE);
         testo.setMinHeight(Region.USE_PREF_SIZE);

         ScrollPane fasce = new ScrollPane(testo);
         fasce.getStyleClass().add("fasce-disponibilita");
         fasce.setFitToWidth(true);
         fasce.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
         fasce.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
         // Calcoliamo l'altezza prima che la tabella disponga la riga, anche quando il testo va a capo.
         fasce.prefViewportHeightProperty().bind(Bindings.createDoubleBinding(() -> {
             double larghezzaTesto = Math.max(1, disponibilitaColonna.getWidth() - 36);
             return Math.clamp(testo.prefHeight(larghezzaTesto), 24, ALTEZZA_MASSIMA_FASCE);
         }, disponibilitaColonna.widthProperty(), testo.textProperty(), testo.fontProperty(), testo.skinProperty()));

         return new TableCell<>() {
             @Override
             protected void updateItem(String valore, boolean vuota) {
                 super.updateItem(valore, vuota);
                 setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                 testo.setText(vuota ? null : valore);
                 // La tabella riusa le celle durante lo scorrimento.
                 fasce.setVvalue(fasce.getVmin());
                 setGraphic(vuota ? null : fasce);
             }
         };
     }

     public void configura(DisponibilitaService servizio) {
         this.servizio = servizio;
     }

     /** Integrazione alternativa: riceve l'elenco completo restituito da US-01. */
     public void mostraDipendenti(List<Dipendente> dipendenti) {
         elencoEsterno = new ArrayList<>(dipendenti);
         aggiorna();
     }

     @FXML
     public void aggiorna() {
         if (occupato) {
             ricaricaRichiesta = true;
             return;
         }
         List<Dipendente> daConsultare = elencoEsterno == null ? null : new ArrayList<>(elencoEsterno);
         occupato = true;
         contenuto.setDisable(true);
         tabella.getItems().clear();
         tabella.setPlaceholder(new Label("Caricamento…"));
         messaggio.getStyleClass().remove("errore");
         messaggio.setText("Caricamento delle disponibilità…");

         Task<List<RiepilogoDisponibilita>> lavoro = new Task<>() {
             @Override protected List<RiepilogoDisponibilita> call() throws Exception {
                 return daConsultare == null ? servizio.consulta() : servizio.consulta(daConsultare);
             }
         };
         lavoro.setOnSucceeded(evento -> {
             tabella.getItems().setAll(lavoro.getValue());
             tabella.setPlaceholder(new Label("Nessun dipendente part-time con contratto valido oggi."));
             messaggio.setText("Consultazione aggiornata: " + lavoro.getValue().size() + " dipendenti part-time.");
             termina();
         });
         lavoro.setOnFailed(evento -> {
             tabella.setPlaceholder(new Label("Dati non disponibili: riprova con Aggiorna."));
             messaggio.setText("Caricamento non riuscito. " + lavoro.getException().getMessage());
             messaggio.getStyleClass().add("errore");
             termina();
         });
         Thread thread = new Thread(lavoro, "disponibilita-consultazione");
         thread.setDaemon(true);
         thread.start();
     }

     private void termina() {
         occupato = false;
         contenuto.setDisable(false);
         if (ricaricaRichiesta) {
             ricaricaRichiesta = false;
             aggiorna();
         }
     }
 }
