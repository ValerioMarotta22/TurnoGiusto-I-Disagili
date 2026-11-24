package it.disagili.turnogiusto.service.vincoli;

import it.disagili.turnogiusto.model.Servizio;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import it.disagili.turnogiusto.model.Contratto;

/**
 * Raccoglie i controlli condivisi dei vincoli US07.
 * Lavora sui dati ricevuti, senza accedere al database o a JavaFX.
 */
public class VerificaVincoliService {

    /**
     * Ingresso complessivo locale di US07, senza database né JavaFX.
     *
     * La proposta contiene il piano risultante della settimana, anche incompleto:
     * il candidato è già incluso e una modifica sostituisce il turno precedente.
     * I confinanti sono le assegnazioni di contesto fornite dal chiamante.
     * Lo storico deve coprire ogni giorno della settimana per le persone proposte.
     *
     * Restituisce solo problemi pertinenti alla proposta. I giorni senza contratto
     * e le sovrapposizioni contrattuali restano errori espliciti, non un esito valido.
     * Non sostituisce i controlli di ruolo, disponibilità e sovrapposizione dei turni,
     * che devono essere già verificati dal chiamante.
     *
     * Il collegamento ai tipi Turno/AssegnazioneTurno dei compagni resta esterno:
     * qui utilizziamo le assegnazioni locali già presenti, senza duplicare quei model.
     */
    public List<ViolazioneTurno> verificaSettimana(
            LocalDate lunedi,
            List<AssegnazioneDaVerificare> proposta,
            List<AssegnazioneDaVerificare> confinanti,
            List<Contratto> contratti) {

        if (lunedi == null || lunedi.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("La settimana deve iniziare di lunedì.");
        }
        if (proposta == null || confinanti == null || contratti == null) {
            throw new IllegalArgumentException(
                    "Servono proposta, confinanti e storico contrattuale.");
        }

        LocalDate lunediSuccessivo = lunedi.plusDays(7);
        // TreeSet raccoglie ogni persona una sola volta e rende stabile l'ordine.
        Set<Integer> dipendenti = new TreeSet<>();
        for (AssegnazioneDaVerificare turno : proposta) {
            if (turno.giornoPiano().isBefore(lunedi)
                    || !turno.giornoPiano().isBefore(lunediSuccessivo)) {
                throw new IllegalArgumentException(
                        "La proposta contiene un turno fuori dalla settimana.");
            }
            dipendenti.add(turno.dipendenteId());
        }

        // I controlli tra turni includono anche il confine con altre settimane.
        List<ViolazioneTurno> violazioni = new ArrayList<>(
                verificaVincoliTraTurniProposta(proposta, confinanti));

        Set<AssegnazioneDaVerificare> uniche = new LinkedHashSet<>(proposta);
        uniche.addAll(confinanti);
        List<AssegnazioneDaVerificare> assegnazioni = new ArrayList<>(uniche);

        // I controlli settimanali filtrano giornoPiano e dipendente internamente.
        // Non richiediamo contratti per persone presenti soltanto nello storico.
        for (int dipendenteId : dipendenti) {
            violazioni.addAll(verificaRiposoSettimanale(
                    dipendenteId, lunedi, assegnazioni));
            violazioni.addAll(verificaMonteOreConStorico(
                    dipendenteId, lunedi, assegnazioni, contratti));
        }

        // Nessun salvataggio o modifica agli ingressi: solo un risultato immutabile.
        return List.copyOf(violazioni);
    }

    // Soglia comune: tra due turni devono trascorrere almeno 11 ore.
    private static final Duration RIPOSO_MINIMO = Duration.ofHours(11);

    /**
     * Restituisce il tempo tra la fine del turno precedente e l'inizio del successivo.
     * Include i giorni interi e conserva minuti e secondi, senza arrotondamenti.
     * Questo calcolo serve sia al controllo della soglia sia al dettaglio della violazione.
     */
    public Duration calcolaRiposo(
            LocalDateTime finePrimoTurno,
            LocalDateTime inizioSecondoTurno) {

        return Duration.between(finePrimoTurno, inizioSecondoTurno);
    }

