package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.service.DipendenteService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Control;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.List;

/**
 * Gestisce il modulo dipendente-form.fxml.
 * JavaFX collega ogni campo al controllo che ha lo stesso fx:id nel FXML:
 * usiamo quindi i controlli della schermata, senza crearne altri con new.
 * Schermata sospesa dal team: il FXML è conservato con suffisso .commentato.
 * Il controller compila ma non è collegato alla finestra principale.
 * Per riattivarlo seguire docs/interfacce-sospese.md.
 */
public class DipendenteFormController {

    // Il controller raccoglie i dati; il service controlla le regole e gestisce la transazione.
    private final DipendenteService service;
    // Questi stati impediscono richieste simultanee e il reinvio di una registrazione riuscita.
    private boolean salvataggioInCorso;
    private boolean registrazioneCompletata;
    private Runnable azioneAnnulla;
    private Runnable azioneRegistrato;

    /** FXMLLoader usa questo costruttore quando apre normalmente il modulo. */
    public DipendenteFormController() {
        this(new DipendenteService());
    }

    // Nei test forniamo un service controllato, senza scrivere sul database.
    DipendenteFormController(DipendenteService service) {
        this.service = service;
    }

    // Campi della sezione Anagrafica.
    @FXML
    private TextField nomeField;

    @FXML
    private TextField cognomeField;

    @FXML
    private ComboBox<Ruolo> ruoloCombo;

    // Campi della sezione Contratto.
    @FXML
    private ComboBox<TipoContratto> tipoContrattoCombo;

    @FXML
    private TextField oreSettimanaliField;

    @FXML
    private TextField costoOrarioField;

    @FXML
    private DatePicker dataInizioPicker;

    @FXML
    private DatePicker dataFinePicker;

    // Messaggi e pulsanti del modulo.
    @FXML
    private Label messaggioLabel;

    @FXML
    private Button salvaButton;

    @FXML
    private Button annullaButton;

    /**
     * JavaFX richiama initialize dopo aver collegato i controlli ai campi.
     * values() restituisce tutte le costanti dell'enum; setAll le inserisce
     * nella ComboBox. Le etichette leggibili sono già definite da toString()
     * nelle enum. Non selezioniamo valori: sarà l'utente a scegliere.
     */
    @FXML
    private void initialize() {
        ruoloCombo.getItems().setAll(Ruolo.values());
        tipoContrattoCombo.getItems().setAll(TipoContratto.values());
        aggiornaControlli();
    }

    /**
     * La schermata che apre il modulo fornisce l'azione di uscita.
     * Va chiamato dopo il caricamento del FXML.
     */
    public void setOnAnnulla(Runnable azione){
        azioneAnnulla= azione;
        aggiornaControlli();
    }

    /** L'elenco fornisce l'azione da eseguire solo dopo un salvataggio riuscito. */
    public void setOnRegistrato(Runnable azione) {
        azioneRegistrato = azione;
    }

    /** La finestra usa questa verifica anche quando viene premuta la X. */
    public boolean puoChiudere() {
        return !salvataggioInCorso;
    }

    /**Esce dal modulo senza avviare alcun salvataggio. */
    @FXML
    private void onAnnulla(){
        if(!salvataggioInCorso && azioneAnnulla != null){
            azioneAnnulla.run();
        }
    }

    /** Legge i controlli sul thread JavaFX e avvia la registrazione in background. */
    @FXML
    private void onSalva() {
        // Protezione aggiuntiva rispetto al pulsante disabilitato nell'interfaccia.
        if (salvataggioInCorso || registrazioneCompletata) {
            return;
        }

        // Creiamo una fotografia dei dati prima di avviare il lavoro in background.
        // final impedisce di riassegnare questa variabile, non rende immutabile l'oggetto.
        final Dipendente dipendente;
        try {
            // Il primo 0 è dipendenteId: il nuovo dipendente non è ancora nel database.
            // Il costruttore per contratti nuovi imposta anche l'ID del contratto a zero.
            Contratto contratto = new Contratto(0, tipoContrattoCombo.getValue(),
                    leggiOre(), leggiCosto(), leggiData(dataInizioPicker, "inizio"));
            contratto.setDataFine(leggiData(dataFinePicker, "fine"));
            // strip rimuove gli spazi iniziali e finali; le scelte delle ComboBox
            // sono già enum, quindi non occorre convertirle dal testo visualizzato.
            dipendente = new Dipendente(nomeField.getText().strip(),
                    cognomeField.getText().strip(), ruoloCombo.getValue(), contratto);
        } catch (IllegalArgumentException errore) {
            // Un problema di conversione interrompe l'operazione prima di chiamare
            // il service. Non svuotiamo i controlli: l'utente può correggere il campo.
            mostraMessaggio(errore.getMessage());
            return;
        }

        // Blocchiamo i campi per non modificare i dati mentre vengono salvati.
        salvataggioInCorso = true;
        aggiornaControlli();
        mostraMessaggio("Salvataggio in corso…");

        // Nel thread di lavoro usiamo soltanto gli oggetti appena costruiti:
        // i controlli JavaFX devono essere letti e aggiornati sul thread grafico.
        // Task rappresenta un lavoro asincrono e notifica a JavaFX successo o errore.
        // Void indica che il lavoro non restituisce un risultato: call termina con null.
        Task<Void> salvataggio = new Task<>() {
            @Override
            protected Void call() throws SQLException {
                // Il service esegue validazione, INSERT e commit/rollback.
                // Una sua eccezione fa passare il Task allo stato FAILED.
                service.registra(dipendente);
                return null;
            }
        };

        // Questi gestori vengono richiamati da JavaFX sul thread grafico.
        salvataggio.setOnSucceeded(event -> {
            // Questo ramo è raggiunto soltanto dopo il ritorno normale del service:
            // il commit è già stato eseguito. Ora possiamo confermare il successo.
            salvataggioInCorso = false;
            registrazioneCompletata = true;
            aggiornaControlli();
            mostraMessaggio("Dipendente registrato correttamente.");
            // L'elenco si aggiorna dopo il commit, mentre la conferma resta leggibile.
            if (azioneRegistrato != null) {
                azioneRegistrato.run();
            }
        });
        salvataggio.setOnFailed(event -> {
            // Riabilitiamo il modulo e conserviamo ogni valore inserito.
            salvataggioInCorso = false;
            aggiornaControlli();
            Throwable errore = salvataggio.getException();
            if (errore instanceof IllegalArgumentException) {
                // Le validazioni del service contengono messaggi destinati all'utente.
                mostraMessaggio(errore.getMessage());
            } else if (errore instanceof SQLException) {
                // Non mostriamo direttamente dettagli SQL o nomi interni delle tabelle.
                // La connessione è una possibile causa: il messaggio non è una diagnosi.
                mostraMessaggio("Impossibile completare il salvataggio. "
                        + "Verifica la connessione al database. I dati compilati sono stati conservati.");
            } else {
                mostraMessaggio("Si è verificato un errore durante il salvataggio. "
                        + "I dati compilati sono stati conservati.");
            }
        });

        // start esegue call su un thread separato, lasciando libero il thread grafico.
        // Un thread daemon non tiene in vita il processo se l'applicazione termina.
        Thread thread = new Thread(salvataggio, "registrazione-dipendente");
        thread.setDaemon(true);
        thread.start();
    }

