package it.disagili.turnogiusto.service.vincoli;

// --- IMPORT -------------------------------------------------------------
import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.service.VerificatorePropostaTurni;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * ADATTATORE tra due pezzi scritti da persone diverse:
 *   - la generazione di Edoardo e la modifica del piano chiamano VerificatorePropostaTurni
 *     (usa AssegnazioneTurno e restituisce testi);
 *   - i controlli di Antonio sono in VerificaVincoliService (usa AssegnazioneDaVerificare
 *     e restituisce ViolazioneTurno).
 * Questa classe traduce l'una nell'altra: non contiene regole, chiama solo quelle di Antonio.
 */
public class VerificatoreVincoliUS07 implements VerificatorePropostaTurni {

    // Ogni violazione e' scritta come "Dipendente 12: motivo". Chi mostra il testo puo' sostituire
    // "Dipendente 12" con il nome (lo fa ModificaPianoService). Il formato e' definito qui, in un punto solo.
    public static final Pattern PREFISSO_DIPENDENTE = Pattern.compile("^Dipendente (\\d+): ");

    // Un turno che inizia prima delle 16:00 e' un pranzo, altrimenti una cena.
    // L'interfaccia di Edoardo passa solo gli orari (non il servizio), quindi il servizio si ricava da qui.
    private static final LocalTime FINE_PRANZO = LocalTime.of(16, 0);

    private final VerificaVincoliService vincoli;

    public VerificatoreVincoliUS07() {
        this(new VerificaVincoliService());
    }

    public VerificatoreVincoliUS07(VerificaVincoliService vincoli) {
        this.vincoli = Objects.requireNonNull(vincoli);
    }

    @Override
    public List<String> verifica(LocalDate inizioSettimana, List<AssegnazioneTurno> proposta,
                                 List<AssegnazioneTurno> confinanti, List<Contratto> contratti) {
        // PASSO 1 - traduco le assegnazioni di Edoardo in quelle di Antonio.
        List<AssegnazioneDaVerificare> propostaAntonio = proposta.stream().map(VerificatoreVincoliUS07::converti).toList();
        List<AssegnazioneDaVerificare> confinantiAntonio = confinanti.stream().map(VerificatoreVincoliUS07::converti).toList();

        // PASSO 2 - chiamo i controlli di Antonio.
        List<ViolazioneTurno> violazioni =
                vincoli.verificaSettimana(inizioSettimana, propostaAntonio, confinantiAntonio, contratti);

        // PASSO 3 - ogni violazione diventa un testo per il titolare.
        return violazioni.stream()
                .map(v -> "Dipendente " + v.dipendenteId() + ": " + v.motivo())
                .toList();
    }

    // giornoPiano = il giorno in cui il turno INIZIA: la cena della domenica appartiene alla domenica
    // anche se finisce il lunedi' alle 00:30.
    private static AssegnazioneDaVerificare converti(AssegnazioneTurno assegnazione) {
        return new AssegnazioneDaVerificare(
                assegnazione.dipendenteId(),
                assegnazione.inizio().toLocalDate(),
                servizioDi(assegnazione.inizio()),
                assegnazione.inizio(),
                assegnazione.fine());
    }

    private static Servizio servizioDi(LocalDateTime inizio) {
        return inizio.toLocalTime().isBefore(FINE_PRANZO) ? Servizio.PRANZO : Servizio.CENA;
    }
}