    /**
     * Verifica il riposo tra due turni dello stesso dipendente.
     * Riceve data e ora complete, così il confronto funziona anche tra settimane diverse.
     *
     * @param finePrimoTurno data e ora di fine del turno precedente
     * @param inizioSecondoTurno data e ora di inizio del turno successivo
     * @return true se il riposo è di almeno 11 ore, false altrimenti
     */
    public boolean rispettaRiposoMinimo(
            LocalDateTime finePrimoTurno,
            LocalDateTime inizioSecondoTurno) {
        // Calcoliamo l'intervallo conservando anche minuti e secondi.
        Duration riposoEffettivo =
                calcolaRiposo(finePrimoTurno, inizioSecondoTurno);

        // compareTo restituisce un valore negativo, zero o un valore positivo.
        // Con >= 0 accettiamo un riposo uguale o superiore alla soglia.
        return riposoEffettivo.compareTo(RIPOSO_MINIMO) >= 0;
    }

    /**
     * Esamina due assegnazioni valide e ordinate nel tempo.
     * La lista vuota indica che questa coppia non viola il riposo minimo;
     * altrimenti il risultato identifica il dipendente, i turni e il motivo.
     */
    public List<ViolazioneTurno> verificaRiposo(
            AssegnazioneDaVerificare precedente,
            AssegnazioneDaVerificare successivo) {

        // Il riposo si confronta soltanto tra turni della stessa persona.
        if (precedente.dipendenteId() != successivo.dipendenteId()) {
            return List.of();
        }

        // Riutilizziamo il controllo delle 11 ore già verificato dai test.
        if (rispettaRiposoMinimo(precedente.fine(), successivo.inizio())) {
            return List.of();
        }

        // La durata serve a spiegare al titolare quanto riposo è disponibile.
        Duration riposoEffettivo =
                calcolaRiposo(precedente.fine(), successivo.inizio());

        String motivo = "Riposo di "
                + riposoEffettivo.toHours() + " ore e "
                + riposoEffettivo.toMinutesPart() + " minuti; minimo richiesto "
                + RIPOSO_MINIMO.toHours() + " ore.";

        // Il punto legge l'ID dal record precedente; una virgola separerebbe due argomenti.
        ViolazioneTurno violazione = new ViolazioneTurno(
                TipoViolazione.RIPOSO_INSUFFICIENTE,
                precedente.dipendenteId(),
                List.of(precedente, successivo),
                motivo);

        return List.of(violazione);
    }

    /**
     * Verifica il riposo su una lista di assegnazioni valide,
     * già controllate rispetto alle sovrapposizioni.
     * Non modifica l'elenco ricevuto dal chiamante.
     */
    public List<ViolazioneTurno> verificaRiposi(
            List<AssegnazioneDaVerificare> assegnazioni) {

        // Copiamo la lista per poterla ordinare senza cambiare quella originale.
        List<AssegnazioneDaVerificare> ordinate =
                new ArrayList<>(assegnazioni);

        // Prima riuniamo i turni della stessa persona,
        // poi li mettiamo in ordine di inizio.
        ordinate.sort(
                Comparator.comparingInt(AssegnazioneDaVerificare::dipendenteId)
                        .thenComparing(AssegnazioneDaVerificare::inizio));

        List<ViolazioneTurno> violazioni = new ArrayList<>();

        // Partiamo dal secondo elemento: per ogni turno serve quello precedente.
        for (int i = 1; i < ordinate.size(); i++) {
            AssegnazioneDaVerificare precedente = ordinate.get(i - 1);
            AssegnazioneDaVerificare successivo = ordinate.get(i);

            // Riutilizziamo il metodo della singola coppia.
            // Quel metodo ignora già le coppie di dipendenti diversi.
            violazioni.addAll(verificaRiposo(precedente, successivo));
        }

        // Restituiamo un risultato non modificabile.
        return List.copyOf(violazioni);
    }

