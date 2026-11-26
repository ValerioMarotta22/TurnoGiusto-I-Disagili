package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.*;
import it.disagili.turnogiusto.repository.*;
import it.disagili.turnogiusto.service.vincoli.VerificatoreVincoliUS07;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Collegamenti US06A/US06B reali con letture in memoria; nessun database. */
class OrariPerRuoloIntegrazioneTest {
    private static final LocalDate LUNEDI = LocalDate.of(2026, 12, 21);

    private DipendenteRepository dipendenti(List<Dipendente> persone) {
        return new DipendenteRepository() {
            @Override public List<Dipendente> trovaTutti(LocalDate giorno) { return persone; }
        };
    }

    private GenerazioneTurniService generatore(DipendenteRepository dipendenti) {
        return new GenerazioneTurniService(new FabbisognoRepository() {
            @Override public List<Fabbisogno> trovaTutti() {
                var fabbisogni = new ArrayList<Fabbisogno>();
                for (var tipo : TipoGiorno.values()) {
                    for (var ruolo : Ruolo.values()) {
                        for (var servizio : Servizio.values()) {
                            fabbisogni.add(new Fabbisogno(ruolo, tipo, servizio, 1));
                        }
                    }
                }
                return fabbisogni;
            }
        }, dipendenti, new DisponibilitaRepository(), new VerificatoreVincoliUS07());
    }

    private void verificaOrari(PianoSettimanale piano) {
        assertEquals(56, piano.turni().size()); // 4 ruoli, 2 servizi, 7 giorni.
        for (var turno : piano.turni()) {
            var fascia = OrariTurni.per(turno.ruolo(), turno.servizio());
            assertEquals(fascia.inizio(), turno.inizio().toLocalTime());
            assertEquals(fascia.fineDelServizio(turno.inizio().toLocalDate()), turno.fine());
        }
    }

    @Test
    void generazioneUsaLeFascePerRuoloAncheNelFestivoENelWeekend() throws Exception {
        var piano = generatore(dipendenti(List.of())).generaPerRuolo(
                LUNEDI, Set.of(LocalDate.of(2026, 12, 25)), List.of()).piano();
        verificaOrari(piano);
    }

    @Test
    void nuovaBozzaDaUS06BUsaGliStessiOrariSenzaSalvare() throws Exception {
        var persone = dipendenti(List.of());
        var generatore = generatore(persone);
        var servizio = new ModificaPianoService(new PianoRepository() {
            @Override public List<AssegnazioneTurno> trovaConfinanti(LocalDate giorno) { return List.of(); }
            @Override public void salva(PianoSettimanale piano) { fail("La bozza non deve essere salvata"); }
        }, persone, generatore::verificaProposta, generatore);
        var esito = servizio.generaPiano(LUNEDI);
        verificaOrari(esito.piano());
        assertTrue(esito.controlloEseguito());
    }

    @Test
    void assegnazioneManualeConservaSiaLeNuoveFasceSiaQuelleDeiPianiEsistenti() throws Exception {
        var contratto = new Contratto(1, 1, TipoContratto.FULL_TIME, 40, BigDecimal.TEN,
                LUNEDI.minusMonths(1), null);
        var persone = dipendenti(List.of(new Dipendente(1, "Anna", "Rossi", Ruolo.CUOCO, contratto)));
        var generatore = generatore(persone);
        var servizio = new ModificaPianoService(new PianoRepository() {
            @Override public List<AssegnazioneTurno> trovaConfinanti(LocalDate giorno) { return List.of(); }
        }, persone, generatore::verificaProposta, generatore);
        // La seconda fascia rappresenta un vecchio piano: non viene convertita di nascosto.
        for (var fascia : List.of(OrariTurni.per(Ruolo.CUOCO, Servizio.CENA),
                new OrarioServizio(LocalTime.of(18, 0), LocalTime.of(0, 30)))) {
            var riga = new Turno(Servizio.CENA, Ruolo.CUOCO, LUNEDI.atTime(fascia.inizio()),
                    fascia.fineDelServizio(LUNEDI), 1, List.of());
            var piano = new PianoSettimanale(LUNEDI, List.of(riga));
            var esito = servizio.assegnaAlPostoScoperto(piano, riga, 1);
            var assegnata = esito.piano().assegnazioni().getFirst();
            assertEquals(riga.inizio(), assegnata.inizio());
            assertEquals(riga.fine(), assegnata.fine());
            assertTrue(esito.violazioni().isEmpty());
            assertTrue(piano.assegnazioni().isEmpty());
        }
    }
}
