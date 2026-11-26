package it.disagili.turnogiusto.service;

// --- IMPORT -------------------------------------------------------------
import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.repository.DipendenteRepository;
import it.disagili.turnogiusto.repository.PianoRepository;
import it.disagili.turnogiusto.service.vincoli.VerificatoreVincoliUS07;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * US-06B: modificare il piano settimanale.
 *
 * Il service NON tiene il piano in memoria: la schermata lo carica, lo passa a ogni operazione
 * e riceve in cambio un piano NUOVO (i record sono immutabili). Solo salva() scrive sul database.
 * Cosi' il titolare puo' fare piu' sostituzioni, guardare i controlli e salvare alla fine.
 *
 * Regole che il service fa rispettare (sono errori: la modifica NON si applica):
 *   - la riga e la persona indicate devono far parte del piano;
 *   - il nuovo dipendente esiste, ha il ruolo della riga e un contratto valido nel giorno del turno;
 *   - non e' gia' in quella riga e non ha un altro turno che si sovrappone.
 * Le regole di US07 (11 ore di riposo, monte ore...) invece NON bloccano: si mostrano come
 * violazioni e decide il titolare, come dice il criterio di US07.
 */
public class ModificaPianoService {

    /** Il controllo dei vincoli: riceve il piano e le assegnazioni confinanti, restituisce i testi delle violazioni. */
    @FunctionalInterface
    public interface VerificaPiano {
        List<String> verifica(PianoSettimanale piano, List<AssegnazioneTurno> confinanti) throws SQLException;
    }

    /**
     * Risultato di una modifica o di una verifica.
     * controlloEseguito = false se i controlli di US07 non sono collegati o non si sono potuti eseguire:
     * l'elenco delle violazioni e' vuoto (oppure contiene il motivo), ma NON vuol dire che il piano sia a posto.
     */
    public record Esito(PianoSettimanale piano, List<String> violazioni, boolean controlloEseguito) {
        public Esito {
            violazioni = List.copyOf(violazioni);
        }
    }

    private final PianoRepository piani;
    private final DipendenteRepository dipendenti;
    private final VerificaPiano verificaPiano;
    private final GenerazioneTurniService generazione; // null nei test che non generano piani

    // Configurazione normale: usa i controlli di Antonio (US07) tramite l'adattatore.
    public ModificaPianoService() {
        this(new VerificatoreVincoliUS07());
    }

    // Permette di scegliere un altro verificatore (o null: controlli spenti). GenerazioneTurniService di
    // Edoardo carica i contratti del periodo e chiama il verificatore con piano, confinanti e contratti.
    public ModificaPianoService(VerificatorePropostaTurni verificatore) {
        this(new PianoRepository(), new DipendenteRepository(), new GenerazioneTurniService(verificatore));
    }

    private ModificaPianoService(PianoRepository piani, DipendenteRepository dipendenti,
                                 GenerazioneTurniService generazione) {
        this(piani, dipendenti, generazione::verificaProposta, generazione);
    }

    // Per i test: permette di passare repository e controlli finti (senza generazione dei piani).
    ModificaPianoService(PianoRepository piani, DipendenteRepository dipendenti, VerificaPiano verificaPiano) {
        this(piani, dipendenti, verificaPiano, null);
    }

    ModificaPianoService(PianoRepository piani, DipendenteRepository dipendenti, VerificaPiano verificaPiano,
                         GenerazioneTurniService generazione) {
        this.piani = Objects.requireNonNull(piani);
        this.dipendenti = Objects.requireNonNull(dipendenti);
        this.verificaPiano = Objects.requireNonNull(verificaPiano);
        this.generazione = generazione;
    }

    // ======================================================================
    // CARICARE E SALVARE
    // ======================================================================

    // il piano salvato per quel lunedi', se c'e'
    public Optional<PianoSettimanale> carica(LocalDate lunedi) throws SQLException {
        return piani.trova(lunedi);
    }

    // scrive il piano sul database (sostituisce quello della stessa settimana)
    public void salva(PianoSettimanale piano) throws SQLException {
        piani.salva(piano);
    }

    // ======================================================================
    // PIANO DI BASE E NOMI (servono alla schermata)
    // ======================================================================

    // Genera in memoria un piano per quella settimana, con la generazione di Edoardo e gli orari
    // condivisi per ruolo, e lo controlla. NON lo salva: lo salva il titolare dopo la revisione.
    // Le sostituzioni nei piani già esistenti conservano invece gli orari della riga.
    public Esito generaPiano(LocalDate lunedi) throws SQLException {
        if (generazione == null) {
            throw new IllegalStateException("La generazione dei piani non è collegata a questo service.");
        }
        List<AssegnazioneTurno> confinanti = piani.trovaConfinanti(lunedi);
        PianoSettimanale piano = generazione.generaPerRuolo(lunedi, Set.of(), confinanti).piano();
        return verifica(piano);
    }

