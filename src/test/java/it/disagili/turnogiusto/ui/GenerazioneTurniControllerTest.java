package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.Fabbisogno;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.TipoGiorno;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.service.GenerazioneTurniService;
import it.disagili.turnogiusto.repository.FabbisognoRepository;
import it.disagili.turnogiusto.ui.GenerazioneTurniController.RigaPiano;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static it.disagili.turnogiusto.ui.JavaFxTestSupport.suJavaFx;
import static org.junit.jupiter.api.Assertions.*;

/** Controlla il percorso JavaFX dal menu al piano e i messaggi della schermata US06A. */
@ExtendWith(JavaFxTestSupport.class)
class GenerazioneTurniControllerTest {
    private static final LocalDate LUNEDI = LocalDate.of(2026, 11, 23);

    // La finestra principale mostra le voci in base a chi ha fatto l'accesso: questi test partono dal menu
    // del titolare, quindi si simula l'accesso del titolare e poi lo si toglie, per non influenzare altri test.
    @org.junit.jupiter.api.BeforeEach
    void accediComeTitolare() {
        it.disagili.turnogiusto.service.SessioneUtente.accedi(new it.disagili.turnogiusto.model.Utente(
                1, "titolare", it.disagili.turnogiusto.model.TipoUtente.TITOLARE, null, "Titolare", false));
    }

    @org.junit.jupiter.api.AfterEach
    void esci() {
        it.disagili.turnogiusto.service.SessioneUtente.esci();
    }

    /** Parte dal menu reale, usa i controlli in una finestra e legge il MySQL configurato per i test. */
    @Test
    @Tag("database")
    void generaEVisualizzaIlPianoDalMenuConIlDatabaseReale() throws Exception {
        List<Fabbisogno> configurati = new FabbisognoRepository().trovaTutti();
        CountDownLatch completato = new CountDownLatch(1);
        Stage finestra = suJavaFx(() -> {
            BorderPane radice = FXMLLoader.load(getClass().getResource("/it/disagili/turnogiusto/main-view.fxml"));
            Stage stage = new Stage();
            stage.setScene(new Scene(radice, 900, 600));
            stage.show();
            return stage;
        });

        try {
            suJavaFx(() -> {
                BorderPane radice = (BorderPane) finestra.getScene().getRoot();
                MenuBar barra = (MenuBar) radice.getTop();
                barra.getMenus().stream().filter(menu -> menu.getText().equals("Turni")).findFirst().orElseThrow()
                        .getItems().getFirst().fire();
                radice.applyCss();
                radice.layout();

                DatePicker data = (DatePicker) radice.lookup("#settimana");
                data.show();
                assertTrue(data.isShowing());
                data.setValue(LUNEDI);
                data.hide();

                FlowPane giorni = (FlowPane) radice.lookup("#festivi");
                CheckBox domenica = (CheckBox) giorni.getChildren().getLast();
                domenica.fire();
                assertTrue(domenica.isSelected());
                Label stato = (Label) radice.lookup("#messaggio");
                stato.textProperty().addListener((osservabile, prima, dopo) -> {
                    if (!dopo.equals("Generazione in corso…")) completato.countDown();
                });
                ((Button) radice.lookup("#genera")).fire();
                return null;
            });

            assertTrue(completato.await(10, TimeUnit.SECONDS), "La generazione sul database deve terminare");
            suJavaFx(() -> {
                Parent radice = finestra.getScene().getRoot();
                Label stato = (Label) radice.lookup("#messaggio");
                TreeTableView<RigaPiano> tabella = tabella(radice);
                assertTrue(stato.getText().startsWith("Bozza dal 23/11/2026 al 29/11/2026"), stato.getText());
                assertFalse(radice.lookup("#genera").isDisabled());
                verificaRigheMostrate(tabella, configurati);
                return null;
            });
        } finally {
            suJavaFx(() -> {
                finestra.close();
                return null;
            });
        }
    }

