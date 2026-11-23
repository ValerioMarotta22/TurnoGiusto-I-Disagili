package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.OrarioServizio;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.service.GenerazioneTurniService;
import it.disagili.turnogiusto.service.ValidazioneDisponibilita;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.converter.LocalDateStringConverter;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Legge gli orari e mostra la bozza generata dal service, mantenendo reattiva la schermata. */
public class GenerazioneTurniController {
    private static final DateTimeFormatter GIORNO = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.ITALIAN);
    private static final DateTimeFormatter GIORNO_COMPLETO = DateTimeFormatter.ofPattern("EEEE dd/MM", Locale.ITALIAN);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ITALIAN);

    @FXML private VBox impostazioni;
    @FXML private DatePicker settimana;
    @FXML private ComboBox<String> inizioPranzo;
    @FXML private ComboBox<String> finePranzo;
    @FXML private ComboBox<String> inizioCena;
    @FXML private ComboBox<String> fineCena;
    @FXML private FlowPane festivi;
    @FXML private TreeTableView<RigaPiano> tabella;
    @FXML private TreeTableColumn<RigaPiano, String> orarioColonna;
    @FXML private TreeTableColumn<RigaPiano, String> ruoloColonna;
    @FXML private TreeTableColumn<RigaPiano, String> assegnatiColonna;
    @FXML private TreeTableColumn<RigaPiano, Integer> richiestiColonna;
    @FXML private TreeTableColumn<RigaPiano, Integer> scopertiColonna;
    @FXML private Label messaggio;

    private final GenerazioneTurniService servizio;
    private Map<Integer, String> nomiDipendenti = Map.of();

    public GenerazioneTurniController() {
        this(new GenerazioneTurniService());
    }

    GenerazioneTurniController(GenerazioneTurniService servizio) {
        this.servizio = servizio;
    }

    @FXML
    private void initialize() {
        settimana.setConverter(new LocalDateStringConverter(DATA, DATA));
        settimana.setDayCellFactory(calendario -> new DateCell() {
            @Override
            public void updateItem(LocalDate data, boolean vuota) {
                super.updateItem(data, vuota);
                // La generazione parte sempre da un lunedì.
                setDisable(vuota || data == null || data.getDayOfWeek() != DayOfWeek.MONDAY);
            }
        });
        settimana.setValue(LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
        // Le etichette dei festivi seguono le date della settimana selezionata.
        settimana.valueProperty().addListener((osservabile, prima, dopo) -> cambiaSettimana(dopo));
        cambiaSettimana(settimana.getValue());
        preparaOrari();
        configuraTabella();
    }

    /** Giorni e servizi usano le frecce e i rientri nativi della tabella ad albero. */
    private void configuraTabella() {
        tabella.setTreeColumn(orarioColonna);
        orarioColonna.setCellValueFactory(cella -> new ReadOnlyStringWrapper(cella.getValue().getValue().testo()));
        configuraColonnaTurno(ruoloColonna, turno -> turno.ruolo().toString());
        configuraColonnaTurno(assegnatiColonna, this::testoAssegnati);
        configuraColonnaTurno(richiestiColonna, Turno::postiRichiesti);
        configuraColonnaTurno(scopertiColonna, Turno::postiScoperti);
        tabella.setRowFactory(vista -> new TreeTableRow<>() {
            @Override
            protected void updateItem(RigaPiano riga, boolean vuota) {
                super.updateItem(riga, vuota);
                TreeItem<RigaPiano> voce = getTreeItem();
                if (vuota || voce == null || voce.isLeaf()) {
                    setStyle("");
                    return;
                }
                // Lo sfondo distingue le intestazioni dei giorni da quelle rientrate dei servizi.
                String sfondo = voce.getParent() == tabella.getRoot() ? "#e8eff7" : "#f1f5fa";
                setStyle("-fx-background-color: " + sfondo + "; -fx-font-weight: bold;");
            }
        });
        scopertiColonna.setCellFactory(colonna -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Integer valore, boolean vuota) {
                super.updateItem(valore, vuota);
                if (vuota || valore == null) {
                    // JavaFX riutilizza le celle mentre si scorre la tabella.
                    setText(null);
                    setStyle("");
                    return;
                }
                String sfondo = valore > 0 ? "#FBE7E6" : "#E7F3E8";
                setText(valore.toString());
                setStyle("-fx-alignment: CENTER; -fx-background-color: " + sfondo + "; -fx-text-fill: #243746;");
            }
        });
        assegnatiColonna.setCellFactory(colonna -> new TreeTableCell<>() {
            private final Label elenco = new Label();

            {
                // I nomi vanno a capo nelle righe che hanno molte assegnazioni.
                elenco.setWrapText(true);
                elenco.prefWidthProperty().bind(colonna.widthProperty().subtract(20));
            }

            @Override
            protected void updateItem(String testo, boolean vuota) {
                super.updateItem(testo, vuota);
                elenco.setText(testo);
                setGraphic(vuota || testo == null ? null : elenco);
            }
        });
    }

    /** Le colonne di dettaglio contengono valori soltanto nelle righe dei turni. */
    private <T> void configuraColonnaTurno(TreeTableColumn<RigaPiano, T> colonna, Function<Turno, T> valore) {
        colonna.setCellValueFactory(cella -> {
            Turno turno = cella.getValue().getValue().turno();
            return turno == null ? null : new ReadOnlyObjectWrapper<>(valore.apply(turno));
        });
    }

    /** Propone intervalli di 15 minuti lasciando inseribili anche gli orari fuori elenco. */
    private void preparaOrari() {
        ObservableList<String> orari = FXCollections.observableArrayList();
        for (int minuti = 0; minuti < 24 * 60; minuti += 15) {
            orari.add(LocalTime.MIDNIGHT.plusMinutes(minuti).toString());
        }
        inizioPranzo.setItems(orari);
        finePranzo.setItems(orari);
        inizioCena.setItems(orari);
        fineCena.setItems(orari);
        inizioPranzo.setValue("11:30");
        finePranzo.setValue("16:00");
        inizioCena.setValue("18:00");
        fineCena.setValue("00:30");
    }

    /** Quando cambia la settimana, le scelte dei festivi della precedente vengono azzerate. */
    private void cambiaSettimana(LocalDate lunedi) {
        festivi.getChildren().clear();
        if (lunedi == null || lunedi.getDayOfWeek() != DayOfWeek.MONDAY) {
            return;
        }
        for (int indice = 0; indice < 7; indice++) {
            LocalDate giorno = lunedi.plusDays(indice);
            CheckBox scelta = new CheckBox(GIORNO.format(giorno));
            scelta.setUserData(giorno);
            festivi.getChildren().add(scelta);
        }
    }

    /** Legge i campi sul thread JavaFX. Solo il service legge il database in background. */
    @FXML
    private void onGenera() {
        LocalDate lunedi;
        Map<Servizio, OrarioServizio> orari;
        try {
            lunedi = settimana.getValue();
            PianoSettimanale.verificaInizioSettimana(lunedi);
            orari = Map.of(Servizio.PRANZO, leggiOrario(inizioPranzo, finePranzo),
                    Servizio.CENA, leggiOrario(inizioCena, fineCena));
        } catch (IllegalArgumentException errore) {
            mostraMessaggio(errore.getMessage(), true);
            return;
        }
        Set<LocalDate> giorniFestivi = leggiFestivi();
        impostazioni.setDisable(true);
        mostraMessaggio("Generazione in corso…", false);

        Task<GenerazioneTurniService.Esito> lavoro = new Task<>() {
            @Override
            protected GenerazioneTurniService.Esito call() throws SQLException {
                // La persistenza della US06B fornirà i turni delle settimane confinanti.
                return servizio.genera(lunedi, orari, giorniFestivi, List.of());
            }
        };
        lavoro.setOnSucceeded(evento -> {
            mostraPiano(lavoro.getValue());
            impostazioni.setDisable(false);
        });
        lavoro.setOnFailed(evento -> {
            impostazioni.setDisable(false);
            Throwable errore = lavoro.getException();
            String testo = errore instanceof SQLException
                    ? "Lettura del database non riuscita. Controlla MySQL in Docker e riprova."
                    : "Generazione non riuscita. " + errore.getMessage();
            if (tabella.getRoot() != null) {
                testo += " La tabella mostra l'ultima bozza generata.";
            }
            mostraMessaggio(testo, true);
        });
        Thread thread = new Thread(lavoro, "generazione-turni");
        thread.setDaemon(true);
        thread.start();
    }

    private OrarioServizio leggiOrario(ComboBox<String> inizio, ComboBox<String> fine) {
        return new OrarioServizio(leggiOra(inizio), leggiOra(fine));
    }

    /** Legge anche il testo appena digitato, prima che la ComboBox aggiorni il valore selezionato. */
    private LocalTime leggiOra(ComboBox<String> campo) {
        try {
            return ValidazioneDisponibilita.leggiOra(campo.getEditor().getText());
        } catch (IllegalArgumentException errore) {
            throw new IllegalArgumentException(campo.getAccessibleText()
                    + ". Scegli un orario dall'elenco o scrivilo come 18:30.");
        }
    }

    private Set<LocalDate> leggiFestivi() {
        Set<LocalDate> selezionati = new HashSet<>();
        for (var nodo : festivi.getChildren()) {
            CheckBox scelta = (CheckBox) nodo;
            if (scelta.isSelected()) {
                selezionati.add((LocalDate) scelta.getUserData());
            }
        }
        return selezionati;
    }

    private void mostraPiano(GenerazioneTurniService.Esito esito) {
        nomiDipendenti = esito.nomiDipendenti();
        PianoSettimanale piano = esito.piano();
        mostraGiorni(piano);
        int assegnati = piano.turni().stream().mapToInt(turno -> turno.assegnazioni().size()).sum();
        String testo = "Bozza dal " + piano.inizioSettimana().format(DATA)
                + " al " + piano.inizioSettimana().plusDays(6).format(DATA)
                + ". Posti assegnati: " + assegnati + ". Posti scoperti: " + piano.postiScoperti() + ".";
        if (!esito.controlloEseguito()) {
            testo += " Monte ore e riposi da verificare.";
        } else if (!esito.violazioni().isEmpty()) {
            testo += " " + String.join(". ", esito.violazioni());
        }
        mostraMessaggio(testo, !esito.violazioni().isEmpty());
    }

    /** Raggruppa per data di inizio e poi per servizio. I gruppi con turni partono aperti. */
    private void mostraGiorni(PianoSettimanale piano) {
        TreeItem<RigaPiano> radice = new TreeItem<>();
        for (int indice = 0; indice < 7; indice++) {
            LocalDate giorno = piano.inizioSettimana().plusDays(indice);
            List<Turno> turniDelGiorno = piano.turni().stream()
                    .filter(turno -> turno.inizio().toLocalDate().equals(giorno)).toList();
            TreeItem<RigaPiano> gruppo = new TreeItem<>(new RigaPiano(giorno, giorno.format(GIORNO_COMPLETO), null));
            if (turniDelGiorno.isEmpty()) {
                gruppo.getChildren().add(new TreeItem<>(new RigaPiano(giorno, "Nessun servizio previsto.", null)));
            }
            for (Servizio servizio : Servizio.values()) {
                gruppo.getChildren().add(creaGruppoServizio(giorno, servizio, turniDelGiorno));
            }
            gruppo.setExpanded(!turniDelGiorno.isEmpty());
            radice.getChildren().add(gruppo);
        }
        radice.setExpanded(true);
        tabella.setRoot(radice);
    }

    /** Anche un servizio senza turni si può aprire, perché contiene un messaggio. */
    private TreeItem<RigaPiano> creaGruppoServizio(LocalDate giorno, Servizio servizio, List<Turno> turniDelGiorno) {
        TreeItem<RigaPiano> gruppo = new TreeItem<>(new RigaPiano(giorno, servizio.toString(), null));
        for (Turno turno : turniDelGiorno) {
            if (turno.servizio() == servizio) {
                gruppo.getChildren().add(new TreeItem<>(new RigaPiano(giorno, testoOrario(turno), turno)));
            }
        }
        if (gruppo.isLeaf()) {
            gruppo.getChildren().add(new TreeItem<>(new RigaPiano(giorno, "Servizio non previsto.", null)));
        } else {
            gruppo.setExpanded(true);
        }
        return gruppo;
    }

    private String testoOrario(Turno turno) {
        LocalDateTime inizio = turno.inizio();
        LocalDateTime fine = turno.fine();
        String giornoFine = inizio.toLocalDate().equals(fine.toLocalDate()) ? "" : fine.format(GIORNO) + " ";
        return inizio.toLocalTime() + " – " + giornoFine + fine.toLocalTime();
    }

    private String testoAssegnati(Turno turno) {
        if (turno.assegnazioni().isEmpty()) {
            return "Nessuno";
        }
        return turno.assegnazioni().stream()
                .map(assegnazione -> nomiDipendenti.getOrDefault(assegnazione.dipendenteId(), "ID " + assegnazione.dipendenteId()))
                .collect(Collectors.joining(", "));
    }

    private void mostraMessaggio(String testo, boolean errore) {
        messaggio.setText(testo);
        messaggio.getStyleClass().remove("errore");
        if (errore) {
            messaggio.getStyleClass().add("errore");
        }
    }

    /** Le intestazioni e gli avvisi hanno solo testo. Le righe di dettaglio contengono anche il turno. */
    record RigaPiano(LocalDate giorno, String testo, Turno turno) {
    }
}
