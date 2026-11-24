package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.service.ModificaPianoException;
import it.disagili.turnogiusto.service.ModificaPianoService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static it.disagili.turnogiusto.ui.JavaFxTestSupport.suJavaFx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * US-06B: la schermata di modifica del piano. Non serve il database: il service e' sostituito
 * da una versione finta che tiene il piano in memoria.
 */
@ExtendWith(JavaFxTestSupport.class)
class ModificaPianoControllerTest {

    private static final LocalDate LUNEDI = LocalDate.of(2026, 11, 30);
    private static final LocalDateTime INIZIO_CENA = LUNEDI.plusDays(5).atTime(18, 0); // sabato
    private static final LocalDateTime FINE_CENA = INIZIO_CENA.plusHours(6);
    private static final int ANNA = 1, SARA = 2, LUCA = 3;

    private ServiceFinto service;
    private Parent radice;

    // ---------------------------------------------------------------- service finto
    private static class ServiceFinto extends ModificaPianoService {
        PianoSettimanale archivio;                 // il piano "salvato" (null = nessun piano)
        PianoSettimanale salvato;                  // l'ultimo piano passato a salva()
        PianoSettimanale generato;                 // il piano che generaPiano() restituisce
        List<String> violazioni = new ArrayList<>();
        ModificaPianoException erroreSostituzione;
        boolean databaseSpento;

        @Override
        public Optional<PianoSettimanale> carica(LocalDate lunedi) throws SQLException {
            if (databaseSpento) throw new SQLException("spento");
            return Optional.ofNullable(archivio);
        }

        @Override
        public Map<Integer, String> nomiDipendenti(LocalDate giorno) {
            return Map.of(ANNA, "Anna Rossi", SARA, "Sara Neri", LUCA, "Luca Verdi");
        }

        @Override
        public Esito verifica(PianoSettimanale piano) {
            return new Esito(piano, violazioni, true);
        }

        @Override
        public List<Dipendente> candidati(PianoSettimanale piano, Turno riga) {
            // nel finto e' candidato solo Luca, che non e' ancora nella riga
            boolean giaDentro = riga.assegnazioni().stream().anyMatch(a -> a.dipendenteId() == LUCA);
            return giaDentro ? List.of() : List.of(persona(LUCA, "Luca", "Verdi"));
        }

        @Override
        public Esito sostituisci(PianoSettimanale piano, Turno riga, int vecchioId, int nuovoId)
                throws ModificaPianoException {
            if (erroreSostituzione != null) throw erroreSostituzione;
            return new Esito(cambia(piano, riga, vecchioId, nuovoId), violazioni, true);
        }

        @Override
        public Esito assegnaAlPostoScoperto(PianoSettimanale piano, Turno riga, int nuovoId) {
            return new Esito(cambia(piano, riga, null, nuovoId), violazioni, true);
        }

        @Override
        public void salva(PianoSettimanale piano) {
            salvato = piano;
            archivio = piano; // dopo il salvataggio il "database" contiene questo piano
        }

        @Override
        public Esito generaPiano(LocalDate lunedi) {
            return new Esito(generato, violazioni, true);
        }

        // sostituisce (o aggiunge) un'assegnazione nella riga indicata
        private static PianoSettimanale cambia(PianoSettimanale piano, Turno riga, Integer vecchioId, int nuovoId) {
            List<Turno> turni = new ArrayList<>();
            for (Turno turno : piano.turni()) {
                if (turno.inizio().equals(riga.inizio()) && turno.ruolo() == riga.ruolo()) {
                    List<AssegnazioneTurno> assegnazioni = new ArrayList<>();
                    for (AssegnazioneTurno a : turno.assegnazioni()) {
                        boolean daSostituire = vecchioId != null && a.dipendenteId() == vecchioId;
                        assegnazioni.add(daSostituire ? new AssegnazioneTurno(nuovoId, turno.inizio(), turno.fine()) : a);
                    }
                    if (vecchioId == null) assegnazioni.add(new AssegnazioneTurno(nuovoId, turno.inizio(), turno.fine()));
                    turni.add(new Turno(turno.servizio(), turno.ruolo(), turno.inizio(), turno.fine(),
                            turno.postiRichiesti(), assegnazioni));
                } else {
                    turni.add(turno);
                }
            }
            return new PianoSettimanale(piano.inizioSettimana(), turni);
        }
    }