    /** Confronta le righe del piano con i fabbisogni effettivi per feriali, sabato e domenica festiva. */
    private void verificaRigheMostrate(TreeTableView<RigaPiano> tabella, List<Fabbisogno> configurati) {
        assertEquals(7, tabella.getRoot().getChildren().size());
        for (int indice = 0; indice < 7; indice++) {
            LocalDate giorno = LUNEDI.plusDays(indice);
            TipoGiorno tipo = switch (indice) {
                case 5 -> TipoGiorno.WEEKEND;
                case 6 -> TipoGiorno.FESTIVO;
                default -> TipoGiorno.FERIALE;
            };
            List<Fabbisogno> attesi = configurati.stream()
                    .filter(fabbisogno -> fabbisogno.getTipoGiorno() == tipo && fabbisogno.getNumeroMinimoDipendenti() > 0)
                    .toList();
            TreeItem<RigaPiano> gruppo = tabella.getRoot().getChildren().get(indice);
            assertEquals(giorno, gruppo.getValue().giorno());
            List<Turno> righe = new ArrayList<>();
            List<TreeItem<RigaPiano>> servizi = gruppo.getChildren().stream().filter(riga -> !riga.isLeaf()).toList();
            assertEquals(List.of("Pranzo", "Cena"),
                    servizi.stream().map(riga -> riga.getValue().testo()).toList());
            for (TreeItem<RigaPiano> servizio : servizi) {
                for (TreeItem<RigaPiano> riga : servizio.getChildren()) {
                    Turno turno = riga.getValue().turno();
                    if (turno != null) {
                        assertTrue(servizio.getValue().testo().startsWith(turno.servizio().toString()));
                        righe.add(turno);
                    }
                }
            }
            assertEquals(attesi.size(), righe.size(), "Righe effettive per " + giorno);
            assertEquals(!attesi.isEmpty(), gruppo.isExpanded());
            for (Turno turno : righe) {
                assertEquals(giorno, turno.inizio().toLocalDate());
                Fabbisogno fabbisogno = attesi.stream().filter(riga -> riga.getServizio() == turno.servizio()
                        && riga.getRuolo() == turno.ruolo()).findFirst().orElseThrow();
                assertEquals(fabbisogno.getNumeroMinimoDipendenti(), turno.postiRichiesti());
                assertEquals(turno.postiRichiesti(), turno.assegnazioni().size() + turno.postiScoperti());
                var fascia = it.disagili.turnogiusto.model.OrariTurni.per(turno.ruolo(), turno.servizio());
                assertEquals(giorno.atTime(fascia.inizio()), turno.inizio());
                assertEquals(fascia.fineDelServizio(giorno), turno.fine());
            }
        }
    }

    @Test
    void apreLaGenerazioneDalMenuPrincipale() throws Exception {
        suJavaFx(() -> {
            BorderPane finestra = FXMLLoader.load(getClass().getResource("/it/disagili/turnogiusto/main-view.fxml"));
            MenuBar menu = (MenuBar) finestra.getTop();
            MenuItem voce = menu.getMenus().stream().filter(m -> m.getText().equals("Turni")).findFirst().orElseThrow()
                    .getItems().getFirst();
            voce.fire();
            StackPane centro = (StackPane) finestra.getCenter();
            assertInstanceOf(Button.class, centro.getChildren().getFirst().lookup("#genera"));
            return null;
        });
    }

