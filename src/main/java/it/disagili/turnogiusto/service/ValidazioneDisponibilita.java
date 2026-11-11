package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Disponibilita;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/** Controlli condivisi dalla schermata e dal salvataggio; non accede al database. */
public final class ValidazioneDisponibilita {

    private static final DateTimeFormatter FORMATO_ORA =
            DateTimeFormatter.ofPattern("HH:mm[:ss]").withResolverStyle(ResolverStyle.STRICT);

    private ValidazioneDisponibilita() {
    }

    public static LocalTime leggiOra(String testo) {
        try {
            return LocalTime.parse(testo == null ? "" : testo.trim(), FORMATO_ORA);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Inserisci un orario valido: HH:mm, ad esempio 18:30.");
        }
    }

    public static void verifica(int dipendenteId, List<Disponibilita> finestre) {
        if (dipendenteId <= 0) {
            throw new IllegalArgumentException("Seleziona un dipendente già registrato.");
        }
        if (finestre == null) {
            throw new IllegalArgumentException("L'elenco delle disponibilità è obbligatorio.");
        }
        for (int i = 0; i < finestre.size(); i++) {
            Disponibilita finestra = finestre.get(i);
            if (finestra == null || finestra.getDipendenteId() != dipendenteId) {
                throw new IllegalArgumentException("Ogni fascia deve appartenere al dipendente selezionato.");
            }
            List<String> errori = finestra.valida();
            if (!errori.isEmpty()) {
                throw new IllegalArgumentException(String.join(". ", errori));
            }
            // La tabella usa TIME senza frazioni di secondo: evitiamo troncamenti silenziosi.
            if (finestra.getOraInizio().getNano() != 0 || finestra.getOraFine().getNano() != 0) {
                throw new IllegalArgumentException("Gli orari non possono contenere frazioni di secondo.");
            }
            for (int j = 0; j < i; j++) {
                Disponibilita precedente = finestre.get(j);
                // equals() del modello confronta gli ID, non giorno e orari.
                if (finestra.getGiornoSettimana() == precedente.getGiornoSettimana()
                        && finestra.getOraInizio().equals(precedente.getOraInizio())
                        && finestra.getOraFine().equals(precedente.getOraFine())) {
                    throw new IllegalArgumentException("La stessa fascia è già presente nell'elenco.");
                }
            }
        }
    }
}