    /**
     * Verifica il riposo della proposta considerando anche i turni confinanti.
     * La proposta contiene già il candidato: non lo aggiungiamo nuovamente.
     * Restituisce soltanto violazioni che coinvolgono almeno un turno proposto.
     * Il chiamante fornisce assegnazioni valide, senza sovrapposizioni per dipendente.
     * Dopo una modifica manuale la proposta deve contenere il turno nuovo al posto
     * del precedente. Il service verifica solo i dati ricevuti, senza leggere il DB:
     * con confinanti vuoti non può rilevare conflitti con turni non forniti.
     */
    public List<ViolazioneTurno> verificaRiposiProposta(
            List<AssegnazioneDaVerificare> proposta,
            List<AssegnazioneDaVerificare> confinanti) {

        // Conserviamo quali assegnazioni appartengono alla proposta.
        Set<AssegnazioneDaVerificare> turniProposti =
                new LinkedHashSet<>(proposta);

        // Uniamo i due elenchi eliminando soltanto le copie identiche.
        Set<AssegnazioneDaVerificare> tutti =
                new LinkedHashSet<>(turniProposti);
        tutti.addAll(confinanti);

        // Riutilizziamo ordinamento e controllo per dipendente già implementati.
        List<ViolazioneTurno> rilevate =
                verificaRiposi(new ArrayList<>(tutti));

        List<ViolazioneTurno> pertinenti = new ArrayList<>();

        for (ViolazioneTurno violazione : rilevate) {
            for (AssegnazioneDaVerificare turno : violazione.turniCoinvolti()) {
                if (turniProposti.contains(turno)) {
                    pertinenti.add(violazione);

                    // Una violazione va aggiunta una sola volta,
                    // anche quando entrambi i suoi turni sono nella proposta.
                    break;
                }
            }
        }

        return List.copyOf(pertinenti);
    }
    /**
     * Verifica se il dipendente ha almeno un giorno del piano libero
     * nella settimana da lunedì a domenica.
     * Usa giornoPiano: una cena domenicale appartiene alla domenica
     * anche quando termina dopo mezzanotte.
     */
    public boolean haRiposoSettimanale(
            int dipendenteId,
            LocalDate lunedi,
            List<AssegnazioneDaVerificare> assegnazioni) {

        // Evitiamo di controllare una settimana iniziata nel giorno sbagliato.
        if (lunedi == null || lunedi.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException(
                    "La settimana deve iniziare di lunedì.");
        }

        LocalDate lunediSuccessivo = lunedi.plusDays(7);

        // Un Set conta ogni giorno una sola volta, anche con pranzo e cena.
        Set<LocalDate> giorniOccupati = new HashSet<>();

        for (AssegnazioneDaVerificare assegnazione : assegnazioni) {
            LocalDate giorno = assegnazione.giornoPiano();

            // Consideriamo soltanto questo dipendente e questa settimana.
            if (assegnazione.dipendenteId() == dipendenteId
                    && !giorno.isBefore(lunedi)
                    && giorno.isBefore(lunediSuccessivo)) {
                giorniOccupati.add(giorno);
            }
        }

        // Meno di sette giorni occupati significa almeno un giorno libero.
        return giorniOccupati.size() < 7;
    }