    @Test
    void inviaDateOrariEFestiviEMostraAssegnatiScopertureEStatoDiBozza() throws Exception {
        GenerazioneTurniService servizio = new GenerazioneTurniService() {
            @Override
            public Esito generaPerRuolo(LocalDate lunedi, Set<LocalDate> festivi,
                               List<AssegnazioneTurno> confinanti) {
                assertEquals(LUNEDI, lunedi);
                assertEquals(Set.of(LUNEDI.plusDays(6)), festivi);
                assertEquals(List.of(), confinanti);
                AssegnazioneTurno assegnata = new AssegnazioneTurno(8, LUNEDI.plusDays(6).atTime(17, 0),
                        LUNEDI.plusWeeks(1).atTime(0, 30));
                Turno turno = new Turno(Servizio.CENA, Ruolo.CAMERIERE, assegnata.inizio(), assegnata.fine(), 2, List.of(assegnata));
                Turno scoperto = new Turno(Servizio.CENA, Ruolo.CUOCO, assegnata.inizio(), assegnata.fine(), 1, List.of());
                AssegnazioneTurno seconda = new AssegnazioneTurno(9, assegnata.inizio().minusDays(1), assegnata.fine().minusDays(1));
                Turno coperto = new Turno(Servizio.CENA, Ruolo.AIUTO_CUOCO, seconda.inizio(), seconda.fine(), 1, List.of(seconda));
                AssegnazioneTurno terza = new AssegnazioneTurno(10, lunedi.plusDays(6).atTime(10, 0),
                        lunedi.plusDays(6).atTime(16, 0));
                Turno pranzo = new Turno(Servizio.PRANZO, Ruolo.LAVAPIATTI, terza.inizio(), terza.fine(), 1, List.of(terza));
                return new Esito(new PianoSettimanale(lunedi, List.of(turno, scoperto, coperto, pranzo)),
                        Map.of(8, "Giulia Rossi", 9, "Luca Verdi", 10, "Ada Neri"), false, List.of());
            }
        };
        CountDownLatch mostrato = new CountDownLatch(1);
        FXMLLoader loader = suJavaFx(() -> {
            FXMLLoader schermata = carica(servizio);
            campoData(schermata).setValue(LUNEDI);
            FlowPane festivi = (FlowPane) schermata.getNamespace().get("festivi");
            ((CheckBox) festivi.getChildren().getLast()).setSelected(true);
            messaggio(schermata).textProperty().addListener((osservabile, prima, dopo) -> {
                if (!dopo.equals("Generazione in corso…")) mostrato.countDown();
            });
            genera(schermata).fire();
            return schermata;
        });

        assertTrue(mostrato.await(5, TimeUnit.SECONDS), "La bozza deve apparire nella tabella");
        suJavaFx(() -> {
            Parent radice = loader.getRoot();
            radice.applyCss();
            radice.layout();
            TreeTableView<RigaPiano> tabella = tabella(radice);
            assertEquals(7, tabella.getRoot().getChildren().size());
            assertEquals(15, tabella.getExpandedItemCount());
            TreeItem<RigaPiano> lunedi = tabella.getRoot().getChildren().getFirst();
            TreeItem<RigaPiano> sabato = tabella.getRoot().getChildren().get(5);
            TreeItem<RigaPiano> domenica = tabella.getRoot().getChildren().get(6);
            TreeItem<RigaPiano> pranzoSabato = sabato.getChildren().getFirst();
            TreeItem<RigaPiano> pranzoDomenica = domenica.getChildren().getFirst();
            TreeItem<RigaPiano> cenaDomenica = domenica.getChildren().getLast();
            assertTrue(sabato.isExpanded());
            assertTrue(domenica.isExpanded());
            assertFalse(pranzoSabato.isExpanded());
            assertTrue(pranzoDomenica.isExpanded());
            assertTrue(cenaDomenica.isExpanded());
            assertEquals(tabella.getTreeItemLevel(domenica) + 1, tabella.getTreeItemLevel(cenaDomenica));
            int primaRigaCena = tabella.getRow(cenaDomenica.getChildren().getFirst());
            TreeTableColumn<?, ?> assegnati = (TreeTableColumn<?, ?>) loader.getNamespace().get("assegnatiColonna");
            TreeTableColumn<?, ?> orario = (TreeTableColumn<?, ?>) loader.getNamespace().get("orarioColonna");
            TreeTableColumn<?, ?> scoperti = (TreeTableColumn<?, ?>) loader.getNamespace().get("scopertiColonna");
            assertEquals("Giulia Rossi", assegnati.getCellData(primaRigaCena));
            assertEquals("Nessuno", assegnati.getCellData(primaRigaCena + 1));
            assertTrue(orario.getCellData(primaRigaCena).toString().contains("30/11 00:30"));
            assertTrue(messaggio(loader).getText().contains("23/11/2026 al 29/11/2026"));
            assertTrue(messaggio(loader).getText().contains("Posti assegnati: 3. Posti scoperti: 2"));
            assertTrue(messaggio(loader).getText().contains("Monte ore e riposi da verificare"));
            verificaColoreDellaCella(tabella, scoperti, 1, Color.web("#FBE7E6"));
            verificaColoreDellaCella(tabella, scoperti, 0, Color.web("#E7F3E8"));
            // La cena si chiude da sola, poi il giorno può nascondere entrambi i servizi.
            cenaDomenica.setExpanded(false);
            assertEquals(13, tabella.getExpandedItemCount());
            assertTrue(pranzoDomenica.isExpanded());
            cenaDomenica.setExpanded(true);
            domenica.setExpanded(false);
            assertEquals(10, tabella.getExpandedItemCount());
            assertTrue(sabato.isExpanded());
            domenica.setExpanded(true);
            assertEquals(15, tabella.getExpandedItemCount());

            // Si possono aprire anche un singolo servizio e un'intera giornata senza turni.
            pranzoSabato.setExpanded(true);
            assertEquals("Servizio non previsto.", orario.getCellData(tabella.getRow(pranzoSabato.getChildren().getFirst())));
            assertFalse(lunedi.isExpanded());
            lunedi.setExpanded(true);
            assertEquals("Nessun servizio previsto.", orario.getCellData(tabella.getRow(lunedi.getChildren().getFirst())));
            assertEquals(List.of("Pranzo", "Cena"), lunedi.getChildren().stream()
                    .filter(riga -> !riga.isLeaf()).map(riga -> riga.getValue().testo()).toList());
            return null;
        });
    }

