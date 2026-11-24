package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.controller.MainController;
import it.disagili.turnogiusto.model.TipoUtente;
import it.disagili.turnogiusto.model.Utente;
import it.disagili.turnogiusto.service.SessioneUtente;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Method;
import java.util.List;

import static it.disagili.turnogiusto.ui.JavaFxTestSupport.suJavaFx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chi vede cosa nella finestra principale. E' il test del motivo per cui esiste il login:
 * un dipendente non deve poter arrivare alla creazione dei turni, ne' dal menu ne' in altro modo.
 */
@ExtendWith(JavaFxTestSupport.class)
class MainControllerPermessiTest {

    private static final Utente TITOLARE = new Utente(1, "titolare", TipoUtente.TITOLARE, null, "Titolare", false);
    private static final Utente PART_TIME = new Utente(2, "sara.neri", TipoUtente.DIPENDENTE, 4, "Sara Neri", true);
    private static final Utente TEMPO_PIENO = new Utente(3, "anna.gialli", TipoUtente.DIPENDENTE, 5, "Anna Gialli", false);

    private BorderPane radice;
    private MainController controller;

    @AfterEach
    void esci() {
        SessioneUtente.esci();
    }

    // apre la finestra principale come la aprirebbe l'applicazione dopo l'accesso (utente null = nessun accesso)
    private void apriComplessivamente(Utente utente) throws Exception {
        SessioneUtente.esci();
        if (utente != null) {
            SessioneUtente.accedi(utente);
        }
        suJavaFx(() -> {
            FXMLLoader caricatore = new FXMLLoader(getClass().getResource("/it/disagili/turnogiusto/main-view.fxml"));
            radice = caricatore.load();
            controller = caricatore.getController();
            return null;
        });
    }

    private List<String> menu() throws Exception {
        return suJavaFx(() -> ((MenuBar) radice.getTop()).getMenus().stream().map(Menu::getText).toList());
    }

    // prova ad aprire una sezione chiamando direttamente il gestore del menu (come farebbe un clic)
    private String provaAdAprire(String gestore) throws Exception {
        return suJavaFx(() -> {
            Method metodo = MainController.class.getDeclaredMethod(gestore);
            metodo.setAccessible(true);
            metodo.invoke(controller);
            StackPane contenuto = (StackPane) radice.lookup("#contenuto");
            assertEquals(1, contenuto.getChildren().size());
            return contenuto.getChildren().get(0) instanceof Label etichetta ? etichetta.getText() : "SCHERMATA APERTA";
        });
    }

    @Test
    void ilTitolareVedeLeSezioniDiGestione() throws Exception {
        apriComplessivamente(TITOLARE);

        assertEquals(List.of("Dipendenti", "Disponibilità", "Fabbisogno", "Turni", "Account"), menu());
        // nel menu Turni ci sono sia la generazione sia la modifica
        int voci = suJavaFx(() -> ((MenuBar) radice.getTop()).getMenus().stream()
                .filter(m -> m.getText().equals("Turni")).findFirst().orElseThrow().getItems().size());
        assertEquals(2, voci);
    }

    @Test
    void ilDipendentePartTimeVedeSoloIlSuoMenu() throws Exception {
        apriComplessivamente(PART_TIME);

        assertEquals(List.of("I miei turni", "Le mie disponibilità", "Account"), menu());
    }

    @Test
    void ilDipendenteATempoPienoVedeSoloITuoiTurni() throws Exception {
        apriComplessivamente(TEMPO_PIENO);

        assertEquals(List.of("I miei turni", "Account"), menu());
    }

    @Test
    void ilDipendenteNonVedeLaCreazioneDeiTurni() throws Exception {
        for (Utente dipendente : new Utente[]{PART_TIME, TEMPO_PIENO}) {
            apriComplessivamente(dipendente);

            List<String> voci = menu();
            assertFalse(voci.contains("Turni"));
            assertFalse(voci.contains("Dipendenti"));
            assertFalse(voci.contains("Fabbisogno"));
        }
    }

    @Test
    void ilDipendenteNonPuoAprireUnaSezioneVietataNemmenoSeLaRichiamaDirettamente() throws Exception {
        // anche senza la voce di menu, il controllo viene rifatto prima di aprire la schermata
        for (Utente dipendente : new Utente[]{PART_TIME, TEMPO_PIENO}) {
            apriComplessivamente(dipendente);

            for (String gestore : List.of("apriGenerazioneTurni", "apriModificaPiano", "apriDipendenti",
                    "apriFabbisogno", "apriDisponibilita")) {
                assertEquals("Non hai i permessi per aprire questa sezione.", provaAdAprire(gestore), gestore);
            }
        }
    }

    @Test
    void ilDipendenteATempoPienoNonPuoGestireDisponibilita() throws Exception {
        apriComplessivamente(TEMPO_PIENO);

        assertEquals("Non hai i permessi per aprire questa sezione.", provaAdAprire("apriLeMieDisponibilita"));
    }

    @Test
    void ilTitolareNonHaLePagineDelDipendente() throws Exception {
        apriComplessivamente(TITOLARE);

        assertEquals("Non hai i permessi per aprire questa sezione.", provaAdAprire("apriMieiTurni"));
        assertEquals("Non hai i permessi per aprire questa sezione.", provaAdAprire("apriLeMieDisponibilita"));
    }

    @Test
    void senzaAccessoNonSiApreNienteEIlMenuHaSoloLAccount() throws Exception {
        apriComplessivamente(null);

        assertEquals(List.of("Account"), menu());
        // il benvenuto si legge prima di aprire altro: dopo un tentativo l'area centrale cambia contenuto
        assertTrue(suJavaFx(() -> ((Label) radice.lookup("#benvenuto")).getText()).contains("Accesso non effettuato"));
        assertEquals("Non hai i permessi per aprire questa sezione.", provaAdAprire("apriGenerazioneTurni"));
        assertEquals("Non hai i permessi per aprire questa sezione.", provaAdAprire("apriMieiTurni"));
    }

    @Test
    void ilMenuAccountDiceChiECollegato() throws Exception {
        apriComplessivamente(PART_TIME);

        String testo = suJavaFx(() -> ((MenuBar) radice.getTop()).getMenus().stream()
                .filter(m -> m.getText().equals("Account")).findFirst().orElseThrow().getItems().get(0).getText());
        assertEquals("Collegato come Sara Neri (Dipendente)", testo);
        assertTrue(suJavaFx(() -> ((Label) radice.lookup("#benvenuto")).getText()).contains("Benvenuto, Sara Neri"));
    }

    @Test
    void uscireSvuotaLaSessione() throws Exception {
        apriComplessivamente(TITOLARE);
        assertTrue(SessioneUtente.corrente().isPresent());

        suJavaFx(() -> {
            Method esci = MainController.class.getDeclaredMethod("onEsci");
            esci.setAccessible(true);
            esci.invoke(controller);
            return null;
        });

        assertTrue(SessioneUtente.corrente().isEmpty());
    }
}