    /**
     * Controlla due assegnazioni valide e ordinate nel tempo.
     * Vieta le fasce adiacenti dello stesso dipendente, indipendentemente
     * dal rispetto delle 11 ore di riposo: cena e pranzo standard del giorno
     * dopo rispettano le 11 ore, ma restano vietati da questa regola.
     * Restituisce una lista vuota se la coppia non viola questo vincolo.
     */
    public List<ViolazioneTurno> verificaTurniConsecutivi(
            AssegnazioneDaVerificare precedente,
            AssegnazioneDaVerificare successivo) {

        // Il divieto riguarda soltanto turni della stessa persona.
        if (precedente.dipendenteId() != successivo.dipendenteId()) {
            return List.of();
        }

        // Primo caso vietato: pranzo e cena dello stesso giorno del piano.
        boolean pranzoECenaStessoGiorno =
                precedente.servizio() == Servizio.PRANZO
                        && successivo.servizio() == Servizio.CENA
                        && precedente.giornoPiano().equals(successivo.giornoPiano());

        // Secondo caso vietato: cena e pranzo del giorno successivo.
        // Usiamo il giorno del piano: la cena resta assegnata alla domenica
        // anche quando termina lunedì alle 00:30.
        boolean cenaEPranzoGiornoDopo =
                precedente.servizio() == Servizio.CENA
                        && successivo.servizio() == Servizio.PRANZO
                        && precedente.giornoPiano().plusDays(1)
                        .equals(successivo.giornoPiano());

        // Il punto esclamativo significa "non" e && significa "e":
        // usciamo senza violazioni se nessuno dei due casi si verifica.
        if (!pranzoECenaStessoGiorno && !cenaEPranzoGiornoDopo) {
            return List.of();
        }

        // Identifichiamo la regola, la persona e i due turni coinvolti.
        // Il risultato potrà essere usato da generatore e modifica manuale.
        return List.of(new ViolazioneTurno(
                TipoViolazione.TURNI_CONSECUTIVI,
                precedente.dipendenteId(),
                List.of(precedente, successivo),
                "Non è consentito assegnare due fasce consecutive "
                        + "allo stesso dipendente."));
    }
    /**
     * Verifica le 11 ore di riposo e il divieto di fasce consecutive
     * sulla proposta, considerando anche le assegnazioni confinanti.
     * Riceve assegnazioni valide, senza sovrapposizioni per dipendente.
     * Il candidato è già nella proposta e non deve essere aggiunto di nuovo.
     * Monte ore e riposo settimanale saranno controllati separatamente.
     */
    public List<ViolazioneTurno> verificaVincoliTraTurniProposta(
            List<AssegnazioneDaVerificare> proposta,
            List<AssegnazioneDaVerificare> confinanti) {

        // Partiamo dalle violazioni delle 11 ore già calcolate
        // dal metodo esistente e pertinenti alla proposta.
        List<ViolazioneTurno> violazioni =
                new ArrayList<>(verificaRiposiProposta(proposta, confinanti));

        // Ricordiamo quali turni appartengono alla proposta.
        Set<AssegnazioneDaVerificare> turniProposti =
                new LinkedHashSet<>(proposta);

        // Uniamo proposta e confinanti eliminando le copie identiche.
        Set<AssegnazioneDaVerificare> tutti =
                new LinkedHashSet<>(turniProposti);
        tutti.addAll(confinanti);

        // Ordiniamo una copia, senza modificare gli elenchi ricevuti.
        List<AssegnazioneDaVerificare> ordinate = new ArrayList<>(tutti);
        ordinate.sort(
                Comparator.comparingInt(AssegnazioneDaVerificare::dipendenteId)
                        .thenComparing(AssegnazioneDaVerificare::inizio));

        // Esaminiamo ogni coppia adiacente nell'elenco ordinato.
        for (int i = 1; i < ordinate.size(); i++) {
            AssegnazioneDaVerificare precedente = ordinate.get(i - 1);
            AssegnazioneDaVerificare successivo = ordinate.get(i);

            // Il simbolo || significa "oppure": basta che uno dei due
            // turni appartenga alla proposta. Ignoriamo problemi tra soli confinanti.
            if (turniProposti.contains(precedente)
                    || turniProposti.contains(successivo)) {
                violazioni.addAll(
                        verificaTurniConsecutivi(precedente, successivo));
            }
        }

        // Restituiamo insieme le violazioni delle due regole.
        return List.copyOf(violazioni);
    }

