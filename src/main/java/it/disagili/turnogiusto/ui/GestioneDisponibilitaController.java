
 package it.disagili.turnogiusto.ui;

 import it.disagili.turnogiusto.model.Dipendente;
 import it.disagili.turnogiusto.model.Disponibilita;
 import it.disagili.turnogiusto.service.DisponibilitaService;
 import it.disagili.turnogiusto.service.ValidazioneDisponibilita;
 import javafx.collections.FXCollections;
 import javafx.collections.ObservableList;
 import javafx.concurrent.Task;
 import javafx.fxml.FXML;
 import javafx.scene.control.Alert;
 import javafx.scene.control.ButtonType;
 import javafx.scene.control.ComboBox;
 import javafx.scene.control.Label;
 import javafx.scene.control.ListView;
 import javafx.scene.control.TextField;
 import javafx.scene.layout.VBox;
 import javafx.util.StringConverter;

 import java.time.DayOfWeek;
 import java.time.format.TextStyle;
 import java.util.ArrayList;
 import java.util.List;
 import java.util.Locale;

 /** US-03: modifica in memoria, poi salvataggio esplicito dell'intero elenco. */
 public class GestioneDisponibilitaController {

     // 1. Controlli collegati agli fx:id della schermata FXML.
     @FXML private VBox contenuto;
     @FXML private VBox editor;
     @FXML private ComboBox<Dipendente> dipendenteCombo;
     @FXML private ComboBox<DayOfWeek> giornoCombo;
     @FXML private TextField oraInizioField;
     @FXML private TextField oraFineField;
     @FXML private ListView<Disponibilita> elencoFinestre;
     @FXML private Label messaggio;

     // 2. Stato della schermata. ObservableList aggiorna la lista visibile quando cambia.
     // modificato riguarda l'elenco; bozzaModificata riguarda i campi non ancora applicati.
     private final ObservableList<Disponibilita> finestre = FXCollections.observableArrayList();
     private DisponibilitaService servizio = new DisponibilitaService();
     private Dipendente dipendenteCorrente;
     private boolean modificato;
     private boolean bozzaModificata;
     private boolean aggiornamentoCampi;
     private boolean aggiornamentoSelezione;
     private boolean occupato;
     private Runnable dopoSalvataggio = () -> { };

     // 3. Preparazione grafica: non interroga il database durante il caricamento FXML.
     @FXML
     private void initialize() {
         elencoFinestre.setItems(finestre);
         elencoFinestre.setPlaceholder(new Label("Nessuna fascia nell'elenco."));
         giornoCombo.getItems().setAll(DayOfWeek.values());
         giornoCombo.setConverter(new StringConverter<>() {
             @Override public String toString(DayOfWeek giorno) {
                 return giorno == null ? "" : giorno.getDisplayName(TextStyle.FULL, Locale.ITALIAN);
             }
             @Override public DayOfWeek fromString(String testo) {
                 throw new UnsupportedOperationException("Il giorno si sceglie dall'elenco.");
             }
         });
         oraInizioField.textProperty().addListener((campo, prima, dopo) -> segnaBozza());
         oraFineField.textProperty().addListener((campo, prima, dopo) -> segnaBozza());
         giornoCombo.valueProperty().addListener((campo, prima, dopo) -> segnaBozza());

         // Un cambio di dipendente non deve cancellare modifiche senza conferma.
         dipendenteCombo.valueProperty().addListener((campo, precedente, scelto) -> {
             if (aggiornamentoSelezione) return;
             if (haModifiche() && !confermaScarto()) {
                 aggiornamentoSelezione = true;
                 dipendenteCombo.setValue(precedente);
                 aggiornamentoSelezione = false;
                 return;
             }
             caricaDipendente(scelto);
         });
         elencoFinestre.getSelectionModel().selectedItemProperty().addListener((campo, prima, dopo) -> {
             if (aggiornamentoSelezione) return;
             if (bozzaModificata && !confermaScarto()) {
                 aggiornamentoSelezione = true;
                 elencoFinestre.getSelectionModel().select(prima);
                 aggiornamentoSelezione = false;
                 return;
             }
             mostraCampi(dopo);
         });
         editor.setDisable(true);
         mostraCampi(null);
     }

     /** Chiamato dalla finestra principale dopo FXMLLoader.load(). */
     public void configura(DisponibilitaService servizio, Runnable dopoSalvataggio) {
         this.servizio = servizio;
         this.dopoSalvataggio = dopoSalvataggio;
     }

     public boolean haModifiche() { return modificato || bozzaModificata; }
     public boolean isOccupato() { return occupato; }

     public boolean confermaScarto() {
         Alert avviso = new Alert(Alert.AlertType.CONFIRMATION,
                 "Scartare le modifiche non salvate?", ButtonType.YES, ButtonType.NO);
         avviso.setHeaderText("Modifiche in sospeso");
         return avviso.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
     }

     @FXML
     public void caricaDipendenti() {
         if (occupato || (haModifiche() && !confermaScarto())) return;
         Task<List<Dipendente>> lavoro = new Task<>() {
             @Override protected List<Dipendente> call() throws Exception {
                 return servizio.trovaPartTime();
             }
         };
         lavoro.setOnSucceeded(evento -> {
             termina();
             aggiornamentoSelezione = true;
             dipendenteCombo.getItems().setAll(lavoro.getValue());
             dipendenteCombo.setValue(null);
             aggiornamentoSelezione = false;
             caricaDipendente(null);
             mostraMessaggio(lavoro.getValue().isEmpty()
                     ? "Nessun dipendente con contratto part-time valido oggi."
                     : "Seleziona il dipendente di cui gestire le disponibilità.", false);
         });
         avvia(lavoro, "Caricamento dipendenti…");
     }

     /** Permette a Valerio di aprire la schermata su un dipendente scelto altrove. */
     public void impostaDipendente(Dipendente dipendente) {
         if (occupato) throw new IllegalStateException("Caricamento già in corso.");
         if (haModifiche() && !confermaScarto()) return;
         aggiornamentoSelezione = true;
         dipendenteCombo.getItems().setAll(dipendente);
         dipendenteCombo.setValue(dipendente);
         aggiornamentoSelezione = false;
         caricaDipendente(dipendente);
     }

     private void caricaDipendente(Dipendente dipendente) {
         dipendenteCorrente = dipendente;
         svuotaElenco();
         editor.setDisable(true);
         if (dipendente == null) return;
         if (dipendente.getId() <= 0 || !dipendente.isPartTime()
                 || !dipendente.getContratto().isInVigore()) {
             mostraMessaggio("Seleziona un dipendente con contratto part-time valido oggi.", true);
             return;
         }
         Task<List<Disponibilita>> lavoro = new Task<>() {
             @Override protected List<Disponibilita> call() throws Exception {
                 return servizio.caricaFinestre(dipendente.getId());
             }
         };
         lavoro.setOnSucceeded(evento -> {
             finestre.setAll(lavoro.getValue());
             editor.setDisable(false);
             termina();
             mostraMessaggio(finestre.isEmpty() ? "Senza disponibilità inserite. Puoi aggiungere la prima fascia."
                     : "Disponibilità caricate. Le modifiche saranno registrate premendo Salva.", false);
         });
         avvia(lavoro, "Caricamento disponibilità…");
     }

     // Le operazioni seguenti cambiano solo l'elenco in memoria, non il database.
     @FXML private void onAggiungi() { applicaFascia(false); }
     @FXML private void onModifica() { applicaFascia(true); }

     private void applicaFascia(boolean modifica) {
         try {
             int indice = elencoFinestre.getSelectionModel().getSelectedIndex();
             if (modifica && indice < 0) {
                 throw new IllegalArgumentException("Seleziona la fascia da modificare.");
             }
             Disponibilita nuova = new Disponibilita(dipendenteCorrente.getId(), giornoCombo.getValue(),
                     ValidazioneDisponibilita.leggiOra(oraInizioField.getText()),
                     ValidazioneDisponibilita.leggiOra(oraFineField.getText()));
             List<Disponibilita> proposta = new ArrayList<>(finestre);
             if (modifica) proposta.set(indice, nuova);
             else proposta.add(nuova);
             ValidazioneDisponibilita.verifica(dipendenteCorrente.getId(), proposta);
             aggiornamentoSelezione = true;
             finestre.setAll(proposta);
             elencoFinestre.getSelectionModel().clearSelection();
             aggiornamentoSelezione = false;
             mostraCampi(null);
             modificato = true;
             mostraMessaggio("Elenco modificato: premi Salva per registrarlo.", false);
         } catch (IllegalArgumentException errore) {
             mostraMessaggio(errore.getMessage(), true);
         }
     }

     @FXML
     private void onRimuovi() {
         int indice = elencoFinestre.getSelectionModel().getSelectedIndex();
         if (indice < 0) {
             mostraMessaggio("Seleziona la fascia da rimuovere.", true);
             return;
         }
         if (bozzaModificata && !confermaScarto()) return;
         aggiornamentoSelezione = true;
         finestre.remove(indice);
         elencoFinestre.getSelectionModel().clearSelection();
         aggiornamentoSelezione = false;
         mostraCampi(null);
         modificato = true;
         mostraMessaggio("Fascia rimossa dall'elenco. Premi Salva per confermare.", false);
     }

     @FXML
     private void onNuova() {
         if (bozzaModificata && !confermaScarto()) return;
         aggiornamentoSelezione = true;
         elencoFinestre.getSelectionModel().clearSelection();
         aggiornamentoSelezione = false;
         mostraCampi(null);
     }

     @FXML
     private void onRicarica() {
         if (!haModifiche() || confermaScarto()) caricaDipendente(dipendenteCorrente);
     }

     // 4. Salvataggio: solo il successo della transazione azzera lo stato di modifica.
     @FXML
     private void onSalva() {
         if (bozzaModificata) {
             mostraMessaggio("Premi Aggiungi o Aggiorna fascia prima di salvare l'elenco.", true);
             return;
         }
         int id = dipendenteCorrente.getId();
         List<Disponibilita> daSalvare = new ArrayList<>(finestre);
         Task<Void> lavoro = new Task<>() {
             @Override protected Void call() throws Exception {
                 servizio.salva(id, daSalvare);
                 return null;
             }
         };
         lavoro.setOnSucceeded(evento -> {
             modificato = false;
             termina();
             mostraMessaggio("Disponibilità salvate. Il titolare può consultare il nuovo elenco.", false);
             dopoSalvataggio.run();
         });
         avvia(lavoro, "Salvataggio in corso…");
     }

     private void segnaBozza() {
         if (!aggiornamentoCampi) bozzaModificata = true;
     }

     private void mostraCampi(Disponibilita finestra) {
         aggiornamentoCampi = true;
         giornoCombo.setValue(finestra == null ? DayOfWeek.MONDAY : finestra.getGiornoSettimana());
         oraInizioField.setText(finestra == null ? "" : finestra.getOraInizio().toString());
         oraFineField.setText(finestra == null ? "" : finestra.getOraFine().toString());
         aggiornamentoCampi = false;
         bozzaModificata = false;
     }

     private void svuotaElenco() {
         aggiornamentoSelezione = true;
         finestre.clear();
         elencoFinestre.getSelectionModel().clearSelection();
         aggiornamentoSelezione = false;
         mostraCampi(null);
         modificato = false;
     }

     // 5. Task esegue JDBC fuori dal thread grafico; gli eventi aggiornano la UI.
     private void avvia(Task<?> lavoro, String testo) {
         occupato = true;
         contenuto.setDisable(true);
         mostraMessaggio(testo, false);
         lavoro.setOnFailed(evento -> {
             termina();
             mostraMessaggio("Operazione non completata. " + lavoro.getException().getMessage(), true);
         });
         // JDBC lavora su un thread separato: la finestra resta reattiva.
         Thread thread = new Thread(lavoro, "disponibilita-gestione");
         thread.setDaemon(true);
         thread.start();
     }

     private void termina() {
         occupato = false;
         contenuto.setDisable(false);
     }

     private void mostraMessaggio(String testo, boolean errore) {
         messaggio.setText(testo);
         messaggio.getStyleClass().remove("errore");
         if (errore) messaggio.getStyleClass().add("errore");
     }
 }
