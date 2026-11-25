package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.TipoUtente;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.model.Utente;
import it.disagili.turnogiusto.service.MieiTurniService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static it.disagili.turnogiusto.ui.JavaFxTestSupport.suJavaFx;
import static org.junit.jupiter.api.Assertions.*;

/** La pagina "I miei turni" del dipendente, con un service finto: non serve il database. */
@ExtendWith(JavaFxTestSupport.class)
class MieiTurniControllerTest {

    private static final LocalDate LUNEDI = LocalDate.of(2026, 11, 30);
    private static final LocalDateTime INIZIO = LUNEDI.plusDays(5).atTime(18, 0); // sabato sera
    private static final Utente ANNA = new Utente(3, "anna.gialli", TipoUtente.DIPENDENTE, 5, "Anna Gialli", false);
    private static final Utente TITOLARE = new Utente(1, "titolare", TipoUtente.TITOLARE, null, "Titolare", false);

    private static class ServiceFinto extends MieiTurniService {
        MieiTurniService.Settimana risposta;
        boolean databaseSpento;
        Integer ultimoDipendente;
        LocalDate ultimoLunedi;

        @Override
        public Settimana turniDi(int dipendenteId, LocalDate lunedi) throws SQLException {
            ultimoDipendente = dipendenteId;
            ultimoLunedi = lunedi;
            if (databaseSpento) throw new SQLException("spento");
            return risposta;
        }
    }

    private static Turno cenaDelSabato() {
        LocalDateTime fine = INIZIO.plusHours(6);
        return new Turno(Servizio.CENA, Ruolo.CAMERIERE, INIZIO, fine, 5,
                List.of(new AssegnazioneTurno(5, INIZIO, fine)));
    }

    private Parent apri(ServiceFinto service, Utente utente) throws Exception {
        MieiTurniController controller = new MieiTurniController(service, utente);
        return suJavaFx(() -> {
            FXMLLoader caricatore = new FXMLLoader(getClass().getResource("/it/disagili/turnogiusto/ui/miei-turni-view.fxml"));
            caricatore.setControllerFactory(tipo -> controller);
            return caricatore.load();
        });
    }

    private static String testo(Parent radice, String id) throws Exception {
        return suJavaFx(() -> ((Label) radice.lookup("#" + id)).getText());
    }

    private static String cella(Parent radice, String idColonna, int riga) throws Exception {
        return suJavaFx(() -> {
            @SuppressWarnings("unchecked")
            TableView<Turno> tabella = (TableView<Turno>) radice.lookup("#tabella");
            TableColumn<Turno, ?> colonna = tabella.getColumns().stream()
                    .filter(c -> idColonna.equals(c.getId())).findFirst().orElseThrow();
            return String.valueOf(colonna.getCellData(riga));
        });
    }

    @Test
    void aprendoLaPaginaSiVedonoISuoiTurniELeOre() throws Exception {
        ServiceFinto service = new ServiceFinto();
        service.risposta = new MieiTurniService.Settimana(true, List.of(cenaDelSabato()), Duration.ofHours(6));

        Parent radice = apri(service, ANNA);

        assertEquals(5, service.ultimoDipendente); // l'ID e' quello di Anna, preso dall'accesso
        assertEquals("Cena", cella(radice, "servizioColonna", 0));
        assertEquals("18:00 - 00:00", cella(radice, "orarioColonna", 0));
        assertEquals("Cameriere", cella(radice, "ruoloColonna", 0));
        assertEquals("Ore pianificate: 6 h", testo(radice, "totale"));
        assertTrue(testo(radice, "messaggio").contains("tuoi turni"));
    }

    @Test
    void unGiornoQualsiasiDiventaIlLunediDellaSettimana() throws Exception {
        ServiceFinto service = new ServiceFinto();
        service.risposta = new MieiTurniService.Settimana(false, List.of(), Duration.ZERO);
        Parent radice = apri(service, ANNA);

        suJavaFx(() -> {
            ((DatePicker) radice.lookup("#settimana")).setValue(LocalDate.of(2026, 12, 3)); // giovedi'
            ((Button) radice.lookup("#mostra")).fire();
            return null;
        });

        assertEquals(LUNEDI, service.ultimoLunedi);
        assertTrue(testo(radice, "intervallo").contains("30/11/2026"));
        assertTrue(testo(radice, "intervallo").contains("06/12/2026"));
    }

    @Test
    void seIlTitolareNonHaPreparatoIlPianoSiDice() throws Exception {
        ServiceFinto service = new ServiceFinto();
        service.risposta = new MieiTurniService.Settimana(false, List.of(), Duration.ZERO);

        Parent radice = apri(service, ANNA);

        assertTrue(testo(radice, "messaggio").contains("non ha ancora preparato il piano"));
    }

    @Test
    void seNonCiSonoTurniInQuellaSettimanaSiDice() throws Exception {
        ServiceFinto service = new ServiceFinto();
        service.risposta = new MieiTurniService.Settimana(true, List.of(), Duration.ZERO);

        Parent radice = apri(service, ANNA);

        assertTrue(testo(radice, "messaggio").contains("non hai turni"));
    }

    @Test
    void seIlDatabaseESpentoSiVedeUnMessaggioChiaro() throws Exception {
        ServiceFinto service = new ServiceFinto();
        service.databaseSpento = true;

        Parent radice = apri(service, ANNA);

        assertTrue(testo(radice, "messaggio").contains("database"));
        assertTrue(suJavaFx(() -> ((Label) radice.lookup("#messaggio")).getStyleClass().contains("errore")));
    }

    @Test
    void ilTitolareONessunoNonUsanoQuestaPagina() throws Exception {
        for (Utente chi : new Utente[]{TITOLARE, null}) {
            ServiceFinto service = new ServiceFinto();
            service.risposta = new MieiTurniService.Settimana(true, List.of(cenaDelSabato()), Duration.ofHours(6));

            Parent radice = apri(service, chi);

            assertNull(service.ultimoDipendente); // il service non viene nemmeno interrogato
            assertTrue(suJavaFx(() -> ((Button) radice.lookup("#mostra")).isDisabled()));
            assertTrue(testo(radice, "messaggio").contains("riservata ai dipendenti"));
        }
    }
}
