package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.service.ContrattoService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Control;
import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.DateTimeException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

/**
 * Gestisce il modulo di modifica contrattuale US02.
 * Raccoglie i dati della schermata e affida validazione e transazione
 * al ContrattoService. Le operazioni sul database avvengono in background.
 */
public class ContrattoFormController {

    private final ContrattoService service;
    // Il primo stato impedisce operazioni durante la transazione;
    // il secondo impedisce di salvare nuovamente dopo un successo.
    private boolean salvataggioInCorso;
    private boolean salvataggioCompletato;
    private Runnable azioneSalvato;

    /** FXMLLoader usa il service reale quando apre normalmente il modulo. */
    public ContrattoFormController() {
        this(new ContrattoService());
    }

    /** Nei test possiamo fornire un service controllato senza scrivere nel DB. */
    ContrattoFormController(ContrattoService service) {
        this.service = Objects.requireNonNull(service);
    }

    // Conserviamo gli identificativi necessari al salvataggio.
    // Non modifichiamo direttamente il Dipendente ricevuto dall'elenco.
    private int dipendenteId;
    private int contrattoAttesoId;

    // La decorrenza mostrata nel modulo sarà usata nella proposta di modifica.
    private LocalDate dataDecorrenza;

    // Runnable conserva un'azione, non la esegue quando viene assegnata.
    // La finestra che aprirà il modulo ci passerà qui la propria azione di chiusura.
    private Runnable azioneAnnulla;

    // Mostra il dipendente selezionato: l'anagrafica non viene modificata.
    @FXML
    private Label dipendenteLabel;

    // Contiene le scelte FULL_TIME e PART_TIME, mostrate con le etichette dell'enum.
    @FXML
    private ComboBox<TipoContratto> tipoContrattoCombo;

    // I TextField contengono testo: prima del salvataggio lo convertiremo
    // rispettivamente in un numero intero e in un importo BigDecimal.
    @FXML
    private TextField oreSettimanaliField;

    @FXML
    private TextField costoOrarioField;

    // Una Label mostra la decorrenza senza consentire di modificarla:
    // il service accetta il cambio soltanto con decorrenza dalla data odierna.
    @FXML
    private Label decorrenzaLabel;

    // Il calendario permette di scegliere la scadenza; lasciarlo vuoto
    // significherà che il nuovo contratto non ha una data di fine.
    @FXML
    private DatePicker dataFinePicker;

    // Mostrerà errori nei dati inseriti o l'esito del salvataggio.
    @FXML
    private Label messaggioLabel;

    // I pulsanti sono collegati ai gestori onSalva e onAnnulla tramite il FXML.
    @FXML
    private Button salvaButton;

    @FXML
    private Button annullaButton;

    /**
     * JavaFX richiama questo metodo dopo aver collegato i controlli del FXML
     * ai campi con lo stesso fx:id. Non creiamo qui nuovi controlli grafici.
     */
    @FXML
    private void initialize() {
        // values() restituisce le costanti dell'enum; setAll popola la ComboBox.
        // La selezione del tipo attuale sarà impostata quando riceveremo il dipendente.
        tipoContrattoCombo.getItems().setAll(TipoContratto.values());

        // Salva richiede un dipendente; Annulla richiede l'azione di chiusura.
        aggiornaControlli();
    }

