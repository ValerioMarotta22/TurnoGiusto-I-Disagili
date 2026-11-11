package it.disagili.turnogiusto.controller;

import it.disagili.turnogiusto.model.Fabbisogno;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.TipoGiorno;
import it.disagili.turnogiusto.repository.FabbisognoRepository;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.SQLException;
import java.util.List;

public class FabbisognoController {
    //campi collegati al FXML = ogni campo corrisponde a un fx:id del fabbisogno view
    //all'interno di <> c'è il tipo di dato, ogni colonna mostra un tipo della tabella dedicata agli oggetti Fabbisogno
    @FXML private TableView<Fabbisogno> tabella;
    @FXML private TableColumn<Fabbisogno, Ruolo> colonnaRuolo;
    @FXML private TableColumn<Fabbisogno, TipoGiorno> colonnaTipoGiorno;
    @FXML private TableColumn<Fabbisogno, Servizio> colonnaServizio;
    @FXML private TableColumn<Fabbisogno, Integer> colonnaNumero;
    @FXML private ComboBox<Ruolo> ruoloCombo;
    @FXML private ComboBox<TipoGiorno> tipoGiornoCombo;
    @FXML private ComboBox<Servizio> servizioCombo;
    @FXML private TextField numeroField;
    @FXML private Label messaggio;

    //repository che parla con il database. solo un'istanza per tutta la schermata
    private final FabbisognoRepository repository = new FabbisognoRepository();

    //INITIALIZE: parte da sola appena javaFX ha caricato il FXML
    //qui si prepara tutto: JavaFX lo chiama in automatico a schermata creata e campi @FXML già collegati

    @FXML
    private void initialize() {
        //step 1: dire a ogni colonna quale dato mostrare
        colonnaRuolo.setCellValueFactory(new PropertyValueFactory<>("ruolo"));
        colonnaTipoGiorno.setCellValueFactory(new PropertyValueFactory<>("tipoGiorno"));
        colonnaServizio.setCellValueFactory(new PropertyValueFactory<>("servizio"));
        colonnaNumero.setCellValueFactory(new PropertyValueFactory<>("numeroMinimoDipendenti"));

        //step 2: riempire menu a tendina con tutti i valori dell'enum
        ruoloCombo.getItems().setAll(Ruolo.values());
        tipoGiornoCombo.getItems().setAll(TipoGiorno.values());
        servizioCombo.getItems().setAll(Servizio.values());

        //step 3: mostrare subito i dati già presenti nel database
        caricaTabella();
    }

    //CARICARE LA TABELLA DEL DATABASE

    //metodo di aiuto: lo uso qui e dopo ogni salvataggio
    private void caricaTabella() {
        try {
            //setAll sostituisce tutte le righe della tabella con quelle nuove
            tabella.getItems().setAll(repository.trovaTutti());
        } catch (SQLException e) {
            //trovaTutti() lascia salire l'errore. Qui nel controller lo gestisco mostrando un messaggio
            messaggio.setText("Database non raggiungibile. Avvia 'docker compose up -d'.");
        }
    }

    // ======================================================================
    // SALVA: parte quando si preme il bottone (onAction="#onSalva" nel FXML)
    // ======================================================================
    // Il metodo fa sei cose in ordine. Ogni volta che qualcosa non va, scrive
    // il motivo in "messaggio" e si ferma con "return": cosi' non si arriva
    // mai a salvare dati sbagliati.
    @FXML
    private void onSalva() {

        // PASSO 1 - leggo cosa ha scelto l'utente nei tre menu a tendina.
        // getValue() restituisce l'elemento scelto, oppure null se non ha
        // scelto niente (lo controlla poi valida()).
        Ruolo ruolo = ruoloCombo.getValue();
        TipoGiorno tipoGiorno = tipoGiornoCombo.getValue();
        Servizio servizio = servizioCombo.getValue();

        // PASSO 2 - il numero arriva come TESTO ("5"): va trasformato in un
        // numero intero. Se l'utente scrive "abc" o lascia il campo vuoto,
        // parseInt lancia una NumberFormatException: la catturo e mostro un
        // messaggio chiaro. trim() toglie gli spazi prima e dopo.
        int numero;
        try {
            numero = Integer.parseInt(numeroField.getText().trim());
        } catch (NumberFormatException e) {
            messaggio.setText("Il numero di dipendenti deve essere un numero intero");
            return; // esco dal metodo: non salvo niente
        }

        // PASSO 3 - con i valori letti costruisco l'oggetto Fabbisogno.
        // Uso il costruttore SENZA id: l'id lo assegna il database.
        Fabbisogno fabbisogno = new Fabbisogno(ruolo, tipoGiorno, servizio, numero);

        // PASSO 4 - le regole di validazione stanno nel model (valida()), non
        // qui: il controller le usa e basta. Se la lista degli errori non e'
        // vuota li mostro, uno per riga, e mi fermo.
        List<String> errori = fabbisogno.valida();
        if (!errori.isEmpty()) {
            messaggio.setText(String.join("\n", errori));
            return;
        }

        // PASSO 5 - salvo nel database tramite il repository. salva() lascia
        // salire la SQLException: qui la gestisco, dicendo all'utente che
        // qualcosa e' andato storto nel salvataggio.
        try {
            repository.salva(fabbisogno);
        } catch (SQLException e) {
            messaggio.setText("Impossibile salvare nel database. Controlla che sia acceso ('docker compose up -d').");
            return;
        }

        // PASSO 6 - aggiorno la schermata. L'ordine conta: scrivo prima il
        // messaggio "salvato" e poi ricarico la tabella, perche' se il
        // ricaricamento fallisce caricaTabella() sostituisce il messaggio con
        // il suo errore. Infine svuoto il campo del numero.
        messaggio.setText("Fabbisogno salvato");
        caricaTabella();
        numeroField.clear();
    }
}