    /** Converte il testo in un intero; il service controllerà poi il limite 1..40. */
    private int leggiOre() {
        String testo = oreSettimanaliField.getText().strip();
        if (testo.isEmpty()) {
            throw new IllegalArgumentException("Le ore settimanali sono obbligatorie");
        }
        try {
            return Integer.parseInt(testo);
        } catch (NumberFormatException errore) {
            throw new IllegalArgumentException("Le ore settimanali devono essere un numero intero");
        }
    }

    /** Converte l'importo senza usare double, per mantenere la precisione decimale. */
    private BigDecimal leggiCosto() {
        String testo = costoOrarioField.getText().strip();
        if (testo.isEmpty()) {
            throw new IllegalArgumentException("Il costo orario è obbligatorio");
        }
        // Accettiamo 12,50 e 12.50, senza separatori delle migliaia o notazione esponenziale.
        // La regex richiede cifre, con eventuale segno e parte decimale.
        // Le doppie barre sono necessarie perché la regex è contenuta in una stringa Java.
        if (!testo.matches("[+-]?\\d+(?:[.,]\\d+)?")) {
            throw new IllegalArgumentException("Inserisci un costo orario numerico, per esempio 12,50");
        }
        // BigDecimal richiede il punto: normalizziamo la virgola senza cambiare il campo.
        // Positività, massimo e decimali ammessi vengono verificati dal service.
        return new BigDecimal(testo.replace(',', '.'));
    }

    /** Usa il formato del DatePicker; campo serve a distinguere inizio e fine negli errori. */
    private LocalDate leggiData(DatePicker picker, String campo) {
        // Leggiamo anche il testo digitato: il valore del DatePicker potrebbe non
        // essere ancora aggiornato se l'utente non ha confermato con Invio.
        String testo = picker.getEditor().getText().strip();
        if (testo.isEmpty()) {
            // La fine vuota è ammessa; l'inizio vuoto verrà rifiutato dal modello.
            return null;
        }
        try {
            return picker.getConverter().fromString(testo);
        } catch (DateTimeException | IllegalArgumentException errore) {
            throw new IllegalArgumentException("La data di " + campo
                    + " non è valida. Selezionala dal calendario.");
        }
    }

    /** Rende visibile l'area messaggi e le assegna spazio nel layout. */
    private void mostraMessaggio(String testo) {
        messaggioLabel.setText(testo);
        messaggioLabel.setVisible(true);
        // visible controlla la visualizzazione, managed la partecipazione al layout.
        messaggioLabel.setManaged(true);
    }

    private void aggiornaControlli() {
        // Dopo il successo il modulo resta leggibile, ma non può reinserire
        // lo stesso dipendente. In caso di errore torna modificabile senza svuotarsi.
        boolean disabilita = salvataggioInCorso || registrazioneCompletata;
        for (Control controllo : List.of(nomeField, cognomeField, ruoloCombo,
                tipoContrattoCombo, oreSettimanaliField, costoOrarioField,
                dataInizioPicker, dataFinePicker, salvaButton)) {
            controllo.setDisable(disabilita);
        }
        // Annulla è disponibile solo se non stiamo salvando e la schermata chiamante
        // ha già fornito l'azione di uscita con setOnAnnulla().
        annullaButton.setDisable(salvataggioInCorso || azioneAnnulla == null
        );
        // Dopo il commit non c'è più nulla da annullare: si può soltanto chiudere.
        annullaButton.setText(registrazioneCompletata ? "Chiudi" : "Annulla");
    }
}