    @Test
    void giorniEServiziSiApronoDaTuttaLaBarraERestanoNeriAllaSelezione() throws Exception {
        suJavaFx(() -> {
            FXMLLoader loader = carica(new GenerazioneTurniService());
            Parent radice = loader.getRoot();
            TreeTableView<RigaPiano> tabella = tabella(radice);
            TreeItem<RigaPiano> root = new TreeItem<>();
            TreeItem<RigaPiano> giorno = new TreeItem<>(new RigaPiano(LUNEDI, "lunedì 23/11", null));
            TreeItem<RigaPiano> pranzo = new TreeItem<>(new RigaPiano(LUNEDI, "Pranzo 10:00 – 16:00", null));
            TreeItem<RigaPiano> cena = new TreeItem<>(new RigaPiano(LUNEDI, "Cena 17:00 – 00:30", null));
            Turno turno = new Turno(Servizio.PRANZO, Ruolo.CUOCO, LUNEDI.atTime(10, 0),
                    LUNEDI.atTime(16, 0), 1, List.of());
            pranzo.getChildren().add(new TreeItem<>(new RigaPiano(LUNEDI, "10:00 – 16:00", turno)));
            cena.getChildren().add(new TreeItem<>(new RigaPiano(LUNEDI, "Servizio non previsto.", null)));
            giorno.getChildren().addAll(List.of(pranzo, cena));
            root.getChildren().add(giorno);
            root.setExpanded(true);
            giorno.setExpanded(true);
            tabella.setRoot(root);

            Stage finestra = new Stage();
            finestra.setScene(radice.getScene());
            finestra.show();
            try {
                for (TreeItem<RigaPiano> voce : List.of(giorno, pranzo, cena)) {
                    for (String bersaglio : List.of("testo", "spazio", "freccia")) {
                        // Ogni bersaglio deve sia aprire sia chiudere, anche nei servizi vuoti.
                        for (int clic = 0; clic < 2; clic++) {
                            radice.applyCss();
                            radice.layout();
                            TreeTableRow<?> riga = rigaVisibile(tabella, voce);
                            TreeTableCell<?, ?> testo = (TreeTableCell<?, ?>) riga.lookupAll(".tree-table-cell").stream()
                                    .filter(nodo -> nodo instanceof TreeTableCell<?, ?> cella
                                            && cella.getTableColumn() == tabella.getTreeColumn())
                                    .findFirst().orElseThrow();
                            Node nodo = switch (bersaglio) {
                                case "testo" -> testo.lookup(".text");
                                case "freccia" -> riga.getDisclosureNode().lookup(".arrow");
                                default -> riga.lookupAll(".tree-table-cell").stream()
                                        .filter(cella -> cella instanceof TreeTableCell<?, ?> valore
                                                && valore.getTableColumn() == tabella.getColumns().getLast())
                                        .findFirst().orElseThrow();
                            };
                            assertNotNull(nodo, bersaglio);
                            boolean aperta = voce.isExpanded();
                            clicca(nodo, MouseButton.PRIMARY);
                            assertEquals(!aperta, voce.isExpanded(), bersaglio + " su " + voce.getValue().testo());
                            assertSame(voce, tabella.getSelectionModel().getSelectedItem());
                            radice.applyCss();
                            radice.layout();
                            assertEquals(Color.BLACK, testo.getTextFill(), "Testo selezionato su " + bersaglio);
                            assertEquals(Color.web(voce == giorno ? "#e8eff7" : "#f1f5fa"),
                                    riga.getBackground().getFills().getFirst().getFill());
                            genera(loader).requestFocus();
                            radice.applyCss();
                            assertEquals(Color.BLACK, testo.getTextFill(), "Testo selezionato senza focus");
                        }
                    }
                    boolean aperta = voce.isExpanded();
                    clicca(rigaVisibile(tabella, voce), MouseButton.SECONDARY);
                    assertEquals(aperta, voce.isExpanded());
                }
                pranzo.setExpanded(true);
                radice.applyCss();
                radice.layout();
                clicca(rigaVisibile(tabella, pranzo.getChildren().getFirst()), MouseButton.PRIMARY);
                assertTrue(giorno.isExpanded());
                assertTrue(pranzo.isExpanded());
            } finally {
                finestra.close();
            }
            return null;
        });
    }