    /**
     * Riceve il dipendente selezionato e precompila il modulo.
     * Deve essere chiamato dopo loader.load(), quando i controlli
     * FXML sono già stati collegati ai campi del controller.
     */
    public void impostaDipendente(Dipendente dipendente) {
        // Un modulo già inviato non deve essere riconfigurato per un altro dipendente.
        if (salvataggioInCorso || salvataggioCompletato) {
            throw new IllegalStateException("Il modulo ha già avviato il salvataggio");
        }
        if (dipendente == null || dipendente.getId() <= 0) {
            throw new IllegalArgumentException(
                    "Seleziona un dipendente registrato"
            );
        }

        Contratto contratto = dipendente.getContratto();
        LocalDate oggi = LocalDate.now();

        // Per US02 occorre un contratto registrato e valido oggi.
        // Il service lo rileggerà comunque dal database al salvataggio.
        if (contratto == null
                || contratto.getId() <= 0
                || !contratto.isValidoIl(oggi)) {
            throw new IllegalArgumentException(
                    "Il dipendente non ha un contratto valido oggi"
            );
        }

        dipendenteId = dipendente.getId();
        contrattoAttesoId = contratto.getId();
        dataDecorrenza = oggi;

        // Copiamo i valori nei controlli, senza modificare gli oggetti originali.
        dipendenteLabel.setText(dipendente.getNomeCompleto());
        tipoContrattoCombo.setValue(contratto.getTipoContratto());

        oreSettimanaliField.setText(
                Integer.toString(contratto.getOreSettimanali())
        );

        // Manteniamo la precisione dell'importo e mostriamo la virgola.
        costoOrarioField.setText(
                contratto.getCostoOrario().toPlainString().replace('.', ',')
        );

        decorrenzaLabel.setText(
                dataDecorrenza.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        );

        // null lascia il calendario vuoto: contratto senza scadenza.
        dataFinePicker.setValue(contratto.getDataFine());
        aggiornaControlli();
    }

    /**
     * Configura l'azione di uscita, dopo il caricamento del FXML.
     * Il chiamante passerà la chiusura della finestra: il controller si limita
     * a conservarla, senza chiudere nulla durante la configurazione.
     */
    public void setOnAnnulla(Runnable azione) {
        azioneAnnulla = azione;
        aggiornaControlli();
    }

    /** L'elenco potrà fornire qui la propria azione di aggiornamento dopo il commit. */
    public void setOnSalvato(Runnable azione) {
        azioneSalvato = azione;
    }

    /** La finestra dovrà usare questa verifica anche per gestire la propria X. */
    public boolean puoChiudere() {
        return !salvataggioInCorso;
    }

    /**
     * Gestore del clic su Annulla, collegato tramite onAction nel FXML.
     * Abbandona il modulo senza validare i campi e senza chiamare il service.
     * Gli oggetti originali non sono stati modificati dalla compilazione.
     */
    @FXML
    private void onAnnulla() {
        if (puoChiudere() && azioneAnnulla != null) {
            // run() esegue ora l'azione che setOnAnnulla aveva soltanto memorizzato.
            azioneAnnulla.run();
        }
    }

