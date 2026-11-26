package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Disponibilita;
import it.disagili.turnogiusto.model.Fabbisogno;
import it.disagili.turnogiusto.model.OrarioServizio;
import it.disagili.turnogiusto.model.OrariTurni;
import java.util.function.Function;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.model.TipoGiorno;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.repository.DipendenteRepository;
import it.disagili.turnogiusto.repository.DisponibilitaRepository;
import it.disagili.turnogiusto.repository.FabbisognoRepository;
import it.disagili.turnogiusto.service.vincoli.VerificatoreVincoliUS07;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** US06A: costruisce una bozza dai fabbisogni, dalle anagrafiche e dalle disponibilità. */
public class GenerazioneTurniService {
    private final FabbisognoRepository fabbisogni;
    private final DipendenteRepository dipendenti;
    private final DisponibilitaRepository disponibilita;
    private final VerificatorePropostaTurni verificatore;

    /** Senza controlli sui vincoli: serve ai test dell'algoritmo. L'applicazione usa {@link #conVincoli()}. */
    public GenerazioneTurniService() {
        this(null);
    }

    /**
     * Configurazione dell'applicazione: ogni assegnazione provata passa dai controlli di US07
     * (riposo, doppio servizio, giorno libero, monte ore) e quelle che li violano vengono scartate.
     */
    public static GenerazioneTurniService conVincoli() {
        return new GenerazioneTurniService(new VerificatoreVincoliUS07());
    }

    /** Entrata per l'integrazione con il verificatore di Antonio. */
    public GenerazioneTurniService(VerificatorePropostaTurni verificatore) {
        this(new FabbisognoRepository(), new DipendenteRepository(), new DisponibilitaRepository(), verificatore);
    }

    /** Permette ai test del service di fornire le letture in memoria. */
    GenerazioneTurniService(FabbisognoRepository fabbisogni, DipendenteRepository dipendenti,
                            DisponibilitaRepository disponibilita, VerificatorePropostaTurni verificatore) {
        this.fabbisogni = fabbisogni;
        this.dipendenti = dipendenti;
        this.disponibilita = disponibilita;
        this.verificatore = verificatore;
    }

    /** Restituisce il piano e i dati necessari per mostrarlo, compreso lo stato della verifica. */
    public record Esito(PianoSettimanale piano, Map<Integer, String> nomiDipendenti,
                        boolean controlloEseguito, List<String> violazioni) {
        public Esito {
            nomiDipendenti = Map.copyOf(nomiDipendenti);
            violazioni = List.copyOf(violazioni);
        }
    }

    /**
     * Genera una nuova bozza in memoria. I turni confinanti sono assegnazioni che iniziano
     * fuori dalla settimana, anche quando terminano al suo interno. US06B potrà passarli
     * dopo averli letti dalla propria persistenza.
     */
    public Esito genera(LocalDate lunedi, Map<Servizio, OrarioServizio> orari, Set<LocalDate> festivi,
                       List<AssegnazioneTurno> confinanti) throws SQLException {
        PianoSettimanale.verificaInizioSettimana(lunedi);
        Map<Servizio, OrarioServizio> orariRichiesti = Map.copyOf(orari);
        if (orariRichiesti.size() != Servizio.values().length) {
            throw new IllegalArgumentException("Indica gli orari di pranzo e cena.");
        }
        // Compatibilità per i chiamanti che forniscono ancora orari espliciti.
        return generaConOrari(lunedi, f -> orariRichiesti.get(f.getServizio()), festivi, confinanti);
    }

    /** Nuove bozze con gli orari condivisi per ruolo, identici per tutti i tipi di giorno. */
    public Esito generaPerRuolo(LocalDate lunedi, Set<LocalDate> festivi,
                               List<AssegnazioneTurno> confinanti) throws SQLException {
        return generaConOrari(lunedi, f -> OrariTurni.per(f.getRuolo(), f.getServizio()), festivi, confinanti);
    }