    private TreeTableRow<?> rigaVisibile(TreeTableView<?> tabella, TreeItem<?> voce) {
        return tabella.lookupAll(".tree-table-row-cell").stream()
                .filter(nodo -> nodo instanceof TreeTableRow<?> riga && riga.getTreeItem() == voce)
                .map(nodo -> (TreeTableRow<?>) nodo).findFirst().orElseThrow();
    }

    /** Invia l'intera sequenza per rilevare anche eventuali doppi toggle della freccia nativa. */
    private void clicca(Node nodo, MouseButton pulsante) {
        var punto = nodo.localToScene(nodo.getBoundsInLocal().getCenterX(), nodo.getBoundsInLocal().getCenterY());
        for (var tipo : List.of(MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED)) {
            boolean premuto = tipo == MouseEvent.MOUSE_PRESSED;
            nodo.fireEvent(new MouseEvent(tipo, punto.getX(), punto.getY(), punto.getX(), punto.getY(), pulsante, 1,
                    false, false, false, false, premuto && pulsante == MouseButton.PRIMARY, false,
                    premuto && pulsante == MouseButton.SECONDARY, false, false, true,
                    new PickResult(nodo, punto.getX(), punto.getY())));
        }
    }

    /** Controlla il colore applicato da JavaFX alla cella che mostra il numero. */
    private void verificaColoreDellaCella(TreeTableView<?> tabella, TreeTableColumn<?, ?> colonna, int numero, Color sfondo) {
        for (Node nodo : tabella.lookupAll(".tree-table-cell")) {
            if (nodo instanceof TreeTableCell<?, ?> cella && cella.getTableColumn() == colonna
                    && !cella.isEmpty() && Integer.valueOf(numero).equals(cella.getItem())) {
                assertEquals(Integer.toString(numero), cella.getText());
                assertEquals(sfondo, cella.getBackground().getFills().getFirst().getFill());
                return;
            }
        }
        fail("Cella scoperture non trovata per il valore " + numero);
    }

    @SuppressWarnings("unchecked")
    private TreeTableView<RigaPiano> tabella(Parent radice) {
        return (TreeTableView<RigaPiano>) radice.lookup("#tabella");
    }

