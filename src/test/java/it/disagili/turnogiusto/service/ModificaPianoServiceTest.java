package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.repository.DipendenteRepository;
import it.disagili.turnogiusto.repository.PianoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** US-06B: modificare il piano. Non serve il database: repository e controlli sono finti. */
class ModificaPianoServiceTest {

    private static final LocalDate LUNEDI = LocalDate.of(2026, 11, 30);
    private static final LocalDateTime INIZIO_CENA = LUNEDI.plusDays(5).atTime(18, 0); // sabato
    private static final LocalDateTime FINE_CENA = INIZIO_CENA.plusHours(6);

    // id dei dipendenti di prova
    private static final int ANNA = 1, SARA = 2, LUCA = 3, PAOLO = 4, GIULIA = 5, MARCO = 6;

    private PianoRepositoryFinto piani;
    private List<String> violazioniDelControllo;
    private boolean controlloCollegato;
    private boolean controlloDaDatiIncompleti;
    private ModificaPianoService service;

    // ---------------------------------------------------------------- repository e controlli finti
    private static class PianoRepositoryFinto extends PianoRepository {
        PianoSettimanale salvato;
        PianoSettimanale daCaricare;
        List<AssegnazioneTurno> confinanti = List.of();

        @Override public Optional<PianoSettimanale> trova(LocalDate lunedi) { return Optional.ofNullable(daCaricare); }
        @Override public void salva(PianoSettimanale piano) { salvato = piano; }
        @Override public List<AssegnazioneTurno> trovaConfinanti(LocalDate lunedi) { return confinanti; }
    }

    private static Dipendente persona(int id, String nome, Ruolo ruolo, boolean conContratto) {
        Contratto contratto = conContratto
                ? new Contratto(id, TipoContratto.FULL_TIME, 40, new BigDecimal("10.00"), LocalDate.of(2026, 1, 1))
                : null;
        return new Dipendente(id, nome, "Prova", ruolo, contratto);
    }

    @BeforeEach
    void prepara() {
        piani = new PianoRepositoryFinto();
        violazioniDelControllo = new ArrayList<>();
        controlloCollegato = true;
        controlloDaDatiIncompleti = false;

        DipendenteRepository dipendenti = new DipendenteRepository() {
            @Override
            public List<Dipendente> trovaTutti(LocalDate data) {
                return List.of(
                        persona(ANNA, "Anna", Ruolo.CAMERIERE, true),
                        persona(SARA, "Sara", Ruolo.CAMERIERE, true),
                        persona(LUCA, "Luca", Ruolo.CAMERIERE, true),
                        persona(PAOLO, "Paolo", Ruolo.LAVAPIATTI, true),
                        persona(GIULIA, "Giulia", Ruolo.CAMERIERE, false),   // senza contratto valido
                        persona(MARCO, "Marco", Ruolo.CAMERIERE, true));
            }
        };

        ModificaPianoService.VerificaPiano controllo = (piano, confinanti) -> {
            if (!controlloCollegato) {
                throw new IllegalStateException("Il controllo dei vincoli US07 non è ancora collegato.");
            }
            if (controlloDaDatiIncompleti) {
                throw new IllegalArgumentException("manca il contratto di un dipendente");
            }
            return violazioniDelControllo;
        };
        service = new ModificaPianoService(piani, dipendenti, controllo);
    }

    // ---------------------------------------------------------------- dati di prova
    private static Turno riga(Servizio servizio, Ruolo ruolo, LocalDateTime inizio, LocalDateTime fine,
                              int posti, int... assegnati) {
        List<AssegnazioneTurno> assegnazioni = new ArrayList<>();
        for (int id : assegnati) {
            assegnazioni.add(new AssegnazioneTurno(id, inizio, fine));
        }
        return new Turno(servizio, ruolo, inizio, fine, posti, assegnazioni);
    }

    // sabato a cena: 3 camerieri richiesti, Anna e Sara assegnate (1 posto scoperto)
    private static Turno cenaDelSabato() {
        return riga(Servizio.CENA, Ruolo.CAMERIERE, INIZIO_CENA, FINE_CENA, 3, ANNA, SARA);
    }

    private static PianoSettimanale piano(Turno... righe) {
        return new PianoSettimanale(LUNEDI, List.of(righe));
    }

    private static List<Integer> assegnatiDi(PianoSettimanale piano, int indiceRiga) {
        return piano.turni().get(indiceRiga).assegnazioni().stream().map(AssegnazioneTurno::dipendenteId).toList();
    }

