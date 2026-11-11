// INTERFACCIA SOSPESA: codice conservato in attesa del CSS condiviso del team.
// Per ripristinarlo, rimuovere questi due avvisi e un prefisso // da ogni riga.
// package it.disagili.turnogiusto;
//
// import it.disagili.turnogiusto.service.DisponibilitaService;
// import it.disagili.turnogiusto.ui.ConsultazioneDisponibilitaController;
// import it.disagili.turnogiusto.ui.GestioneDisponibilitaController;
// import javafx.application.Application;
// import javafx.fxml.FXMLLoader;
// import javafx.scene.Parent;
// import javafx.scene.Scene;
// import javafx.scene.control.Alert;
// import javafx.scene.control.Tab;
// import javafx.scene.control.TabPane;
// import javafx.stage.Stage;
//
// /** Avvio autonomo delle due US: non sostituisce la finestra principale di Valerio. */
// public class DisponibilitaApplication extends Application {
//
//     @Override
//     public void start(Stage stage) throws Exception {
//         FXMLLoader gestioneLoader = new FXMLLoader(getClass().getResource(
//                 "/it/disagili/turnogiusto/ui/gestione-disponibilita-view.fxml"));
//         FXMLLoader consultazioneLoader = new FXMLLoader(getClass().getResource(
//                 "/it/disagili/turnogiusto/ui/consultazione-disponibilita-view.fxml"));
//         Parent gestioneVista = gestioneLoader.load();
//         Parent consultazioneVista = consultazioneLoader.load();
//         GestioneDisponibilitaController gestione = gestioneLoader.getController();
//         ConsultazioneDisponibilitaController consultazione = consultazioneLoader.getController();
//
//         DisponibilitaService servizio = new DisponibilitaService();
//         consultazione.configura(servizio);
//         gestione.configura(servizio, consultazione::aggiorna);
//
//         Tab gestioneTab = new Tab("Gestione — dipendente", gestioneVista);
//         Tab consultazioneTab = new Tab("Consultazione — titolare", consultazioneVista);
//         gestioneTab.setClosable(false);
//         consultazioneTab.setClosable(false);
//         TabPane schede = new TabPane(gestioneTab, consultazioneTab);
//         consultazioneTab.setOnSelectionChanged(evento -> {
//             if (consultazioneTab.isSelected()) consultazione.aggiorna();
//         });
//
//         stage.setTitle("TurnoGiusto — Disponibilità part-time");
//         stage.setScene(new Scene(schede, 1080, 760));
//         stage.setMinWidth(960);
//         stage.setMinHeight(690);
//         stage.setOnCloseRequest(evento -> {
//             if (gestione.isOccupato()) {
//                 evento.consume();
//                 new Alert(Alert.AlertType.INFORMATION, "Attendi il completamento dell'operazione in corso.")
//                         .showAndWait();
//             } else if (gestione.haModifiche() && !gestione.confermaScarto()) {
//                 evento.consume();
//             }
//         });
//         stage.show();
//         gestione.caricaDipendenti();
//     }
// }
//