    // id -> "Nome Cognome" dei dipendenti con il contratto valido nel giorno indicato
    // (la schermata li usa per scrivere i nomi invece degli ID).
    public Map<Integer, String> nomiDipendenti(LocalDate giorno) throws SQLException {
        Map<Integer, String> nomi = new HashMap<>();
        for (Dipendente dipendente : dipendenti.trovaTutti(giorno)) {
            nomi.put(dipendente.getId(), dipendente.getNomeCompleto());
        }
        return nomi;
    }

    // ======================================================================
    // CONTROLLARE IL PIANO
    // ======================================================================
    public Esito verifica(PianoSettimanale piano) throws SQLException {
        try {
            // Le assegnazioni "confinanti" (giorno prima e dopo la settimana) si leggono dal database.
            List<AssegnazioneTurno> confinanti = piani.trovaConfinanti(piano.inizioSettimana());
            List<String> violazioni = verificaPiano.verifica(piano, confinanti);
            return new Esito(piano, conNomi(piano.inizioSettimana(), violazioni), true);
        } catch (IllegalStateException nonCollegato) {
            // Il service di Edoardo lancia questa eccezione quando il verificatore US07 e' null.
            // Non e' un errore dell'utente: restituisco il piano senza controlli, dichiarandolo.
            return new Esito(piano, List.of(), false);
        } catch (IllegalArgumentException nonEseguibile) {
            // I controlli di Antonio rifiutano i dati incompleti (per esempio un giorno senza contratto).
            // Il piano resta modificabile: dico perche' i controlli non sono stati eseguiti.
            return new Esito(piano, List.of("Controlli non eseguibili: " + nonEseguibile.getMessage()), false);
        }
    }

    // I testi dell'adattatore cominciano con "Dipendente 12: ". Qui l'ID diventa il nome, cosi' il titolare
    // legge "Anna Rossi: ..." (il formato e' definito in VerificatoreVincoliUS07.PREFISSO_DIPENDENTE).
    private List<String> conNomi(LocalDate lunedi, List<String> violazioni) throws SQLException {
        if (violazioni.isEmpty()) {
            return violazioni;
        }
        Map<Integer, String> nomi = new HashMap<>();
        for (Dipendente dipendente : dipendenti.trovaTutti(lunedi)) {
            nomi.put(dipendente.getId(), dipendente.getNomeCompleto());
        }
        List<String> risultato = new ArrayList<>();
        for (String testo : violazioni) {
            Matcher prefisso = VerificatoreVincoliUS07.PREFISSO_DIPENDENTE.matcher(testo);
            if (prefisso.find() && nomi.containsKey(Integer.parseInt(prefisso.group(1)))) {
                risultato.add(nomi.get(Integer.parseInt(prefisso.group(1))) + ": " + testo.substring(prefisso.end()));
            } else {
                risultato.add(testo); // formato diverso o dipendente sconosciuto: lo lascio com'e'
            }
        }
        return risultato;
    }

    // ======================================================================
    // CHI SI PUO' METTERE IN UNA RIGA (per riempire il menu a tendina)
    // ======================================================================
    public List<Dipendente> candidati(PianoSettimanale piano, Turno riga)
            throws SQLException, ModificaPianoException {
        int indice = indiceDellaRiga(piano, riga);
        Turno attuale = piano.turni().get(indice);

        List<Dipendente> risultato = new ArrayList<>();
        // trovaTutti(giorno) restituisce ogni dipendente con il contratto valido in quel giorno (o null).
        for (Dipendente dipendente : dipendenti.trovaTutti(attuale.inizio().toLocalDate())) {
            boolean idoneo = dipendente.getRuolo() == attuale.ruolo()
                    && dipendente.getContratto() != null
                    && !giaNellaRiga(attuale, dipendente.getId())
                    && !sovrappostoAltrove(piano, indice, attuale, dipendente.getId());
            if (idoneo) {
                risultato.add(dipendente);
            }
        }
        return risultato;
    }

    // ======================================================================
    // SOSTITUIRE UN ASSEGNATO
    // ======================================================================
    // Toglie "vecchioId" dalla riga e mette "nuovoId". Restituisce il piano modificato (NON salvato)
    // insieme all'esito dei controlli.
    public Esito sostituisci(PianoSettimanale piano, Turno riga, int vecchioId, int nuovoId)
            throws SQLException, ModificaPianoException {
        return applica(piano, riga, vecchioId, nuovoId);
    }

    // ======================================================================
    // COPRIRE UN POSTO SCOPERTO
    // ======================================================================
    // Aggiunge una persona a una riga che ha ancora posti scoperti.
    public Esito assegnaAlPostoScoperto(PianoSettimanale piano, Turno riga, int nuovoId)
            throws SQLException, ModificaPianoException {
        return applica(piano, riga, null, nuovoId);
    }