    /** Legge i controlli sul thread grafico e avvia l'aggiornamento contrattuale. */
    @FXML
    private void onSalva() {
        if (salvataggioInCorso || salvataggioCompletato) {
            return;
        }
        final Contratto nuoviDati;
        try {
            if (dipendenteId <= 0 || contrattoAttesoId <= 0) {
                throw new IllegalArgumentException("Seleziona un dipendente con un contratto valido");
            }
            // Il modulo potrebbe essere rimasto aperto oltre mezzanotte:
            // la decorrenza effettiva è oggi al momento dell'invio, anche nella Label.
            dataDecorrenza = LocalDate.now();
            decorrenzaLabel.setText(dataDecorrenza.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            // Costruiamo un oggetto distinto: il contratto dell'elenco rimane intatto.
            // L'ID zero indica una proposta; l'ID atteso viene passato separatamente.
            nuoviDati = new Contratto(0, dipendenteId, tipoContrattoCombo.getValue(),
                    leggiOre(), leggiCosto(), dataDecorrenza, leggiDataFine());
        } catch (IllegalArgumentException errore) {
            mostraMessaggio(errore.getMessage());
            return;
        }

        // Catturiamo gli ID insieme ai dati: il thread di lavoro non legge i controlli.
        final int idDipendente = dipendenteId;
        final int idAtteso = contrattoAttesoId;
        salvataggioInCorso = true;
        aggiornaControlli();
        mostraMessaggio("Salvataggio in corso…");

        Task<ContrattoService.Esito> salvataggio = new Task<>() {
            @Override
            protected ContrattoService.Esito call() throws SQLException {
                // Il service rilegge il contratto e gestisce validazioni, commit e rollback.
                return Objects.requireNonNull(service.aggiorna(idDipendente, idAtteso, nuoviDati));
            }
        };
        // Questi gestori vengono eseguiti sul thread JavaFX, dove aggiorniamo la UI.
        salvataggio.setOnSucceeded(event -> {
            salvataggioInCorso = false;
            salvataggioCompletato = true;
            aggiornaControlli();
            ContrattoService.Esito esito = salvataggio.getValue();
            if (esito.richiedeRicontrolloTurni()) {
                mostraMessaggio("Contratto aggiornato. Il monte ore è diminuito: "
                        + "ricontrolla i turni già pianificati.");
            } else if (esito.integrazionePianificazioneDaCompletare()) {
                // Non confondiamo la verifica non disponibile con l'assenza di turni.
                mostraMessaggio("Contratto aggiornato. Non è stato possibile verificare "
                        + "se ci sono turni da ricontrollare.");
            } else {
                mostraMessaggio("Contratto aggiornato correttamente.");
            }
            // Solo dopo il ritorno normale del service il commit è confermato.
            // Lasciamo aperto il modulo per leggere il messaggio e aggiorniamo l'elenco.
            if (azioneSalvato != null) {
                azioneSalvato.run();
            }
        });
        salvataggio.setOnFailed(event -> {
            salvataggioInCorso = false;
            aggiornaControlli();
            Throwable errore = salvataggio.getException();
            if (errore instanceof IllegalArgumentException) {
                mostraMessaggio(errore.getMessage());
            } else if (errore instanceof SQLException) {
                mostraMessaggio("Impossibile salvare il contratto. Verifica la connessione "
                        + "al database. I dati compilati sono stati conservati.");
            } else {
                mostraMessaggio("Si è verificato un errore durante il salvataggio. "
                        + "I dati compilati sono stati conservati.");
            }
            // Nessun campo viene svuotato: l'utente può correggere i dati e riprovare.
        });

        // La query non blocca l'interfaccia. Un daemon non trattiene il processo
        // dopo l'uscita dall'applicazione; non annulliamo un Task durante il commit.
        Thread thread = new Thread(salvataggio, "aggiornamento-contratto");
        thread.setDaemon(true);
        thread.start();
    }

    /** Converte il testo in intero; i limiti sulle ore sono verificati dal service. */
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

    /** Accetta punto o virgola decimale e conserva l'importo esatto con BigDecimal. */
    private BigDecimal leggiCosto() {
        String testo = costoOrarioField.getText().strip();
        if (testo.isEmpty()) {
            throw new IllegalArgumentException("Il costo orario è obbligatorio");
        }
        // Stesso formato della registrazione US01: nessun separatore delle migliaia
        // o notazione esponenziale. Positività e limiti SQL restano nel service.
        if (!testo.matches("[+-]?\\d+(?:[.,]\\d+)?")) {
            throw new IllegalArgumentException("Inserisci un costo orario numerico, per esempio 12,50");
        }
        return new BigDecimal(testo.replace(',', '.'));
    }

    /** Legge anche una data digitata nel calendario senza aver premuto Invio. */
    private LocalDate leggiDataFine() {
        String testo = dataFinePicker.getEditor().getText().strip();
        if (testo.isEmpty()) {
            return null;
        }
        try {
            return dataFinePicker.getConverter().fromString(testo);
        } catch (DateTimeException | IllegalArgumentException errore) {
            throw new IllegalArgumentException("La data di fine non è valida. Selezionala dal calendario.");
        }
    }

    /** Mostra il messaggio e gli riserva spazio nel layout. */
    private void mostraMessaggio(String testo) {
        messaggioLabel.setText(testo);
        messaggioLabel.setVisible(true);
        messaggioLabel.setManaged(true);
    }

    /** Mantiene coerenti campi e pulsanti durante attesa, errore e successo. */
    private void aggiornaControlli() {
        boolean disabilita = salvataggioInCorso || salvataggioCompletato || dipendenteId <= 0;
        for (Control controllo : List.of(tipoContrattoCombo, oreSettimanaliField,
                costoOrarioField, dataFinePicker, salvaButton)) {
            controllo.setDisable(disabilita);
        }
        annullaButton.setDisable(salvataggioInCorso || azioneAnnulla == null);
        // Dopo il commit il salvataggio non può più essere annullato: si può chiudere.
        annullaButton.setText(salvataggioCompletato ? "Chiudi" : "Annulla");
    }
}
