package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Disponibilita;
import it.disagili.turnogiusto.repository.AccessoDipendentiDisponibilita;
import it.disagili.turnogiusto.repository.Database;
import it.disagili.turnogiusto.repository.DipendentiDisponibilitaJdbc;
import it.disagili.turnogiusto.repository.DisponibilitaRepository;
import it.disagili.turnogiusto.repository.FornitoreConnessioni;
import it.disagili.turnogiusto.repository.SalvataggioDisponibilitaRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Coordina le due US senza mettere SQL nei controller o modificare i modelli. */
public class DisponibilitaService {

    private final AccessoDipendentiDisponibilita dipendenti;
    private final DisponibilitaRepository lettura;
    private final SalvataggioDisponibilitaRepository scrittura;

    public DisponibilitaService() {
        this(new DipendentiDisponibilitaJdbc());
    }

    /** Qui si potra passare l'adattatore del repository di Antonio. */
    public DisponibilitaService(AccessoDipendentiDisponibilita dipendenti) {
        this(dipendenti, Database::getConnection);
    }

    /** Permette ai test di usare un database dedicato senza cambiare la configurazione dell'app. */
    public DisponibilitaService(AccessoDipendentiDisponibilita dipendenti, FornitoreConnessioni connessioni) {
        this.dipendenti = Objects.requireNonNull(dipendenti);
        this.lettura = new DisponibilitaRepository(connessioni);
        this.scrittura = new SalvataggioDisponibilitaRepository(dipendenti, connessioni);
    }

    public List<Dipendente> trovaPartTime() throws SQLException {
        return selezionaPartTime(dipendenti.trovaTutti(), LocalDate.now());
    }

    public List<Dipendente> selezionaPartTime(List<Dipendente> tutti, LocalDate data)
            throws SQLException {
        List<Dipendente> partTime = new ArrayList<>();
        Set<Integer> idLetti = new HashSet<>();
        for (Dipendente dipendente : tutti) {
            if (dipendente == null || dipendente.getId() <= 0 || !idLetti.add(dipendente.getId())) {
                throw new SQLException("Elenco dipendenti incoerente: ID assente o ripetuto.");
            }
            if (dipendente.isPartTime() && dipendente.getContratto().isValidoIl(data)) {
                partTime.add(dipendente);
            }
        }
        return partTime;
    }

    public List<Disponibilita> caricaFinestre(int dipendenteId) throws SQLException {
        return lettura.trovaPerDipendente(dipendenteId);
    }

    public void salva(int dipendenteId, List<Disponibilita> finestre) throws SQLException {
        scrittura.sostituisciPerDipendente(dipendenteId, finestre);
    }

    public List<RiepilogoDisponibilita> consulta() throws SQLException {
        return consulta(trovaPartTime());
    }

    /** Anche la finestra principale puo passare direttamente l'elenco di US-01. */
    public List<RiepilogoDisponibilita> consulta(List<Dipendente> dipendenti) throws SQLException {
        List<RiepilogoDisponibilita> riepiloghi = new ArrayList<>();
        for (Dipendente dipendente : selezionaPartTime(dipendenti, LocalDate.now())) {
            riepiloghi.add(new RiepilogoDisponibilita(dipendente, caricaFinestre(dipendente.getId())));
        }
        return riepiloghi;
    }
}