    // Un solo algoritmo: cambia soltanto come viene scelta la fascia del fabbisogno.
    private Esito generaConOrari(LocalDate lunedi, Function<Fabbisogno, OrarioServizio> orari,
                                Set<LocalDate> festivi, List<AssegnazioneTurno> confinanti) throws SQLException {
        PianoSettimanale.verificaInizioSettimana(lunedi);
        Set<LocalDate> giorniFestivi = Set.copyOf(festivi);
        List<AssegnazioneTurno> storico = copiaConfinanti(lunedi, confinanti);
        for (LocalDate festivo : giorniFestivi) {
            if (!nellaSettimana(lunedi, festivo)) {
                throw new IllegalArgumentException("I festivi devono appartenere alla settimana selezionata.");
            }
        }

        List<Turno> richiesti = creaTurniRichiesti(lunedi, orari, giorniFestivi, fabbisogni.trovaTutti());
        DatiDipendenti dati = caricaDipendenti(lunedi, richiesti, storico);
        Map<Integer, List<Disponibilita>> finestrePerDipendente = new HashMap<>();
        List<Turno> turni = new ArrayList<>();
        List<AssegnazioneTurno> proposta = new ArrayList<>();

        for (Turno richiesto : richiesti) {
            List<AssegnazioneTurno> assegnate = new ArrayList<>();
            for (Dipendente dipendente : dati.perGiorno().get(richiesto.inizio().toLocalDate())) {
                if (assegnate.size() == richiesto.postiRichiesti()) {
                    break;
                }
                Contratto contratto = dipendente.getContratto();
                if (dipendente.getRuolo() != richiesto.ruolo() || contratto == null
                        || !contratto.isValidoIl(richiesto.inizio().toLocalDate())) {
                    continue;
                }
                AssegnazioneTurno candidata = new AssegnazioneTurno(dipendente.getId(),
                        richiesto.inizio(), richiesto.fine());
                if (haSovrapposizioni(candidata, proposta) || haSovrapposizioni(candidata, storico)
                        || !copreTurno(dipendente, candidata, finestrePerDipendente)) {
                    continue;
                }

                // Il verificatore riceve la proposta completa, compresa l'assegnazione appena provata.
                proposta.add(candidata);
                // La riga temporanea include il candidato una sola volta e conserva
                // il fabbisogno originale, anche durante la costruzione del piano.
                List<AssegnazioneTurno> tentativo = new ArrayList<>(assegnate);
                tentativo.add(candidata);
                List<Turno> righeProposte = new ArrayList<>(turni);
                righeProposte.add(new Turno(richiesto.servizio(), richiesto.ruolo(),
                        richiesto.inizio(), richiesto.fine(), richiesto.postiRichiesti(), tentativo));
                if (verificatore != null && !verifica(new PianoSettimanale(lunedi, righeProposte),
                        storico, dati.contratti()).isEmpty()) {
                    proposta.removeLast();
                    continue;
                }
                assegnate.add(candidata);
            }
            turni.add(new Turno(richiesto.servizio(), richiesto.ruolo(), richiesto.inizio(), richiesto.fine(),
                    richiesto.postiRichiesti(), assegnate));
        }

        PianoSettimanale piano = new PianoSettimanale(lunedi, turni);
        List<String> violazioni = verificatore == null ? List.of() : verifica(piano, storico, dati.contratti());
        return new Esito(piano, dati.nomi(), verificatore != null, violazioni);
    }

    /** Verifica il piano completo risultante da una modifica, con i contratti riletti dal database. */
    public List<String> verificaProposta(PianoSettimanale proposta, List<AssegnazioneTurno> confinanti)
            throws SQLException {
        if (verificatore == null) {
            throw new IllegalStateException("Il controllo dei vincoli US07 non è ancora collegato.");
        }
        LocalDate lunedi = proposta.inizioSettimana();
        List<AssegnazioneTurno> storico = copiaConfinanti(lunedi, confinanti);
        DatiDipendenti dati = caricaDipendenti(lunedi, proposta.turni(), storico);
        return verifica(proposta, storico, dati.contratti());
    }

    private List<String> verifica(PianoSettimanale proposta,
                                 List<AssegnazioneTurno> storico, List<Contratto> contratti) {
        return List.copyOf(verificatore.verificaPiano(proposta, storico, contratti));
    }

    /** Espande ciascun fabbisogno sulla settimana e pone prima gli intervalli che iniziano prima. */
    private List<Turno> creaTurniRichiesti(LocalDate lunedi, Function<Fabbisogno, OrarioServizio> orari,
                                          Set<LocalDate> festivi, List<Fabbisogno> configurati) {
        List<Turno> turni = new ArrayList<>();
        for (int indice = 0; indice < 7; indice++) {
            LocalDate giorno = lunedi.plusDays(indice);
            TipoGiorno tipo = tipoDelGiorno(giorno, festivi);
            for (Fabbisogno fabbisogno : configurati) {
                if (fabbisogno.getTipoGiorno() == tipo && fabbisogno.getNumeroMinimoDipendenti() > 0) {
                    Servizio servizio = fabbisogno.getServizio();
                    OrarioServizio orario = orari.apply(fabbisogno);
                    turni.add(new Turno(servizio, fabbisogno.getRuolo(), giorno.atTime(orario.inizio()),
                            orario.fineDelServizio(giorno), fabbisogno.getNumeroMinimoDipendenti(), List.of()));
                }
            }
        }
        // L'ordine resta stabile anche quando il database restituisce i fabbisogni in ordine diverso.
        turni.sort(Comparator.comparing(Turno::inizio).thenComparing(Turno::servizio).thenComparing(Turno::ruolo));
        return turni;
    }

    private TipoGiorno tipoDelGiorno(LocalDate giorno, Set<LocalDate> festivi) {
        if (festivi.contains(giorno)) {
            return TipoGiorno.FESTIVO;
        }
        DayOfWeek giornoSettimana = giorno.getDayOfWeek();
        return giornoSettimana == DayOfWeek.SATURDAY || giornoSettimana == DayOfWeek.SUNDAY
                ? TipoGiorno.WEEKEND : TipoGiorno.FERIALE;
    }