    private static Dipendente persona(int id, String nome, String cognome) {
        Contratto contratto = new Contratto(id, TipoContratto.FULL_TIME, 40, new BigDecimal("10.00"), LocalDate.of(2026, 1, 1));
        return new Dipendente(id, nome, cognome, Ruolo.CAMERIERE, contratto);
    }

    // sabato a cena: 3 camerieri richiesti, Anna e Sara assegnate (1 posto scoperto)
    private static PianoSettimanale pianoDiEsempio() {
        List<AssegnazioneTurno> assegnati = List.of(
                new AssegnazioneTurno(ANNA, INIZIO_CENA, FINE_CENA),
                new AssegnazioneTurno(SARA, INIZIO_CENA, FINE_CENA));
        Turno cena = new Turno(Servizio.CENA, Ruolo.CAMERIERE, INIZIO_CENA, FINE_CENA, 3, assegnati);
        return new PianoSettimanale(LUNEDI, List.of(cena));
    }

    // ---------------------------------------------------------------- preparazione
    @BeforeEach
    void prepara() throws Exception {
        service = new ServiceFinto();
        service.archivio = pianoDiEsempio();
        ModificaPianoController controller = new ModificaPianoController(service);
        controller.impostaConfermaScarto(() -> true); // nei test non si apre nessuna finestra di conferma
        radice = suJavaFx(() -> {
            FXMLLoader caricatore = new FXMLLoader(getClass().getResource(
                    "/it/disagili/turnogiusto/ui/modifica-piano-view.fxml"));
            caricatore.setControllerFactory(tipo -> controller);
            return caricatore.load();
        });
    }

    // piccoli aiuti per leggere i controlli della schermata
    @SuppressWarnings("unchecked")
    private <T> T nodo(String id) {
        return (T) radice.lookup("#" + id);
    }

    private void premi(String idPulsante) throws Exception {
        suJavaFx(() -> {
            Button pulsante = nodo(idPulsante);
            pulsante.fire();
            return null;
        });
    }

    private String testo(String idEtichetta) throws Exception {
        return suJavaFx(() -> ((Label) nodo(idEtichetta)).getText());
    }

    // Le colonne non sono nodi della scena: si cercano nella tabella, per fx:id.
    private String cella(String idColonna, int riga) throws Exception {
        return suJavaFx(() -> {
            TableView<Turno> tabella = nodo("tabella");
            TableColumn<Turno, ?> colonna = tabella.getColumns().stream()
                    .filter(c -> idColonna.equals(c.getId()))
                    .findFirst()
                    .orElseThrow();
            return String.valueOf(colonna.getCellData(riga));
        });
    }

    private void selezionaPrimaRiga() throws Exception {
        suJavaFx(() -> {
            TableView<Turno> tabella = nodo("tabella");
            tabella.getSelectionModel().select(0);
            return null;
        });
    }

    // ================================================================ CARICARE

