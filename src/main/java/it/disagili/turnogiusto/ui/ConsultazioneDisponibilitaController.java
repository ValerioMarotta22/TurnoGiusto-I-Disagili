// INTERFACCIA SOSPESA: codice conservato in attesa del CSS condiviso del team.
// Per ripristinarlo, rimuovere questi due avvisi e un prefisso // da ogni riga.
// package it.disagili.turnogiusto.ui;
//
// import it.disagili.turnogiusto.model.Dipendente;
// import it.disagili.turnogiusto.service.DisponibilitaService;
// import it.disagili.turnogiusto.service.RiepilogoDisponibilita;
// import javafx.beans.property.SimpleStringProperty;
// import javafx.concurrent.Task;
// import javafx.fxml.FXML;
// import javafx.scene.control.Label;
// import javafx.scene.control.TableColumn;
// import javafx.scene.control.TableView;
// import javafx.scene.layout.VBox;
//
// import java.util.ArrayList;
// import java.util.List;
//
// /** US-04: ogni part-time ha una riga, anche quando non ha inserito fasce. */
// public class ConsultazioneDisponibilitaController {
//
//     @FXML private VBox contenuto;
//     @FXML private TableView<RiepilogoDisponibilita> tabella;
//     @FXML private TableColumn<RiepilogoDisponibilita, String> dipendenteColonna;
//     @FXML private TableColumn<RiepilogoDisponibilita, String> ruoloColonna;
//     @FXML private TableColumn<RiepilogoDisponibilita, String> disponibilitaColonna;
//     @FXML private Label messaggio;
//
//     private DisponibilitaService servizio = new DisponibilitaService();
//     private List<Dipendente> elencoEsterno;
//     private boolean occupato;
//     private boolean ricaricaRichiesta;
//
//     @FXML
//     private void initialize() {
//         dipendenteColonna.setCellValueFactory(riga ->
//                 new SimpleStringProperty(riga.getValue().getDipendente().getNomeCompleto()));
//         ruoloColonna.setCellValueFactory(riga ->
//                 new SimpleStringProperty(riga.getValue().getDipendente().getRuolo().toString()));
//         disponibilitaColonna.setCellValueFactory(riga ->
//                 new SimpleStringProperty(riga.getValue().getTestoDisponibilita()));
//         tabella.setPlaceholder(new Label("Premi Aggiorna per caricare le disponibilità."));
//     }
//
//     public void configura(DisponibilitaService servizio) {
//         this.servizio = servizio;
//     }
//
//     /** Integrazione alternativa: riceve l'elenco completo restituito da US-01. */
//     public void mostraDipendenti(List<Dipendente> dipendenti) {
//         elencoEsterno = new ArrayList<>(dipendenti);
//         aggiorna();
//     }
//
//     @FXML
//     public void aggiorna() {
//         if (occupato) {
//             ricaricaRichiesta = true;
//             return;
//         }
//         List<Dipendente> daConsultare = elencoEsterno == null ? null : new ArrayList<>(elencoEsterno);
//         occupato = true;
//         contenuto.setDisable(true);
//         tabella.getItems().clear();
//         tabella.setPlaceholder(new Label("Caricamento…"));
//         messaggio.getStyleClass().remove("errore");
//         messaggio.setText("Caricamento delle disponibilità…");
//
//         Task<List<RiepilogoDisponibilita>> lavoro = new Task<>() {
//             @Override protected List<RiepilogoDisponibilita> call() throws Exception {
//                 return daConsultare == null ? servizio.consulta() : servizio.consulta(daConsultare);
//             }
//         };
//         lavoro.setOnSucceeded(evento -> {
//             tabella.getItems().setAll(lavoro.getValue());
//             tabella.setPlaceholder(new Label("Nessun dipendente part-time con contratto valido oggi."));
//             messaggio.setText("Consultazione aggiornata: " + lavoro.getValue().size() + " dipendenti part-time.");
//             termina();
//         });
//         lavoro.setOnFailed(evento -> {
//             tabella.setPlaceholder(new Label("Dati non disponibili: riprova con Aggiorna."));
//             messaggio.setText("Caricamento non riuscito. " + lavoro.getException().getMessage());
//             messaggio.getStyleClass().add("errore");
//             termina();
//         });
//         Thread thread = new Thread(lavoro, "disponibilita-consultazione");
//         thread.setDaemon(true);
//         thread.start();
//     }
//
//     private void termina() {
//         occupato = false;
//         contenuto.setDisable(false);
//         if (ricaricaRichiesta) {
//             ricaricaRichiesta = false;
//             aggiorna();
//         }
//     }
// }
//