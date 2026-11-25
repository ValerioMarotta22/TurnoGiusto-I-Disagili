package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.model.Utente;
import it.disagili.turnogiusto.service.MieiTurniService;
import it.disagili.turnogiusto.service.SessioneUtente;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.converter.LocalDateStringConverter;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * La pagina del dipendente: i suoi turni di una settimana. Sola lettura.
 * Il dipendente e' quello che ha fatto l'accesso (SessioneUtente): non c'e' nessun modo di scegliere un altro.
 */
public class MieiTurniController {

    private static final DateTimeFormatter GIORNO = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.ITALIAN);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ITALIAN);
    private static final DateTimeFormatter ORA = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private DatePicker settimana;
    @FXML private Button mostra;
    @FXML private Label intervallo;
    @FXML private TableView<Turno> tabella;
    @FXML private TableColumn<Turno, String> giornoColonna;
    @FXML private TableColumn<Turno, String> servizioColonna;
    @FXML private TableColumn<Turno, String> orarioColonna;
    @FXML private TableColumn<Turno, String> ruoloColonna;
    @FXML private Label totale;
    @FXML private Label messaggio;

    private final MieiTurniService servizio;
    private final Utente utente;

    // Uso normale: il dipendente e' quello collegato in questo momento.
    public MieiTurniController() {
        this(new MieiTurniService(), SessioneUtente.corrente().orElse(null));
    }

    // Per i test: service finto e utente deciso dal test.
    MieiTurniController(MieiTurniService servizio, Utente utente) {
        this.servizio = servizio;
        this.utente = utente;
    }

    @FXML
    private void initialize() {
        settimana.setConverter(new LocalDateStringConverter(DATA, DATA));
        settimana.setValue(LocalDate.now());
        giornoColonna.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().inizio().format(GIORNO)));
        servizioColonna.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().servizio().toString()));
        orarioColonna.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().inizio().format(ORA) + " - " + c.getValue().fine().format(ORA)));
        ruoloColonna.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().ruolo().toString()));

        // Solo un dipendente ha turni suoi. Senza accesso, o per il titolare, la pagina non ha senso.
        if (utente == null || utente.dipendenteId() == null) {
            mostra.setDisable(true);
            messaggio.setText("Questa pagina è riservata ai dipendenti che hanno fatto l'accesso.");
            return;
        }
        onMostra(); // all'apertura si vede subito la settimana corrente
    }

    @FXML
    private void onMostra() {
        if (utente == null || utente.dipendenteId() == null) {
            return;
        }
        LocalDate scelto = settimana.getValue() == null ? LocalDate.now() : settimana.getValue();
        LocalDate lunedi = scelto.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        intervallo.setText("Settimana dal " + lunedi.format(DATA) + " al " + lunedi.plusDays(6).format(DATA));
        try {
            MieiTurniService.Settimana risultato = servizio.turniDi(utente.dipendenteId(), lunedi);
            tabella.getItems().setAll(risultato.turni());
            if (!risultato.pianoPresente()) {
                totale.setText("");
                info("Per questa settimana il titolare non ha ancora preparato il piano.");
            } else if (risultato.turni().isEmpty()) {
                totale.setText("Ore pianificate: 0");
                info("In questa settimana non hai turni.");
            } else {
                totale.setText("Ore pianificate: " + testoDurata(risultato.durata()));
                info("Questi sono i tuoi turni della settimana.");
            }
        } catch (SQLException eccezione) {
            tabella.getItems().clear();
            totale.setText("");
            errore("Impossibile leggere i turni. Controlla che il database sia acceso (docker compose up -d).");
        }
    }

    private static String testoDurata(Duration durata) {
        long ore = durata.toHours();
        long minuti = durata.toMinutesPart();
        return minuti == 0 ? ore + " h" : ore + " h " + minuti + " min";
    }

    private void info(String testo) {
        messaggio.setText(testo);
        messaggio.getStyleClass().remove("errore");
    }

    private void errore(String testo) {
        messaggio.setText(testo);
        if (!messaggio.getStyleClass().contains("errore")) {
            messaggio.getStyleClass().add("errore");
        }
    }
}