    @Test
    void caricareMostraIlPianoConINomiEIControlli() throws Exception {
        service.violazioni.add("Anna Rossi: 10 ore di riposo, minimo 11");
        // un giovedi' qualsiasi (3 dicembre): il programma lo trasforma nel lunedi' della sua settimana (30 novembre)
        suJavaFx(() -> {
            DatePicker data = nodo("settimana");
            data.setValue(LocalDate.of(2026, 12, 3));
            return null;
        });

        premi("carica");

        assertTrue(cella("giornoColonna", 0).contains("05/12")); // sabato 5 dicembre
        assertEquals("Cena", cella("servizioColonna", 0));
        assertEquals("18:00 - 00:00", cella("orarioColonna", 0));
        assertEquals("Cameriere", cella("ruoloColonna", 0));
        assertEquals("3", cella("richiestiColonna", 0));
        assertEquals("1", cella("scopertiColonna", 0));
        assertEquals("Anna Rossi, Sara Neri", cella("assegnatiColonna", 0));
        assertEquals(List.of("Anna Rossi: 10 ore di riposo, minimo 11"),
                suJavaFx(() -> ((ListView<String>) nodo("violazioni")).getItems()));
        assertTrue(testo("intervallo").contains("30/11/2026")); // lunedi'
        assertTrue(testo("intervallo").contains("06/12/2026")); // domenica
    }

    @Test
    void conUnGiornoQualsiasiSiCaricaLaSettimanaIntera() throws Exception {
        // il DatePicker parte da oggi: qualunque giorno e' valido, il service riceve il lunedi'
        premi("carica");

        assertTrue(testo("messaggio").contains("Piano caricato"));
    }

    @Test
    void senzaUnPianoSalvatoCompareIlPulsanteGenera() throws Exception {
        service.archivio = null;

        premi("carica");

        assertTrue(suJavaFx(() -> ((Button) nodo("genera")).isVisible()));
        assertTrue(testo("messaggio").contains("non c'è ancora un piano"));
    }

    @Test
    void generareUnPianoDiBaseLoMostraEAbilitaIlSalvataggio() throws Exception {
        service.archivio = null;
        service.generato = pianoDiEsempio();
        premi("carica");

        premi("genera");

        assertEquals("Anna Rossi, Sara Neri", cella("assegnatiColonna", 0));
        assertFalse(suJavaFx(() -> ((Button) nodo("salva")).isDisabled())); // c'e' qualcosa da salvare
        assertNull(service.salvato); // ma non e' stato salvato da solo
    }

    @Test
    void seIlDatabaseESpentoSiVedeUnMessaggioChiaro() throws Exception {
        service.databaseSpento = true;

        premi("carica");

        assertTrue(testo("messaggio").contains("database"));
        assertTrue(suJavaFx(() -> ((Label) nodo("messaggio")).getStyleClass().contains("errore")));
    }

    // ================================================================ MODIFICARE