    /** Raccoglie i contratti rilevanti senza duplicare quelli validi per più giorni. */
    private DatiDipendenti caricaDipendenti(LocalDate lunedi, List<Turno> turni,
                                            List<AssegnazioneTurno> confinanti) throws SQLException {
        Set<LocalDate> date = new TreeSet<>(lunedi.datesUntil(lunedi.plusWeeks(1)).toList());
        turni.forEach(turno -> aggiungiDate(date, turno.inizio(), turno.fine()));
        confinanti.forEach(turno -> aggiungiDate(date, turno.inizio(), turno.fine()));

        Map<LocalDate, List<Dipendente>> perGiorno = new HashMap<>();
        Map<Integer, Contratto> contratti = new LinkedHashMap<>();
        Map<Integer, String> nomi = new HashMap<>();
        for (LocalDate data : date) {
            List<Dipendente> anagrafiche = new ArrayList<>(dipendenti.trovaTutti(data));
            anagrafiche.sort(Comparator.comparingInt(Dipendente::getId));
            perGiorno.put(data, anagrafiche);
            for (Dipendente dipendente : anagrafiche) {
                nomi.put(dipendente.getId(), dipendente.getNomeCompleto());
                Contratto contratto = dipendente.getContratto();
                if (contratto != null) {
                    contratti.putIfAbsent(contratto.getId(), contratto);
                }
            }
        }
        return new DatiDipendenti(perGiorno, List.copyOf(contratti.values()), nomi);
    }

    private void aggiungiDate(Set<LocalDate> date, LocalDateTime inizio, LocalDateTime fine) {
        // Una fine alle 00:00 non occupa nemmeno un istante nella giornata seguente.
        LocalDate ultimoGiorno = fine.minusNanos(1).toLocalDate();
        inizio.toLocalDate().datesUntil(ultimoGiorno.plusDays(1)).forEach(date::add);
    }

    private boolean haSovrapposizioni(AssegnazioneTurno candidata, List<AssegnazioneTurno> esistenti) {
        for (AssegnazioneTurno altra : esistenti) {
            if (altra.dipendenteId() == candidata.dipendenteId() && candidata.siSovrappone(altra)) {
                return true;
            }
        }
        return false;
    }

    private List<AssegnazioneTurno> copiaConfinanti(LocalDate lunedi, List<AssegnazioneTurno> confinanti) {
        List<AssegnazioneTurno> storico = List.copyOf(confinanti);
        for (AssegnazioneTurno turno : storico) {
            if (nellaSettimana(lunedi, turno.inizio().toLocalDate())) {
                throw new IllegalArgumentException("Un turno della settimana deve comparire solo nella proposta.");
            }
        }
        return storico;
    }

    private boolean nellaSettimana(LocalDate lunedi, LocalDate giorno) {
        return !giorno.isBefore(lunedi) && giorno.isBefore(lunedi.plusWeeks(1));
    }

    /** Un part-time deve coprire tutto l'intervallo, anche con più fasce senza interruzioni. */
    private boolean copreTurno(Dipendente dipendente, AssegnazioneTurno turno,
                                Map<Integer, List<Disponibilita>> finestrePerDipendente) throws SQLException {
        if (!dipendente.isPartTime()) {
            return dipendente.getContratto().getTipoContratto() == TipoContratto.FULL_TIME;
        }
        int id = dipendente.getId();
        List<Disponibilita> finestre = finestrePerDipendente.get(id);
        if (finestre == null) {
            finestre = disponibilita.trovaPerDipendente(id);
            finestrePerDipendente.put(id, finestre);
        }

        List<Intervallo> fasce = new ArrayList<>();
        // Partiamo dal giorno precedente per trovare le finestre che proseguono oltre mezzanotte.
        LocalDate giorno = turno.inizio().toLocalDate().minusDays(1);
        while (!giorno.isAfter(turno.fine().toLocalDate())) {
            for (Disponibilita finestra : finestre) {
                if (giorno.getDayOfWeek() == finestra.getGiornoSettimana()) {
                    LocalDate giornoFine = finestra.attraversaMezzanotte() ? giorno.plusDays(1) : giorno;
                    fasce.add(new Intervallo(giorno.atTime(finestra.getOraInizio()), giornoFine.atTime(finestra.getOraFine())));
                }
            }
            giorno = giorno.plusDays(1);
        }
        fasce.sort(Comparator.comparing(Intervallo::inizio));
        LocalDateTime copertoFino = turno.inizio();
        for (Intervallo fascia : fasce) {
            if (fascia.inizio().isAfter(copertoFino)) {
                break; // La prima interruzione lascia scoperta una parte del turno.
            }
            if (fascia.fine().isAfter(copertoFino)) {
                copertoFino = fascia.fine();
            }
            if (!copertoFino.isBefore(turno.fine())) {
                return true;
            }
        }
        return false;
    }

    /** Dati anagrafici letti una volta per data e contratti pronti per US07. */
    private record DatiDipendenti(Map<LocalDate, List<Dipendente>> perGiorno, List<Contratto> contratti,
                                  Map<Integer, String> nomi) { }

    /** Fascia settimanale convertita in orari con data. */
    private record Intervallo(LocalDateTime inizio, LocalDateTime fine) { }
}