    // Logica comune: vecchioId = null significa "aggiungo", altrimenti "sostituisco".
    private Esito applica(PianoSettimanale piano, Turno riga, Integer vecchioId, int nuovoId)
            throws SQLException, ModificaPianoException {

        // PASSO 1 - la riga deve far parte del piano. Si cerca per servizio, ruolo e orario di inizio.
        int indice = indiceDellaRiga(piano, riga);
        Turno attuale = piano.turni().get(indice);

        // PASSO 2 - controlli sulla persona che esce (o sul posto libero).
        if (vecchioId != null) {
            if (!giaNellaRiga(attuale, vecchioId)) {
                throw new ModificaPianoException("La persona da sostituire non è assegnata a questo turno");
            }
            if (vecchioId == nuovoId) {
                throw new ModificaPianoException("Hai scelto la stessa persona: non cambia niente");
            }
        } else if (attuale.postiScoperti() == 0) {
            throw new ModificaPianoException("In questo turno non ci sono posti scoperti");
        }

        // PASSO 3 - controlli sulla persona che entra. Si cerca tra i dipendenti con il contratto
        // valido nel giorno del turno.
        Dipendente nuovo = dipendenti.trovaTutti(attuale.inizio().toLocalDate()).stream()
                .filter(d -> d.getId() == nuovoId)
                .findFirst()
                .orElseThrow(() -> new ModificaPianoException("Il dipendente scelto non esiste"));
        if (nuovo.getRuolo() != attuale.ruolo()) {
            throw new ModificaPianoException(nuovo.getNomeCompleto() + " è " + nuovo.getRuolo()
                    + ": questo turno richiede " + attuale.ruolo());
        }
        if (nuovo.getContratto() == null) {
            throw new ModificaPianoException(nuovo.getNomeCompleto()
                    + " non ha un contratto valido nel giorno del turno");
        }
        if (giaNellaRiga(attuale, nuovoId)) {
            throw new ModificaPianoException(nuovo.getNomeCompleto() + " è già assegnato a questo turno");
        }
        if (sovrappostoAltrove(piano, indice, attuale, nuovoId)) {
            throw new ModificaPianoException(nuovo.getNomeCompleto()
                    + " ha già un altro turno che si sovrappone a questo");
        }

        // PASSO 4 - costruisco la riga nuova: copio le assegnazioni, sostituendo (o aggiungendo) quella richiesta.
        // L'orario di una assegnazione e' sempre quello della sua riga.
        List<AssegnazioneTurno> assegnazioni = new ArrayList<>();
        for (AssegnazioneTurno assegnazione : attuale.assegnazioni()) {
            if (vecchioId != null && assegnazione.dipendenteId() == vecchioId) {
                assegnazioni.add(new AssegnazioneTurno(nuovoId, attuale.inizio(), attuale.fine()));
            } else {
                assegnazioni.add(assegnazione);
            }
        }
        if (vecchioId == null) {
            assegnazioni.add(new AssegnazioneTurno(nuovoId, attuale.inizio(), attuale.fine()));
        }
        Turno rigaNuova = new Turno(attuale.servizio(), attuale.ruolo(), attuale.inizio(), attuale.fine(),
                attuale.postiRichiesti(), assegnazioni);

        // PASSO 5 - nuovo piano con quella riga cambiata, poi i controlli di US07.
        // La modifica si applica anche se ci sono violazioni: le mostro, decide il titolare.
        List<Turno> turni = new ArrayList<>(piano.turni());
        turni.set(indice, rigaNuova);
        return verifica(new PianoSettimanale(piano.inizioSettimana(), turni));
    }

    // ======================================================================
    // METODI DI AIUTO
    // ======================================================================

    // posizione della riga nel piano; la riga si riconosce da servizio, ruolo e orario di inizio
    private static int indiceDellaRiga(PianoSettimanale piano, Turno riga) throws ModificaPianoException {
        for (int i = 0; i < piano.turni().size(); i++) {
            Turno turno = piano.turni().get(i);
            if (turno.servizio() == riga.servizio() && turno.ruolo() == riga.ruolo()
                    && turno.inizio().equals(riga.inizio())) {
                return i;
            }
        }
        throw new ModificaPianoException("Questo turno non fa parte del piano");
    }

    private static boolean giaNellaRiga(Turno riga, int dipendenteId) {
        return riga.assegnazioni().stream().anyMatch(a -> a.dipendenteId() == dipendenteId);
    }

    // vero se la persona ha un'assegnazione in UN'ALTRA riga che si sovrappone nel tempo a questa
    private static boolean sovrappostoAltrove(PianoSettimanale piano, int indiceRiga, Turno riga, int dipendenteId) {
        AssegnazioneTurno nuova = new AssegnazioneTurno(dipendenteId, riga.inizio(), riga.fine());
        for (int i = 0; i < piano.turni().size(); i++) {
            if (i == indiceRiga) {
                continue; // la stessa riga e' gia' coperta da giaNellaRiga
            }
            for (AssegnazioneTurno altra : piano.turni().get(i).assegnazioni()) {
                if (altra.dipendenteId() == dipendenteId && altra.siSovrappone(nuova)) {
                    return true;
                }
            }
        }
        return false;
    }
}