    /**
     * Segnala l'assenza di un giorno libero nella settimana del piano.
     * Riutilizza il controllo booleano già verificato dai test.
     */
    public List<ViolazioneTurno> verificaRiposoSettimanale(
            int dipendenteId,
            LocalDate lunedi,
            List<AssegnazioneDaVerificare> assegnazioni) {

        // Se esiste almeno un giorno libero, non c'è una violazione.
        // Il metodo controlla anche che la data ricevuta sia un lunedì.
        if (haRiposoSettimanale(dipendenteId, lunedi, assegnazioni)) {
            return List.of();
        }

        LocalDate lunediSuccessivo = lunedi.plusDays(7);

        // Conserviamo soltanto i turni della persona nella settimana richiesta.
        // Il Set elimina eventuali copie identiche della stessa assegnazione.
        Set<AssegnazioneDaVerificare> coinvolti = new LinkedHashSet<>();

        for (AssegnazioneDaVerificare assegnazione : assegnazioni) {
            LocalDate giorno = assegnazione.giornoPiano();

            if (assegnazione.dipendenteId() == dipendenteId
                    && !giorno.isBefore(lunedi)
                    && giorno.isBefore(lunediSuccessivo)) {
                coinvolti.add(assegnazione);
            }
        }

        // Ordiniamo i turni per rendere la segnalazione facile da leggere.
        List<AssegnazioneDaVerificare> ordinati = new ArrayList<>(coinvolti);
        ordinati.sort(Comparator.comparing(AssegnazioneDaVerificare::inizio));

        return List.of(new ViolazioneTurno(
                TipoViolazione.RIPOSO_SETTIMANALE_ASSENTE,
                dipendenteId,
                ordinati,
                "Nella settimana dal " + lunedi
                        + " al " + lunedi.plusDays(6)
                        + " il dipendente non ha alcun giorno del piano libero."));
    }

    /**
     * Somma i minuti assegnati a un dipendente nella settimana del piano.
     * Riceve assegnazioni valide, con orari espressi al minuto.
     *
     * Ogni turno conta interamente nella settimana del suo giornoPiano:
     * la cena domenicale comprende anche i 30 minuti dopo mezzanotte.
     * Le copie identiche della stessa assegnazione vengono contate una sola volta.
     */
    public long calcolaMinutiSettimanali(
            int dipendenteId,
            LocalDate lunedi,
            List<AssegnazioneDaVerificare> assegnazioni) {

        // La settimana deve andare da lunedì a domenica.
        if (lunedi == null || lunedi.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException(
                    "La settimana deve iniziare di lunedì.");
        }

        LocalDate lunediSuccessivo = lunedi.plusDays(7);
        long minutiTotali = 0;

        // Evitiamo di contare due volte eventuali copie identiche.
        Set<AssegnazioneDaVerificare> turniUnici =
                new LinkedHashSet<>(assegnazioni);

        for (AssegnazioneDaVerificare assegnazione : turniUnici) {
            LocalDate giorno = assegnazione.giornoPiano();

            // Selezioniamo solo questa persona e questa settimana del piano.
            if (assegnazione.dipendenteId() == dipendenteId
                    && !giorno.isBefore(lunedi)
                    && giorno.isBefore(lunediSuccessivo)) {

                // Usiamo date e ore complete: la cena attraversa la mezzanotte.
                long minutiTurno = Duration.between(
                        assegnazione.inizio(),
                        assegnazione.fine()).toMinutes();

                minutiTotali += minutiTurno;
            }
        }

        return minutiTotali;
    }

    /**
     * Ingresso compatibile per chi dispone di un solo contratto.
     * Delega alla verifica con storico: limiti, date e messaggi hanno
     * una sola implementazione, anche quando il contratto non cambia.
     */
    public List<ViolazioneTurno> verificaMonteOreSettimanale(
            int dipendenteId,
            LocalDate lunedi,
            List<AssegnazioneDaVerificare> assegnazioni,
            Contratto contratto) {

        if (contratto == null || contratto.getDipendenteId() != dipendenteId) {
            throw new IllegalArgumentException(
                    "Serve un contratto del dipendente da verificare.");
        }

        // La lista con un solo elemento consente di riusare la stessa verifica.
        return verificaMonteOreConStorico(
                dipendenteId, lunedi, assegnazioni, List.of(contratto));
    }