    @Test
    void ilCalendarioPermetteSoloIlLunediEAzzeraIFestiviQuandoLaSettimanaCambia() throws Exception {
        suJavaFx(() -> {
            FXMLLoader loader = carica(new GenerazioneTurniService());
            DatePicker data = campoData(loader);
            DateCell giorno = data.getDayCellFactory().call(data);
            giorno.updateItem(LUNEDI.plusDays(1), false);
            assertTrue(giorno.isDisabled());
            giorno.updateItem(LUNEDI, false);
            assertFalse(giorno.isDisabled());

            data.setValue(LUNEDI);
            FlowPane festivi = (FlowPane) loader.getNamespace().get("festivi");
            ((CheckBox) festivi.getChildren().getFirst()).setSelected(true);
            data.setValue(LUNEDI.plusWeeks(1));
            assertEquals(7, festivi.getChildren().size());
            CheckBox primoFestivo = (CheckBox) festivi.getChildren().getFirst();
            assertEquals(LUNEDI.plusWeeks(1), primoFestivo.getUserData());
            assertFalse(primoFestivo.isSelected());
            return null;
        });
    }

    @Test
    void mostraGliOrariFissiSenzaCampiModificabili() throws Exception {
        suJavaFx(() -> {
            FXMLLoader loader = carica(new GenerazioneTurniService());
            Parent radice = loader.getRoot();
            // Valori espliciti: il riepilogo deve mostrare tutte le fasce concordate.
            assertEquals("Pranzo\nCuoco: 10:00 – 15:30\nAiuto cuoco: 10:00 – 15:30"
                    + "\nCameriere: 11:30 – 16:00\nLavapiatti: 12:00 – 16:00",
                    ((Label) radice.lookup("#orarioPranzo")).getText());
            assertEquals("Cena\nCuoco: 17:00 – 00:00 (giorno dopo)"
                    + "\nAiuto cuoco: 17:00 – 00:00 (giorno dopo)"
                    + "\nCameriere: 18:00 – 00:30 (giorno dopo)"
                    + "\nLavapiatti: 19:00 – 00:30 (giorno dopo)",
                    ((Label) radice.lookup("#orarioCena")).getText());
            assertTrue(radice.lookupAll(".combo-box").isEmpty());
            return null;
        });
    }

    @Test
    void rifiutaUnaDataDiversaDaLunediPrimaDiInvocareIlService() throws Exception {
        suJavaFx(() -> {
            FXMLLoader loader = carica(new GenerazioneTurniService());
            campoData(loader).setValue(LUNEDI.plusDays(1));
            genera(loader).fire();
            assertTrue(messaggio(loader).getText().contains("Seleziona il lunedì"));
            return null;
        });
    }

    @Test
    void segnalaUnErroreDiLetturaEPermetteDiRiprovare() throws Exception {
        CountDownLatch mostrato = new CountDownLatch(1);
        FXMLLoader loader = suJavaFx(() -> {
            FXMLLoader schermata = carica(new GenerazioneTurniService() {
                @Override
                public Esito generaPerRuolo(LocalDate lunedi, Set<LocalDate> festivi,
                                   List<AssegnazioneTurno> confinanti) throws SQLException {
                    throw new SQLException("Database di prova spento");
                }
            });
            messaggio(schermata).textProperty().addListener((osservabile, prima, dopo) -> {
                if (dopo.startsWith("Lettura del database non riuscita")) mostrato.countDown();
            });
            genera(schermata).fire();
            return schermata;
        });
        assertTrue(mostrato.await(5, TimeUnit.SECONDS));
        assertFalse(suJavaFx(() -> genera(loader).isDisabled()));
    }

    /** Carica la schermata reale con un service controllato dal test. */
    // ================================================================ SALVA BOZZA

    /** Repository finto: ricorda cosa e' stato salvato, senza database. */
    private static class PianiFinti extends it.disagili.turnogiusto.repository.PianoRepository {
        PianoSettimanale salvato;
        Set<LocalDate> festiviSalvati;
        boolean giaPresente;

        @Override public java.util.Optional<PianoSettimanale> trova(LocalDate lunedi) {
            return giaPresente ? java.util.Optional.of(new PianoSettimanale(lunedi, List.of())) : java.util.Optional.empty();
        }
        @Override public void salva(PianoSettimanale piano, Set<LocalDate> festivi) {
            salvato = piano;
            festiviSalvati = festivi;
        }
    }

