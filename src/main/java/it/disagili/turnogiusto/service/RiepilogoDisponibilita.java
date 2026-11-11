package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Disponibilita;

import java.util.List;

/** Una riga della consultazione: esiste anche quando il dipendente non ha fasce. */
public class RiepilogoDisponibilita {

    private final Dipendente dipendente;
    private final List<Disponibilita> finestre;

    public RiepilogoDisponibilita(Dipendente dipendente, List<Disponibilita> finestre) {
        this.dipendente = dipendente;
        this.finestre = List.copyOf(finestre);
    }

    public Dipendente getDipendente() {
        return dipendente;
    }

    public List<Disponibilita> getFinestre() {
        return finestre;
    }

    public String getTestoDisponibilita() {
        if (finestre.isEmpty()) {
            return "senza disponibilità inserite";
        }
        StringBuilder testo = new StringBuilder();
        for (Disponibilita finestra : finestre) {
            if (!testo.isEmpty()) {
                testo.append("\n");
            }
            testo.append(finestra);
        }
        return testo.toString();
    }
}