    /**
     * Seleziona un unico contratto che copra l'intera settimana del piano.
     * I cambi programmati da US02 dovranno iniziare il primo lunedì del mese
     * successivo: ogni settimana usa quindi il limite intero di un contratto.
     * Contratti passati/futuri esterni alla settimana e altre persone sono ignorati.
     * I casi di assunzione/cessazione con settimana scoperta restano da definire.
     */
    private Contratto trovaContrattoDellaSettimana(
            int dipendenteId, LocalDate lunedi, List<Contratto> contratti) {

        LocalDate domenica = lunedi.plusDays(6);
        Contratto selezionato = null;
        for (Contratto contratto : contratti) {
            if (contratto.getDipendenteId() != dipendenteId) {
                continue;
            }
            if (contratto.getDataInizio() == null) {
                throw new IllegalArgumentException("Il contratto deve avere una data di inizio.");
            }

            // Intersezione fra periodi inclusivi: consideriamo tutti i tipi di contratto.
            boolean interessaSettimana = !contratto.getDataInizio().isAfter(domenica)
                    && (contratto.getDataFine() == null
                    || !contratto.getDataFine().isBefore(lunedi));
            if (interessaSettimana) {
                if (selezionato != null) {
                    throw new IllegalArgumentException(
                            "Più contratti interessano la settimana del dipendente "
                                    + dipendenteId + ": verificare sovrapposizioni o cambi a metà settimana.");
                }
                selezionato = contratto;
            }
        }

        if (selezionato == null || !selezionato.isValidoIl(lunedi)
                || !selezionato.isValidoIl(domenica)) {
            throw new IllegalArgumentException(
                    "Serve un contratto valido per tutta la settimana del dipendente " + dipendenteId);
        }
        List<String> errori = selezionato.valida();
        if (!errori.isEmpty()) {
            throw new IllegalArgumentException("Contratto non valido: " + String.join("; ", errori));
        }
        return selezionato;
    }
    /**
     * Verifica le ore assegnate usando lo storico contrattuale ricevuto.
     * Usa il limite intero dell'unico contratto valido per tutta la settimana.
     * La decorrenza dei cambi futuri viene stabilita da US02, non da questo controllo.
     */
    public List<ViolazioneTurno> verificaMonteOreConStorico(
            int dipendenteId,
            LocalDate lunedi,
            List<AssegnazioneDaVerificare> assegnazioni,
            List<Contratto> contratti) {

        // Quanto lavoro è stato assegnato nella settimana del piano.
        long minutiAssegnati =
                calcolaMinutiSettimanali(dipendenteId, lunedi, assegnazioni);

        // Il limite è settimanale e non viene ripartito tra più contratti.
        Contratto contratto = trovaContrattoDellaSettimana(dipendenteId, lunedi, contratti);
        long limiteMinuti = contratto.getOreSettimanali() * 60L;

        // Il limite esatto è consentito; segnaliamo soltanto il superamento.
        if (minutiAssegnati <= limiteMinuti) {
            return List.of();
        }

        LocalDate lunediSuccessivo = lunedi.plusDays(7);
        Set<AssegnazioneDaVerificare> coinvolti = new LinkedHashSet<>();

        // Conserviamo i turni della persona nella settimana controllata.
        // Le copie identiche vengono raccolte una volta sola.
        for (AssegnazioneDaVerificare assegnazione : assegnazioni) {
            LocalDate giorno = assegnazione.giornoPiano();

            if (assegnazione.dipendenteId() == dipendenteId
                    && !giorno.isBefore(lunedi)
                    && giorno.isBefore(lunediSuccessivo)) {
                coinvolti.add(assegnazione);
            }
        }

        List<AssegnazioneDaVerificare> ordinati = new ArrayList<>(coinvolti);
        ordinati.sort(Comparator.comparing(AssegnazioneDaVerificare::inizio));

        // Presentiamo durata assegnata e limite nella stessa unità leggibile.
        String motivo = "Nella settimana dal " + lunedi
                + " risultano assegnate " + (minutiAssegnati / 60)
                + " ore e " + (minutiAssegnati % 60)
                + " minuti; il limite applicabile è "
                + (limiteMinuti / 60) + " ore e "
                + (limiteMinuti % 60) + " minuti.";

        return List.of(new ViolazioneTurno(
                TipoViolazione.MONTE_ORE_SUPERATO,
                dipendenteId,
                ordinati,
                motivo));
    }


}