    // ================================================================ SOSTITUIRE

    @Test
    void sostituireUnAssegnatoCambiaSoloQuelPosto() throws Exception {
        PianoSettimanale prima = piano(cenaDelSabato());

        ModificaPianoService.Esito esito = service.sostituisci(prima, cenaDelSabato(), ANNA, LUCA);

        assertEquals(List.of(LUCA, SARA), assegnatiDi(esito.piano(), 0));
        assertEquals(List.of(ANNA, SARA), assegnatiDi(prima, 0)); // il piano di partenza non cambia
        assertTrue(esito.controlloEseguito());
    }

    @Test
    void leViolazioniDeiControlliSiMostranoMaNonBloccanoLaModifica() throws Exception {
        violazioniDelControllo.add("Dipendente " + LUCA + ": 10 ore di riposo, minimo 11");

        ModificaPianoService.Esito esito = service.sostituisci(piano(cenaDelSabato()), cenaDelSabato(), ANNA, LUCA);

        // "Dipendente 3" diventa il nome della persona
        assertEquals(List.of("Luca Prova: 10 ore di riposo, minimo 11"), esito.violazioni());
        assertTrue(assegnatiDi(esito.piano(), 0).contains(LUCA)); // la modifica c'e' comunque
    }

    @Test
    void unaViolazioneConUnAltroFormatoResta() throws Exception {
        violazioniDelControllo.add("Troppi part-time nel turno");

        ModificaPianoService.Esito esito = service.sostituisci(piano(cenaDelSabato()), cenaDelSabato(), ANNA, LUCA);

        assertEquals(List.of("Troppi part-time nel turno"), esito.violazioni());
    }

    @Test
    void seIControlliRifiutanoIDatiLaModificaSiApplicaEIlMotivoSiVede() throws Exception {
        controlloDaDatiIncompleti = true;

        ModificaPianoService.Esito esito = service.sostituisci(piano(cenaDelSabato()), cenaDelSabato(), ANNA, LUCA);

        assertFalse(esito.controlloEseguito());
        assertEquals(1, esito.violazioni().size());
        assertTrue(esito.violazioni().get(0).contains("Controlli non eseguibili"));
        assertTrue(assegnatiDi(esito.piano(), 0).contains(LUCA));
    }

    @Test
    void sipuControllareUnPianoSenzaModificarlo() throws Exception {
        PianoSettimanale esistente = piano(cenaDelSabato());

        ModificaPianoService.Esito esito = service.verifica(esistente);

        assertSame(esistente, esito.piano());
        assertTrue(esito.controlloEseguito());
    }

    @Test
    void senzaIControlliCollegatiLaModificaSiApplicaEloDichiara() throws Exception {
        controlloCollegato = false;

        ModificaPianoService.Esito esito = service.sostituisci(piano(cenaDelSabato()), cenaDelSabato(), ANNA, LUCA);

        assertFalse(esito.controlloEseguito());
        assertTrue(esito.violazioni().isEmpty());
        assertTrue(assegnatiDi(esito.piano(), 0).contains(LUCA));
    }

    // ================================================================ RIFIUTI

    @Test
    void nonSiSostituisceUnaPersonaCheNonEAssegnata() {
        ModificaPianoException errore = assertThrows(ModificaPianoException.class,
                () -> service.sostituisci(piano(cenaDelSabato()), cenaDelSabato(), MARCO, LUCA));
        assertTrue(errore.getMessage().contains("non è assegnata"));
    }

    @Test
    void nonSiSostituisceConLaStessaPersona() {
        assertThrows(ModificaPianoException.class,
                () -> service.sostituisci(piano(cenaDelSabato()), cenaDelSabato(), ANNA, ANNA));
    }

    @Test
    void unDipendenteInesistenteVieneRifiutato() {
        ModificaPianoException errore = assertThrows(ModificaPianoException.class,
                () -> service.sostituisci(piano(cenaDelSabato()), cenaDelSabato(), ANNA, 99));
        assertTrue(errore.getMessage().contains("non esiste"));
    }

    @Test
    void ilRuoloDeveCoincidereConQuelloDellaRiga() {
        ModificaPianoException errore = assertThrows(ModificaPianoException.class,
                () -> service.sostituisci(piano(cenaDelSabato()), cenaDelSabato(), ANNA, PAOLO));
        assertTrue(errore.getMessage().contains("richiede"));
    }

