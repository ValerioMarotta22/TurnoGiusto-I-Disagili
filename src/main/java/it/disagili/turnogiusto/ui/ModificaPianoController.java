package it.disagili.turnogiusto.ui;

// --- IMPORT -------------------------------------------------------------
import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.service.ModificaPianoException;
import it.disagili.turnogiusto.service.ModificaPianoService;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.util.converter.LocalDateStringConverter;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Schermata di modifica del piano settimanale (US-06B).
 *
 * Il controller NON contiene regole ne' SQL: legge i campi, chiama ModificaPianoService e mostra
 * il risultato. Tiene in memoria il piano che si sta modificando (corrente) e ricorda se ci sono
 * modifiche non ancora salvate (modificato): solo "Salva piano" scrive sul database.
 */
public class ModificaPianoController {

    // Formati di data e ora in italiano (lun 30/11, 18:00...).
    private static final DateTimeFormatter GIORNO = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.ITALIAN);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ITALIAN);
    private static final DateTimeFormatter ORA = DateTimeFormatter.ofPattern("HH:mm");

    private static final String ERRORE_DATABASE =
            "Impossibile leggere o scrivere sul database. Controlla che sia acceso (docker compose up -d).";

    // ---------------------------------------------------------------- campi collegati al FXML
    @FXML private DatePicker settimana;
    @FXML private Button carica;
    @FXML private Button genera;
    @FXML private Label intervallo;
    @FXML private TableView<Turno> tabella;
    @FXML private TableColumn<Turno, String> giornoColonna;
    @FXML private TableColumn<Turno, String> servizioColonna;
    @FXML private TableColumn<Turno, String> orarioColonna;
    @FXML private TableColumn<Turno, String> ruoloColonna;
    @FXML private TableColumn<Turno, Integer> richiestiColonna;
    @FXML private TableColumn<Turno, Integer> scopertiColonna;
    @FXML private TableColumn<Turno, String> assegnatiColonna;
    @FXML private VBox pannelloModifica;
    @FXML private ComboBox<Persona> daSostituire;
    @FXML private ComboBox<Persona> nuovaPersona;
    @FXML private Button sostituisci;
    @FXML private Button copriPosto;
    @FXML private ListView<String> violazioni;
    @FXML private Label statoControlli;
    @FXML private Button salva;
    @FXML private Button annulla;
    @FXML private Label messaggio;

    /** Una persona nei menu a tendina: nel menu si legge il nome, nel codice serve l'ID. */
    record Persona(int id, String nome) {
        @Override
        public String toString() {
            return nome;
        }
    }

    private final ModificaPianoService servizio;

    // ---------------------------------------------------------------- stato della schermata
    private LocalDate lunedi;                 // la settimana mostrata
    private PianoSettimanale corrente;        // il piano mostrato, con le modifiche non ancora salvate
    private boolean modificato;               // ci sono modifiche non salvate?
    private Map<Integer, String> nomi = Map.of(); // id -> nome dei dipendenti

    // Prima di buttare via modifiche non salvate si chiede conferma. Nei test si puo' sostituire.
    private BooleanSupplier confermaScarto = this::chiediConfermaScarto;

    public ModificaPianoController() {
        this(new ModificaPianoService());
    }

    // Permette ai test di passare un service finto.
    ModificaPianoController(ModificaPianoService servizio) {
        this.servizio = servizio;
    }

    void impostaConfermaScarto(BooleanSupplier conferma) {
        this.confermaScarto = conferma;
    }

    // ======================================================================
    // INIZIALIZZAZIONE: parte da sola quando JavaFX ha caricato il FXML
    // ======================================================================
    @FXML
    private void initialize() {
        settimana.setConverter(new LocalDateStringConverter(DATA, DATA));
        settimana.setValue(LocalDate.now());
        configuraTabella();

        // Quando cambia la riga scelta nella tabella si aggiorna il pannello di modifica.
        tabella.getSelectionModel().selectedItemProperty().addListener((osservabile, prima, dopo) -> aggiornaPannello());
        // I pulsanti si attivano solo quando ci sono le scelte necessarie.
        daSostituire.valueProperty().addListener((osservabile, prima, dopo) -> aggiornaPulsanti());
        nuovaPersona.valueProperty().addListener((osservabile, prima, dopo) -> aggiornaPulsanti());
        aggiornaPulsanti();
    }

    // Dice a ogni colonna quale dato della riga (un Turno) mostrare.
    private void configuraTabella() {
        giornoColonna.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().inizio().format(GIORNO)));
        servizioColonna.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().servizio().toString()));
        orarioColonna.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().inizio().format(ORA) + " - " + c.getValue().fine().format(ORA)));
        ruoloColonna.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().ruolo().toString()));
        richiestiColonna.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().postiRichiesti()));
        scopertiColonna.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().postiScoperti()));
        assegnatiColonna.setCellValueFactory(c -> new ReadOnlyStringWrapper(testoAssegnati(c.getValue())));

        // Colonna "Scoperti": rosso tenue se mancano persone, verde tenue se la riga e' completa.
        scopertiColonna.setCellFactory(colonna -> new TableCell<>() {
            @Override
            protected void updateItem(Integer valore, boolean vuota) {
                super.updateItem(valore, vuota);
                if (vuota || valore == null) {
                    setText(null);
                    setStyle(""); // JavaFX riutilizza le celle mentre si scorre: va ripulita
                    return;
                }
                setText(valore.toString());
                setStyle("-fx-alignment: CENTER; -fx-background-color: "
                        + (valore > 0 ? "#FBE7E6" : "#E7F3E8") + "; -fx-text-fill: #243746;");
            }
        });
    }

    // ======================================================================
    // CARICARE IL PIANO DI UNA SETTIMANA
    // ======================================================================
    @FXML
    private void onCarica() {
        LocalDate scelto = settimana.getValue();
        if (scelto == null) {
            errore("Scegli un giorno della settimana.");
            return;
        }
        if (modificato && !confermaScarto.getAsBoolean()) {
            return; // l'utente ha deciso di tenere le modifiche
        }
        caricaSettimana(scelto.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), null);
    }

    // Legge il piano dal database e lo mostra. "prefisso" e' un testo opzionale da mettere davanti al messaggio.
    private void caricaSettimana(LocalDate lunediScelto, String prefisso) {
        try {
            lunedi = lunediScelto;
            intervallo.setText("Settimana dal " + lunedi.format(DATA) + " al " + lunedi.plusDays(6).format(DATA));
            nomi = servizio.nomiDipendenti(lunedi);
            modificato = false;

            Optional<PianoSettimanale> salvato = servizio.carica(lunedi);
            if (salvato.isPresent()) {
                mostra(salvato.get());
                mostraEsito(servizio.verifica(salvato.get()));
                info(testo(prefisso, "Piano caricato. Scegli una riga per modificarlo."));
            } else {
                // Nessun piano salvato: si puo' generarne uno di base.
                corrente = null;
                tabella.getItems().clear();
                violazioni.getItems().clear();
                statoControlli.setText("");
                info(testo(prefisso, "Per questa settimana non c'è ancora un piano salvato. "
                        + "Puoi generarne uno di base e poi modificarlo."));
            }
        } catch (SQLException eccezione) {
            corrente = null;
            tabella.getItems().clear();
            errore(ERRORE_DATABASE);
        }
        aggiornaPannello();
        aggiornaPulsanti();
    }

    // ======================================================================
    // GENERARE UN PIANO DI BASE (solo se la settimana non ha un piano salvato)
    // ======================================================================
    @FXML
    private void onGenera() {
        try {
            ModificaPianoService.Esito esito = servizio.generaPiano(lunedi);
            modificato = true; // la bozza non e' ancora salvata
            mostra(esito.piano());
            mostraEsito(esito);
            info("Piano di base generato. Controllalo, correggilo se serve e poi salvalo.");
        } catch (SQLException eccezione) {
            errore(ERRORE_DATABASE);
        } catch (IllegalArgumentException | IllegalStateException eccezione) {
            errore("Impossibile generare il piano: " + eccezione.getMessage());
        }
        aggiornaPannello();
        aggiornaPulsanti();
    }

    // ======================================================================
    // SOSTITUIRE UNA PERSONA
    // ======================================================================
    @FXML
    private void onSostituisci() {
        Turno riga = tabella.getSelectionModel().getSelectedItem();
        Persona esce = daSostituire.getValue();
        Persona entra = nuovaPersona.getValue();
        if (riga == null || esce == null || entra == null) {
            errore("Scegli la riga, la persona da sostituire e quella nuova.");
            return;
        }
        try {
            // Il service controlla le regole e restituisce un piano NUOVO (non salvato) con l'esito dei controlli.
            ModificaPianoService.Esito esito = servizio.sostituisci(corrente, riga, esce.id(), entra.id());
            applica(esito, riga, esce.nome() + " sostituito da " + entra.nome() + ".");
        } catch (ModificaPianoException eccezione) {
            errore(eccezione.getMessage()); // messaggio gia' scritto per l'utente
        } catch (SQLException eccezione) {
            errore(ERRORE_DATABASE);
        }
    }

    // ======================================================================
    // COPRIRE UN POSTO SCOPERTO
    // ======================================================================
    @FXML
    private void onCopriPosto() {
        Turno riga = tabella.getSelectionModel().getSelectedItem();
        Persona entra = nuovaPersona.getValue();
        if (riga == null || entra == null) {
            errore("Scegli la riga e la persona da inserire.");
            return;
        }
        try {
            ModificaPianoService.Esito esito = servizio.assegnaAlPostoScoperto(corrente, riga, entra.id());
            applica(esito, riga, entra.nome() + " inserito in un posto scoperto.");
        } catch (ModificaPianoException eccezione) {
            errore(eccezione.getMessage());
        } catch (SQLException eccezione) {
            errore(ERRORE_DATABASE);
        }
    }

    // Dopo una modifica riuscita: mostra il piano nuovo e i controlli, e ricorda che va salvato.
    private void applica(ModificaPianoService.Esito esito, Turno rigaModificata, String descrizione) {
        modificato = true;
        mostra(esito.piano());
        selezionaRiga(rigaModificata);
        mostraEsito(esito);
        info(descrizione + " Le modifiche non sono ancora salvate.");
        aggiornaPulsanti();
    }

    // ======================================================================
    // SALVARE E ANNULLARE
    // ======================================================================
    @FXML
    private void onSalva() {
        if (corrente == null) {
            return;
        }
        try {
            servizio.salva(corrente);
            modificato = false;
            info("Piano salvato. Se chiudi e riapri la schermata lo ritrovi com'è adesso.");
        } catch (SQLException eccezione) {
            errore(ERRORE_DATABASE);
        }
        aggiornaPulsanti();
    }

    @FXML
    private void onAnnulla() {
        if (lunedi == null || (modificato && !confermaScarto.getAsBoolean())) {
            return;
        }
        // Rilegge il piano dal database: le modifiche non salvate spariscono.
        caricaSettimana(lunedi, "Modifiche annullate.");
    }

    // ======================================================================
    // MOSTRARE I DATI
    // ======================================================================

    // Mette il piano nella tabella e lo ricorda come piano corrente.
    private void mostra(PianoSettimanale piano) {
        corrente = piano;
        tabella.getItems().setAll(piano.turni());
        aggiornaPannello();
    }

    // Dopo una modifica la riga e' un Turno nuovo: si ritrova quella con stesso servizio, ruolo e orario.
    private void selezionaRiga(Turno riga) {
        for (Turno turno : tabella.getItems()) {
            if (turno.servizio() == riga.servizio() && turno.ruolo() == riga.ruolo()
                    && turno.inizio().equals(riga.inizio())) {
                tabella.getSelectionModel().select(turno);
                return;
            }
        }
    }

    // Elenco delle violazioni e una frase che dice se i controlli sono stati fatti davvero.
    private void mostraEsito(ModificaPianoService.Esito esito) {
        violazioni.getItems().setAll(esito.violazioni());
        if (!esito.controlloEseguito()) {
            statoControlli.setText("I controlli sulle regole non sono stati eseguiti: l'elenco vuoto "
                    + "NON vuol dire che il piano sia a posto.");
        } else if (esito.violazioni().isEmpty()) {
            statoControlli.setText("Nessuna violazione: il piano rispetta le regole controllate.");
        } else {
            statoControlli.setText(esito.violazioni().size() + " violazione/i. Puoi salvare lo stesso: "
                    + "la decisione è del titolare.");
        }
    }

    // Riempie i due menu del pannello a partire dalla riga scelta nella tabella.
    private void aggiornaPannello() {
        Turno riga = tabella.getSelectionModel().getSelectedItem();
        daSostituire.getItems().clear();
        nuovaPersona.getItems().clear();
        if (riga == null || corrente == null) {
            pannelloModifica.setDisable(true);
            return;
        }
        pannelloModifica.setDisable(false);

        // "Chi esce": le persone gia' assegnate a quella riga.
        for (AssegnazioneTurno assegnazione : riga.assegnazioni()) {
            daSostituire.getItems().add(new Persona(assegnazione.dipendenteId(), nome(assegnazione.dipendenteId())));
        }
        // "Chi entra": solo i dipendenti idonei, calcolati dal service.
        try {
            for (Dipendente dipendente : servizio.candidati(corrente, riga)) {
                nuovaPersona.getItems().add(new Persona(dipendente.getId(), dipendente.getNomeCompleto()));
            }
        } catch (SQLException eccezione) {
            errore(ERRORE_DATABASE);
        } catch (ModificaPianoException eccezione) {
            errore(eccezione.getMessage());
        }
        aggiornaPulsanti();
    }

    // Attiva o disattiva i pulsanti in base a cosa e' stato scelto.
    private void aggiornaPulsanti() {
        Turno riga = tabella.getSelectionModel().getSelectedItem();
        boolean haRiga = riga != null && corrente != null;
        sostituisci.setDisable(!haRiga || daSostituire.getValue() == null || nuovaPersona.getValue() == null);
        copriPosto.setDisable(!haRiga || riga.postiScoperti() == 0 || nuovaPersona.getValue() == null);
        salva.setDisable(!modificato || corrente == null);
        annulla.setDisable(!modificato);
        // "Genera" compare solo se la settimana e' stata caricata e non ha un piano.
        boolean nessunPiano = lunedi != null && corrente == null;
        genera.setVisible(nessunPiano);
        genera.setManaged(nessunPiano);
    }

    // ======================================================================
    // MESSAGGI E METODI DI AIUTO
    // ======================================================================
    private void info(String testo) {
        messaggio.setText(testo);
        messaggio.getStyleClass().remove("errore");
    }

    private void errore(String testo) {
        messaggio.setText(testo);
        if (!messaggio.getStyleClass().contains("errore")) {
            messaggio.getStyleClass().add("errore"); // sfondo rosso tenue, definito nel CSS
        }
    }

    private static String testo(String prefisso, String frase) {
        return prefisso == null ? frase : prefisso + " " + frase;
    }

    private String nome(int dipendenteId) {
        return nomi.getOrDefault(dipendenteId, "Dipendente " + dipendenteId);
    }

    private String testoAssegnati(Turno riga) {
        if (riga.assegnazioni().isEmpty()) {
            return "—";
        }
        List<String> persone = riga.assegnazioni().stream()
                .map(assegnazione -> nome(assegnazione.dipendenteId()))
                .collect(Collectors.toList());
        return String.join(", ", persone);
    }

    private boolean chiediConfermaScarto() {
        Alert avviso = new Alert(Alert.AlertType.CONFIRMATION,
                "Ci sono modifiche non salvate: se continui andranno perse.", ButtonType.OK, ButtonType.CANCEL);
        avviso.setHeaderText("Scartare le modifiche?");
        return avviso.showAndWait().filter(bottone -> bottone == ButtonType.OK).isPresent();
    }
}