    private GenerazioneTurniService servizioConBozza() {
        return new GenerazioneTurniService() {
            @Override
            public Esito generaPerRuolo(LocalDate lunedi, Set<LocalDate> festivi, List<AssegnazioneTurno> confinanti) {
                AssegnazioneTurno a = new AssegnazioneTurno(8, lunedi.atTime(18, 0), lunedi.atTime(23, 0));
                Turno t = new Turno(Servizio.CENA, Ruolo.CAMERIERE, a.inizio(), a.fine(), 1, List.of(a));
                return new Esito(new PianoSettimanale(lunedi, List.of(t)), Map.of(8, "Giulia Rossi"), true, List.of());
            }
        };
    }

    /** Genera con giovedi' e venerdi' spuntati e preme "Salva bozza"; restituisce la schermata. */
    private FXMLLoader generaESalva(PianiFinti piani, boolean conferma) throws Exception {
        CountDownLatch mostrato = new CountDownLatch(1);
        FXMLLoader loader = suJavaFx(() -> {
            FXMLLoader schermata = new FXMLLoader(getClass().getResource("generazione-turni-view.fxml"));
            GenerazioneTurniController controller = new GenerazioneTurniController(servizioConBozza(), piani);
            controller.impostaConfermaSovrascrittura(() -> conferma);
            schermata.setControllerFactory(classe -> controller);
            Parent radice = schermata.load();
            new Scene(radice, 900, 720);
            campoData(schermata).setValue(LUNEDI);
            FlowPane festivi = (FlowPane) schermata.getNamespace().get("festivi");
            ((CheckBox) festivi.getChildren().get(3)).setSelected(true);
            ((CheckBox) festivi.getChildren().get(4)).setSelected(true);
            assertTrue(((javafx.scene.control.Button) schermata.getNamespace().get("salvaBozza")).isDisabled(),
                    "prima di generare non c'e' niente da salvare");
            messaggio(schermata).textProperty().addListener((osservabile, prima, dopo) -> {
                if (!dopo.equals("Generazione in corso…")) mostrato.countDown();
            });
            genera(schermata).fire();
            return schermata;
        });
        assertTrue(mostrato.await(5, TimeUnit.SECONDS));
        suJavaFx(() -> {
            ((javafx.scene.control.Button) loader.getNamespace().get("salvaBozza")).fire();
            return null;
        });
        return loader;
    }

    @Test
    void salvaBozzaSalvaIlPianoInsiemeAiFestiviSpuntati() throws Exception {
        PianiFinti piani = new PianiFinti();

        FXMLLoader loader = generaESalva(piani, true);

        assertNotNull(piani.salvato);
        assertEquals(LUNEDI, piani.salvato.inizioSettimana());
        assertEquals(Set.of(LUNEDI.plusDays(3), LUNEDI.plusDays(4)), piani.festiviSalvati);
        assertTrue(suJavaFx(() -> messaggio(loader).getText()).contains("Modifica piano"));
    }

    @Test
    void seLaSettimanaHaGiaUnPianoENonSiConfermaNonSalvaNiente() throws Exception {
        PianiFinti piani = new PianiFinti();
        piani.giaPresente = true;

        FXMLLoader loader = generaESalva(piani, false);

        assertNull(piani.salvato);
        assertTrue(suJavaFx(() -> messaggio(loader).getText()).contains("annullato"));
    }

    private FXMLLoader carica(GenerazioneTurniService servizio) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("generazione-turni-view.fxml"));
        loader.setControllerFactory(classe -> new GenerazioneTurniController(servizio));
        Parent radice = loader.load();
        // Con una Scene JavaFX applica gli stili e crea le celle della tabella.
        new Scene(radice, 900, 720);
        radice.applyCss();
        return loader;
    }

    private DatePicker campoData(FXMLLoader loader) {
        return (DatePicker) loader.getNamespace().get("settimana");
    }

    private Button genera(FXMLLoader loader) {
        return (Button) loader.getNamespace().get("genera");
    }

    private Label messaggio(FXMLLoader loader) {
        return (Label) loader.getNamespace().get("messaggio");
    }
}