    @Test
    void serveUnContrattoValidoNelGiornoDelTurno() {
        ModificaPianoException errore = assertThrows(ModificaPianoException.class,
                () -> service.sostituisci(piano(cenaDelSabato()), cenaDelSabato(), ANNA, GIULIA));
        assertTrue(errore.getMessage().contains("contratto"));
    }

    @Test
    void chiEGiaNelTurnoNonSiPuoRimettere() {
        ModificaPianoException errore = assertThrows(ModificaPianoException.class,
                () -> service.sostituisci(piano(cenaDelSabato()), cenaDelSabato(), ANNA, SARA));
        assertTrue(errore.getMessage().contains("già assegnato"));
    }

    @Test
    void nonSiPuoMettereChiHaGiaUnTurnoCheSiSovrappone() {
        // un altro turno di camerieri, sabato dalle 20 alle 2, dove lavora Luca
        LocalDateTime inizioAltro = INIZIO_CENA.plusHours(2);
        Turno altro = riga(Servizio.CENA, Ruolo.CAMERIERE, inizioAltro, inizioAltro.plusHours(6), 1, LUCA);

        ModificaPianoException errore = assertThrows(ModificaPianoException.class,
                () -> service.sostituisci(piano(cenaDelSabato(), altro), cenaDelSabato(), ANNA, LUCA));
        assertTrue(errore.getMessage().contains("si sovrappone"));
    }

    @Test
    void unaRigaCheNonEnelPianoVieneRifiutata() {
        Turno estranea = riga(Servizio.PRANZO, Ruolo.CAMERIERE, INIZIO_CENA.minusHours(6), INIZIO_CENA.minusHours(3), 1);

        assertThrows(ModificaPianoException.class,
                () -> service.sostituisci(piano(cenaDelSabato()), estranea, ANNA, LUCA));
    }

    // ================================================================ POSTI SCOPERTI E CANDIDATI

    @Test
    void sipuCoprireUnPostoScoperto() throws Exception {
        ModificaPianoService.Esito esito = service.assegnaAlPostoScoperto(piano(cenaDelSabato()), cenaDelSabato(), LUCA);

        assertEquals(List.of(ANNA, SARA, LUCA), assegnatiDi(esito.piano(), 0));
        assertEquals(0, esito.piano().postiScoperti());
    }

    @Test
    void senzaPostiScopertiNonSiPuoAggiungereNessuno() {
        Turno completo = riga(Servizio.CENA, Ruolo.CAMERIERE, INIZIO_CENA, FINE_CENA, 2, ANNA, SARA);

        assertThrows(ModificaPianoException.class,
                () -> service.assegnaAlPostoScoperto(piano(completo), completo, LUCA));
    }

    @Test
    void icandidatiSonoSoloIdoneiPerRuoloContrattoETurno() throws Exception {
        // Marco lavora gia' in un turno che si sovrappone: non e' candidato
        LocalDateTime inizioAltro = INIZIO_CENA.plusHours(2);
        Turno altro = riga(Servizio.CENA, Ruolo.CAMERIERE, inizioAltro, inizioAltro.plusHours(6), 1, MARCO);

        List<Dipendente> candidati = service.candidati(piano(cenaDelSabato(), altro), cenaDelSabato());

        // Anna e Sara sono gia' nel turno, Paolo e' lavapiatti, Giulia non ha contratto, Marco e' sovrapposto
        assertEquals(List.of(LUCA), candidati.stream().map(Dipendente::getId).toList());
    }

    // ================================================================ CARICARE E SALVARE

    @Test
    void caricaESalvaPassanoDalRepository() throws SQLException {
        PianoSettimanale esempio = piano(cenaDelSabato());
        piani.daCaricare = esempio;

        assertEquals(Optional.of(esempio), service.carica(LUNEDI));
        service.salva(esempio);
        assertSame(esempio, piani.salvato);
    }

    // ================================================================ NOMI E PIANO DI BASE

    @Test
    void inomiDeiDipendentiSonoIdEnomeCompleto() throws SQLException {
        Map<Integer, String> nomi = service.nomiDipendenti(LUNEDI);

        assertEquals("Anna Prova", nomi.get(ANNA));
        assertEquals("Luca Prova", nomi.get(LUCA));
        assertEquals(6, nomi.size());
    }

    @Test
    void senzaLaGenerazioneCollegataNonSiPuoGenerareUnPiano() {
        // il service dei test e' costruito senza GenerazioneTurniService
        assertThrows(IllegalStateException.class, () -> service.generaPiano(LUNEDI));
    }
}