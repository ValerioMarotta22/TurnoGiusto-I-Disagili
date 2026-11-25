package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.OrarioServizio;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.service.GenerazioneTurniService;
import it.disagili.turnogiusto.service.vincoli.VerificatoreVincoliUS07;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.concurrent.Task;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/** Genera e mostra la bozza con gli orari fissi dei servizi, mantenendo reattiva la schermata. */
public class GenerazioneTurniController {
    private static final DateTimeFormatter GIORNO = DateTimeFormatter.ofPattern("EEE dd/MM", Locale.ITALIAN);
    private static final DateTimeFormatter GIORNO_COMPLETO = DateTimeFormatter.ofPattern("EEEE dd/MM", Locale.ITALIAN);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ITALIAN);
    private static final Map<Servizio, OrarioServizio> ORARI_SERVIZI = Map.of(
            Servizio.PRANZO, new OrarioServizio(LocalTime.of(10, 0), LocalTime.of(16, 0)),
            Servizio.CENA, new OrarioServizio(LocalTime.of(17, 0), LocalTime.of(0, 30)));
    private static final PseudoClass GIORNO_PIANO = PseudoClass.getPseudoClass("giorno-piano");
    private static final PseudoClass SERVIZIO_PIANO = PseudoClass.getPseudoClass("servizio-piano");

    @FXML private VBox impostazioni;
    @FXML private DatePicker settimana;
    @FXML private Label orarioPranzo;
    @FXML private Label orarioCena;
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
        // Con i controlli di US07 collegati: senza, il generatore ignorerebbe riposi, doppi servizi e monte ore.
        this(GenerazioneTurniService.conVincoli());
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
        orarioPranzo.setText(testoServizio(Servizio.PRANZO));
        orarioCena.setText(testoServizio(Servizio.CENA));
        configuraTabella();
    }

    /** Giorni e servizi si aprono da tutta la riga, mantenendo frecce e navigazione da tastiera. */
    private void configuraTabella() {
        tabella.setTreeColumn(orarioColonna);
        orarioColonna.setCellValueFactory(cella -> new ReadOnlyStringWrapper(cella.getValue().getValue().testo()));
        configuraColonnaTurno(ruoloColonna, turno -> turno.ruolo().toString());
        configuraColonnaTurno(assegnatiColonna, this::testoAssegnati);
        configuraColonnaTurno(richiestiColonna, Turno::postiRichiesti);
        configuraColonnaTurno(scopertiColonna, Turno::postiScoperti);
        tabella.setRowFactory(vista -> new TreeTableRow<>() {
            {
                addEventFilter(MouseEvent.MOUSE_PRESSED, evento -> {
                    TreeItem<RigaPiano> voce = getTreeItem();
                    if (evento.getButton() == MouseButton.PRIMARY && !isEmpty()
                            && voce != null && !voce.isLeaf()) {
                        vista.requestFocus();
                        vista.getSelectionModel().select(voce);
                        vista.getFocusModel().focus(getIndex());
                        voce.setExpanded(!voce.isExpanded());
                        // Intercetta anche la freccia e il doppio clic prima del comportamento nativo.
                        evento.consume();
                    }
                });
            }

            @Override
            protected void updateItem(RigaPiano riga, boolean vuota) {
                super.updateItem(riga, vuota);
                TreeItem<RigaPiano> voce = getTreeItem();
                boolean intestazione = !vuota && voce != null && !voce.isLeaf();
                boolean giorno = intestazione && voce.getParent() == tabella.getRoot();
                // Azzera entrambi gli stati quando JavaFX riutilizza una riga per un turno.
                pseudoClassStateChanged(GIORNO_PIANO, giorno);
                pseudoClassStateChanged(SERVIZIO_PIANO, intestazione && !giorno);
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

    private String testoServizio(Servizio servizio) {
        OrarioServizio orario = ORARI_SERVIZI.get(servizio);
        return servizio + " " + orario.inizio() + " – " + orario.fine();
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
        try {
            lunedi = settimana.getValue();
            PianoSettimanale.verificaInizioSettimana(lunedi);
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
                return servizio.genera(lunedi, ORARI_SERVIZI, giorniFestivi, List.of());
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
            testo += " " + String.join(". ", esito.violazioni().stream().map(this::conNome).toList());
        }
        mostraMessaggio(testo, !esito.violazioni().isEmpty());
    }

    /** I controlli scrivono "Dipendente 12: motivo": qui l'id diventa il nome, come nella modifica del piano. */
    private String conNome(String violazione) {
        Matcher prefisso = VerificatoreVincoliUS07.PREFISSO_DIPENDENTE.matcher(violazione);
        if (prefisso.find()) {
            String nome = nomiDipendenti.get(Integer.parseInt(prefisso.group(1)));
            if (nome != null) {
                return nome + ": " + violazione.substring(prefisso.end());
            }
        }
        return violazione;
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
        TreeItem<RigaPiano> gruppo = new TreeItem<>(new RigaPiano(giorno, testoServizio(servizio), null));
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