    @Test
    void sceglieUnaRigaSiRiempionoIMenuConLePersoneGiuste() throws Exception {
        premi("carica");

        selezionaPrimaRiga();

        List<String> escono = suJavaFx(() -> ((ComboBox<ModificaPianoController.Persona>) nodo("daSostituire"))
                .getItems().stream().map(ModificaPianoController.Persona::nome).toList());
        List<String> entrano = suJavaFx(() -> ((ComboBox<ModificaPianoController.Persona>) nodo("nuovaPersona"))
                .getItems().stream().map(ModificaPianoController.Persona::nome).toList());
        assertEquals(List.of("Anna Rossi", "Sara Neri"), escono);
        assertEquals(List.of("Luca Verdi"), entrano);
        // i pulsanti restano spenti finche' non si sceglie chi esce e chi entra
        assertTrue(suJavaFx(() -> ((Button) nodo("sostituisci")).isDisabled()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sostituireUnaPersonaAggiornaLaTabellaEIlSalvataggioEAttivo() throws Exception {
        premi("carica");
        selezionaPrimaRiga();

        suJavaFx(() -> {
            ComboBox<ModificaPianoController.Persona> esce = nodo("daSostituire");
            ComboBox<ModificaPianoController.Persona> entra = nodo("nuovaPersona");
            esce.setValue(esce.getItems().get(0));   // Anna
            entra.setValue(entra.getItems().get(0)); // Luca
            return null;
        });
        assertFalse(suJavaFx(() -> ((Button) nodo("sostituisci")).isDisabled()));

        premi("sostituisci");

        assertEquals("Luca Verdi, Sara Neri", cella("assegnatiColonna", 0));
        assertTrue(testo("messaggio").contains("non sono ancora salvate"));
        assertFalse(suJavaFx(() -> ((Button) nodo("salva")).isDisabled()));
        assertNull(service.salvato); // finche' non si preme Salva, il database non cambia
    }

    @Test
    @SuppressWarnings("unchecked")
    void salvareScriveIlPianoModificatoEDisattivaIlPulsante() throws Exception {
        premi("carica");
        selezionaPrimaRiga();
        suJavaFx(() -> {
            ComboBox<ModificaPianoController.Persona> esce = nodo("daSostituire");
            ComboBox<ModificaPianoController.Persona> entra = nodo("nuovaPersona");
            esce.setValue(esce.getItems().get(0));
            entra.setValue(entra.getItems().get(0));
            return null;
        });
        premi("sostituisci");

        premi("salva");

        assertNotNull(service.salvato);
        assertTrue(service.salvato.assegnazioni().stream().anyMatch(a -> a.dipendenteId() == LUCA));
        assertTrue(suJavaFx(() -> ((Button) nodo("salva")).isDisabled()));
        assertTrue(testo("messaggio").contains("Piano salvato"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void seIlServiceRifiutaLaModificaSiMostraIlMotivoENullaCambia() throws Exception {
        premi("carica");
        selezionaPrimaRiga();
        service.erroreSostituzione = new ModificaPianoException("Luca Verdi ha già un altro turno che si sovrappone a questo");
        suJavaFx(() -> {
            ComboBox<ModificaPianoController.Persona> esce = nodo("daSostituire");
            ComboBox<ModificaPianoController.Persona> entra = nodo("nuovaPersona");
            esce.setValue(esce.getItems().get(0));
            entra.setValue(entra.getItems().get(0));
            return null;
        });

        premi("sostituisci");

        assertEquals("Luca Verdi ha già un altro turno che si sovrappone a questo", testo("messaggio"));
        assertTrue(suJavaFx(() -> ((Label) nodo("messaggio")).getStyleClass().contains("errore")));
        assertEquals("Anna Rossi, Sara Neri", cella("assegnatiColonna", 0)); // il piano non e' cambiato
        assertTrue(suJavaFx(() -> ((Button) nodo("salva")).isDisabled()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sipuCoprireUnPostoScoperto() throws Exception {
        premi("carica");
        selezionaPrimaRiga();
        suJavaFx(() -> {
            ComboBox<ModificaPianoController.Persona> entra = nodo("nuovaPersona");
            entra.setValue(entra.getItems().get(0)); // Luca
            return null;
        });
        assertFalse(suJavaFx(() -> ((Button) nodo("copriPosto")).isDisabled())); // la riga ha 1 posto scoperto

        premi("copriPosto");

        assertEquals("Anna Rossi, Sara Neri, Luca Verdi", cella("assegnatiColonna", 0));
        assertEquals("0", cella("scopertiColonna", 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void annullareRipristinaIlPianoSalvato() throws Exception {
        premi("carica");
        selezionaPrimaRiga();
        suJavaFx(() -> {
            ComboBox<ModificaPianoController.Persona> esce = nodo("daSostituire");
            ComboBox<ModificaPianoController.Persona> entra = nodo("nuovaPersona");
            esce.setValue(esce.getItems().get(0));
            entra.setValue(entra.getItems().get(0));
            return null;
        });
        premi("sostituisci");

        premi("annulla");

        assertEquals("Anna Rossi, Sara Neri", cella("assegnatiColonna", 0));
        assertTrue(suJavaFx(() -> ((Button) nodo("salva")).isDisabled()));
        assertTrue(testo("messaggio").contains("Modifiche annullate"));
    }
}
