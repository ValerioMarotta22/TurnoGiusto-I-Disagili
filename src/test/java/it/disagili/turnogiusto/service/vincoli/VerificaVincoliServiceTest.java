package it.disagili.turnogiusto.service.vincoli;

import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.TipoContratto;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test dei vincoli US07 con dati costruiti in memoria.
 * Non richiedono JavaFX o una connessione al database.
 * Le prove distinguono l'esito del controllo (true/false) dalla durata effettiva,
 * che servirà a spiegare al titolare perché il riposo è insufficiente.
 * Verificano il calcolo del riposo e le violazioni sulle nostre assegnazioni locali.
 * Il pranzo standard è 11:30-16:00; la cena è 18:00-00:30 del giorno dopo.
 * Alcune prove usano intenzionalmente un pranzo anticipato alle 11:00 o alle 11:29
 * per verificare il rifiuto di un riposo inferiore a 11 ore: non sono fasce standard.
 * I test del riposo restano separati da quelli del divieto di fasce consecutive:
 * cena e pranzo standard del giorno dopo rispettano le 11 ore, ma violano il secondo vincolo.
 * I tipi condivisi di US06A non sono ancora disponibili in questo ramo:
 * queste prove non attestano il collegamento reale con proposta/confinanti.
 */
class VerificaVincoliServiceTest {

    @Test
    void dieciOreTraDomenicaELunediNonRispettanoIlRiposoMinimo() {

        // PREPARAZIONE: Anna termina domenica alle 23:00 e ricomincia lunedì alle 09:00.
        // LocalDateTime contiene data e ora; le date fisse rendono il test ripetibile.
        LocalDateTime finePrimoTurno =
                LocalDateTime.of(2026, 11, 22, 23, 0);

        LocalDateTime inizioSecondoTurno =
                LocalDateTime.of(2026, 11, 23, 9, 0);

        VerificaVincoliService verificatore = new VerificaVincoliService();

        // ESECUZIONE: chiediamo al servizio se il riposo tra i due turni è sufficiente.
        boolean riposoRispettato = verificatore.rispettaRiposoMinimo(
                finePrimoTurno, inizioSecondoTurno);

        // VERIFICA: dieci ore sono insufficienti, quindi il risultato deve essere false.
        // La virgola separa il risultato da controllare dal messaggio mostrato se il test fallisce.
        assertFalse(riposoRispettato,
                "Dieci ore di riposo non rispettano il minimo di undici");
    }

    @Test
    void cenaDomenicaEPranzoStandardLunediRispettanoIlRiposoMinimo() {
        LocalDate domenica = LocalDate.of(2026, 11, 22);
        LocalDate lunedi = domenica.plusDays(1);

        // Anna ha ID 5. La cena sarà fornita in confinanti e il pranzo in proposta
        // quando saranno disponibili i tipi condivisi e il punto di collegamento US06A.
        // La cena resta nel piano della domenica, ma finisce lunedì.
        AssegnazioneDaVerificare cenaDomenica =
                new AssegnazioneDaVerificare(
                        5,
                        domenica,
                        Servizio.CENA,
                        domenica.atTime(18, 0),
                        lunedi.atTime(0, 30));

        AssegnazioneDaVerificare pranzoLunedi =
                new AssegnazioneDaVerificare(
                        5,
                        lunedi,
                        Servizio.PRANZO,
                        lunedi.atTime(11, 30),
                        lunedi.atTime(16, 0));

        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Riutilizziamo il record locale già scritto come dato di prova.
        // Il metodo attuale riceve le date: non introduciamo un'altra API provvisoria.
        boolean riposoRispettato = verificatore.rispettaRiposoMinimo(
                cenaDomenica.fine(), pranzoLunedi.inizio());

        // Dalle 00:30 alle 11:30 passano esattamente 11 ore.
        // L'eventuale divieto di fasce consecutive è un controllo distinto.
        assertTrue(riposoRispettato,
                "Cena e pranzo standard del giorno dopo rispettano le undici ore di riposo");
    }

    @Test
    void cenaDomenicaECenaLunediRispettanoIlRiposoMinimo() {
        // Dalla fine della cena domenicale all'inizio di quella del lunedì
        // trascorrono 17 ore e 30 minuti: la soglia è rispettata.
        LocalDateTime fineCenaDomenica = LocalDateTime.of(2026, 11, 23, 0, 30);
        LocalDateTime inizioCenaLunedi = LocalDateTime.of(2026, 11, 23, 18, 0);

        VerificaVincoliService verificatore = new VerificaVincoliService();

        assertTrue(verificatore.rispettaRiposoMinimo(
                        fineCenaDomenica, inizioCenaLunedi),
                "Diciassette ore e trenta minuti rispettano il riposo minimo");
    }

    @Test
    void undiciOreEsatteTraDomenicaELunediRispettanoIlRiposoMinimo() {
        LocalDate domenica = LocalDate.of(2026, 11, 22);
        LocalDate lunedi = domenica.plusDays(1);

        AssegnazioneDaVerificare cenaDomenica =
                new AssegnazioneDaVerificare(
                        5,
                        domenica,
                        Servizio.CENA,
                        domenica.atTime(18, 0),
                        lunedi.atTime(0, 30));

        // Il pranzo standard delle 11:30 coincide anche con la soglia esatta:
        // dalla fine della cena alle 00:30 trascorrono precisamente 11 ore.
        AssegnazioneDaVerificare turnoDiProvaLunedi =
                new AssegnazioneDaVerificare(
                        5,
                        lunedi,
                        Servizio.PRANZO,
                        lunedi.atTime(11, 30),
                        lunedi.atTime(16, 0));

        VerificaVincoliService verificatore = new VerificaVincoliService();

        // ESECUZIONE: controlliamo il riposo tra i due turni.
        boolean riposoRispettato = verificatore.rispettaRiposoMinimo(
                cenaDomenica.fine(), turnoDiProvaLunedi.inizio());

        // VERIFICA: esattamente 11 ore soddisfano il minimo richiesto.
        assertTrue(riposoRispettato,
                "Undici ore esatte di riposo devono essere accettate");
    }

    @Test
    void unMinutoInMenoDiUndiciOreNonRispettaIlRiposoMinimo() {
        // La cena termina lunedì alle 00:30.
        LocalDateTime finePrimoTurno =
                LocalDateTime.of(2026, 11, 23, 0, 30);

        // Orario usato soltanto per questa prova:
        // alle 11:29 sono trascorse 10 ore e 59 minuti.
        // Il pranzo standard inizia alle 11:30: qui lo anticipiamo di un minuto.
        LocalDateTime inizioSecondoTurno =
                LocalDateTime.of(2026, 11, 23, 11, 29);

        VerificaVincoliService verificatore = new VerificaVincoliService();

        boolean riposoRispettato = verificatore.rispettaRiposoMinimo(
                finePrimoTurno, inizioSecondoTurno);

        assertFalse(riposoRispettato,
                "Anche un solo minuto mancante rende il riposo insufficiente");
    }

    @Test
    void calcolaDieciOreETrentaMinutiConUnPranzoAnticipato() {
        // PREPARAZIONE: la cena domenicale termina il lunedì alle 00:30;
        // usiamo intenzionalmente un pranzo anticipato alle 11:00, fuori dalla fascia standard.
        LocalDateTime fineCena = LocalDateTime.of(2026, 11, 23, 0, 30);
        LocalDateTime inizioPranzo = LocalDateTime.of(2026, 11, 23, 11, 0);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // ESECUZIONE: chiediamo la durata, non soltanto se supera la soglia.
        Duration riposoEffettivo = verificatore.calcolaRiposo(fineCena, inizioPranzo);

        // VERIFICA: 10 * 60 + 30 = 630 minuti. assertEquals riceve prima
        // il valore atteso e poi il risultato da controllare.
        assertEquals(Duration.ofMinutes(630), riposoEffettivo,
                "Il riposo tra cena e pranzo deve essere di 10 ore e 30 minuti");
    }

    @Test
    void calcolaDiciassetteOreETrentaMinutiTraDueCene() {
        // Anche quando il riposo è sufficiente, il calcolo deve restituire
        // l'intera durata disponibile: non deve limitarla al minimo di 11 ore.
        LocalDateTime fineCenaDomenica = LocalDateTime.of(2026, 11, 23, 0, 30);
        LocalDateTime inizioCenaLunedi = LocalDateTime.of(2026, 11, 23, 18, 0);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        Duration riposoEffettivo = verificatore.calcolaRiposo(
                fineCenaDomenica, inizioCenaLunedi);

        // 17 * 60 + 30 = 1050 minuti.
        assertEquals(Duration.ofMinutes(1050), riposoEffettivo,
                "Il riposo tra le due cene deve conservare tutte le 17 ore e 30 minuti");
    }

    @Test
    void calcolaSeicentosessantaMinutiAlLimiteEsatto() {
        // Il pranzo standard delle 11:30 raggiunge esattamente la soglia.
        LocalDateTime finePrimoTurno = LocalDateTime.of(2026, 11, 23, 0, 30);
        LocalDateTime inizioSecondoTurno = LocalDateTime.of(2026, 11, 23, 11, 30);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        Duration riposoEffettivo = verificatore.calcolaRiposo(
                finePrimoTurno, inizioSecondoTurno);

        // 11 * 60 = 660 minuti, da usare anche come riferimento nella spiegazione.
        assertEquals(Duration.ofMinutes(660), riposoEffettivo,
                "Undici ore esatte devono corrispondere a 660 minuti");
    }

    @Test
    void calcolaRiposoConservandoAncheIGiorniInteri() {
        // Anna termina lunedì alle 00:30 e torna per il pranzo del martedì.
        // Il calcolo deve includere le 24 ore del giorno intero trascorso,
        // oltre alle 11 ore: confrontare solo gli orari non basta.
        LocalDateTime fineCena = LocalDateTime.of(2026, 11, 23, 0, 30);
        LocalDateTime inizioPranzoMartedi = LocalDateTime.of(2026, 11, 24, 11, 30);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        Duration riposoEffettivo = verificatore.calcolaRiposo(
                fineCena, inizioPranzoMartedi);

        // 35 * 60 = 2100 minuti; la durata non si azzera dopo 24 ore.
        assertEquals(Duration.ofMinutes(2100), riposoEffettivo,
                "Il riposo deve includere anche i giorni interi tra i due turni");
    }

    @Test
    void segnalaDipendenteTurniEMotivoDelRiposoInsufficiente() {
        // Anna, ID 5: cena domenicale e pranzo anticipato alle 11:00 per questa prova.
        // Le 10 ore e 30 minuti permettono di verificare i dettagli della violazione.
        AssegnazioneDaVerificare cena = cenaDomenicaleDi(5);
        AssegnazioneDaVerificare pranzo = pranzoDiLunedi(5, 11, 0);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        List<ViolazioneTurno> violazioni = verificatore.verificaRiposo(cena, pranzo);

        // Il risultato deve essere utilizzabile sia dal generatore sia dalla modifica
        // manuale: non basta una stringa priva del dipendente o dei turni coinvolti.
        assertEquals(1, violazioni.size(), "La coppia deve produrre una sola violazione");
        ViolazioneTurno violazione = violazioni.getFirst();
        assertEquals(TipoViolazione.RIPOSO_INSUFFICIENTE, violazione.tipo());
        assertEquals(5, violazione.dipendenteId());
        assertEquals(List.of(cena, pranzo), violazione.turniCoinvolti());
        assertTrue(violazione.motivo().contains("10 ore e 30 minuti"),
                "La spiegazione deve riportare il riposo effettivo senza perdere i minuti");
        assertTrue(violazione.motivo().contains("11 ore"),
                "La spiegazione deve riportare anche la soglia richiesta");
    }

    @Test
    void nonProduceViolazioniTraCenaDomenicaleECenaDelLunedi() {
        AssegnazioneDaVerificare cenaDomenica = cenaDomenicaleDi(5);
        LocalDate lunedi = LocalDate.of(2026, 11, 23);
        AssegnazioneDaVerificare cenaLunedi = new AssegnazioneDaVerificare(
                5, lunedi, Servizio.CENA, lunedi.atTime(18, 0),
                lunedi.plusDays(1).atTime(0, 30));
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // La lista vuota significa nessuna violazione del riposo per questa coppia,
        // non che l'intero piano abbia superato tutti i controlli US07.
        assertTrue(verificatore.verificaRiposo(cenaDomenica, cenaLunedi).isEmpty(),
                "Diciassette ore e trenta minuti non devono generare violazioni");
    }

    @Test
    void nonProduceViolazioniConUndiciOreEsatte() {
        AssegnazioneDaVerificare cena = cenaDomenicaleDi(5);
        // Il pranzo standard delle 11:30 rispetta esattamente la soglia del riposo.
        AssegnazioneDaVerificare turnoDiProva = pranzoDiLunedi(5, 11, 30);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        assertTrue(verificatore.verificaRiposo(cena, turnoDiProva).isEmpty(),
                "Il limite esatto di undici ore deve essere accettato anche nell'esito strutturato");
    }

    @Test
    void nonConfrontaIlRiposoDiDueDipendentiDiversi() {
        AssegnazioneDaVerificare cenaDiAnna = cenaDomenicaleDi(5);
        AssegnazioneDaVerificare pranzoDiUnAltroDipendente = pranzoDiLunedi(6, 11, 0);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Il riposo è un vincolo individuale: la cena di Anna non riduce il riposo
        // di un altro dipendente. Gli ID, non i nomi, distinguono le persone.
        assertTrue(verificatore.verificaRiposo(cenaDiAnna, pranzoDiUnAltroDipendente).isEmpty(),
                "Turni di persone diverse non devono produrre una violazione di riposo tra loro");
    }

    @Test
    void laViolazioneConservaITurniAncheSeLaListaOriginaleCambia() {
        AssegnazioneDaVerificare cena = cenaDomenicaleDi(5);
        AssegnazioneDaVerificare pranzo = pranzoDiLunedi(5, 11, 0);
        List<AssegnazioneDaVerificare> turniOriginali = new ArrayList<>(List.of(cena, pranzo));
        ViolazioneTurno violazione = new ViolazioneTurno(
                TipoViolazione.RIPOSO_INSUFFICIENTE, 5, turniOriginali,
                "Riposo di 10 ore e 30 minuti; minimo richiesto 11 ore.");

        // Simuliamo il chiamante che modifica il proprio elenco dopo la verifica.
        // Il risultato già ottenuto deve continuare a descrivere la coppia originale.
        turniOriginali.clear();
        assertEquals(List.of(cena, pranzo), violazione.turniCoinvolti());
        assertThrows(UnsupportedOperationException.class,
                () -> violazione.turniCoinvolti().clear(),
                "Nemmeno chi legge il risultato deve poter svuotare i turni della violazione");
    }

    @Test
    void ordinaPerDipendenteEDataSenzaModificareLaListaRicevuta() {
        AssegnazioneDaVerificare cenaDiAnna = cenaDomenicaleDi(5);
        AssegnazioneDaVerificare pranzoDiAnna = pranzoDiLunedi(5, 11, 0);
        AssegnazioneDaVerificare pranzoDiUnAltroDipendente = pranzoDiLunedi(6, 11, 0);

        // L'elenco è volutamente disordinato. Il turno dell'altra persona non deve
        // nascondere il collegamento tra i due turni di Anna, né generare falsi errori.
        List<AssegnazioneDaVerificare> assegnazioni = new ArrayList<>(List.of(
                pranzoDiUnAltroDipendente, pranzoDiAnna, cenaDiAnna));
        List<AssegnazioneDaVerificare> ordineOriginale = List.copyOf(assegnazioni);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        List<ViolazioneTurno> violazioni = verificatore.verificaRiposi(assegnazioni);

        assertEquals(1, violazioni.size());
        assertEquals(5, violazioni.getFirst().dipendenteId());
        assertEquals(List.of(cenaDiAnna, pranzoDiAnna), violazioni.getFirst().turniCoinvolti());
        assertEquals(ordineOriginale, assegnazioni,
                "La verifica non deve riordinare la lista posseduta dal chiamante");
    }

    @Test
    void nessunaViolazioneConUnaListaVuotaOUnSoloTurno() {
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Il generatore costruisce il piano progressivamente: senza una coppia
        // di turni non c'è un intervallo di riposo da verificare in questo elenco.
        assertTrue(verificatore.verificaRiposi(List.of()).isEmpty());
        assertTrue(verificatore.verificaRiposi(List.of(cenaDomenicaleDi(5))).isEmpty());
    }

    @Test
    void verificaTutteLeCoppieConsecutiveDelloStessoDipendente() {
        AssegnazioneDaVerificare cenaDomenica = cenaDomenicaleDi(5);
        AssegnazioneDaVerificare pranzoLunedi = pranzoDiLunedi(5, 11, 0);
        LocalDate lunedi = LocalDate.of(2026, 11, 23);
        AssegnazioneDaVerificare cenaLunedi = new AssegnazioneDaVerificare(
                5, lunedi, Servizio.CENA, lunedi.atTime(18, 0),
                lunedi.plusDays(1).atTime(0, 30));
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Anche partendo da un elenco invertito dobbiamo verificare entrambe le coppie:
        // 10 ore e 30 minuti tra cena/pranzo e 2 ore tra pranzo/cena del lunedì.
        List<ViolazioneTurno> violazioni = verificatore.verificaRiposi(
                List.of(cenaLunedi, pranzoLunedi, cenaDomenica));

        assertEquals(2, violazioni.size(), "Non bisogna fermarsi alla prima violazione");
        assertEquals(List.of(cenaDomenica, pranzoLunedi), violazioni.get(0).turniCoinvolti());
        assertEquals(List.of(pranzoLunedi, cenaLunedi), violazioni.get(1).turniCoinvolti());
    }

    @Test
    void nessunaViolazioneConRiposiSufficientiEDipendentiDistinti() {
        AssegnazioneDaVerificare cenaDomenica = cenaDomenicaleDi(5);
        LocalDate lunedi = LocalDate.of(2026, 11, 23);
        AssegnazioneDaVerificare cenaLunedi = new AssegnazioneDaVerificare(
                5, lunedi, Servizio.CENA, lunedi.atTime(18, 0),
                lunedi.plusDays(1).atTime(0, 30));
        AssegnazioneDaVerificare pranzoDiUnAltroDipendente = pranzoDiLunedi(6, 11, 0);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Anna ha 17 ore e 30 minuti di riposo; il pranzo appartiene a un'altra persona.
        // L'assenza di errori riguarda soltanto il riposo sulle assegnazioni fornite.
        assertTrue(verificatore.verificaRiposi(
                List.of(cenaLunedi, pranzoDiUnAltroDipendente, cenaDomenica)).isEmpty());
    }

    @Test
    void segnalaIlPranzoAnticipatoUsandoLaCenaDomenicaleTraIConfinanti() {
        AssegnazioneDaVerificare cena = cenaDomenicaleDi(5);
        AssegnazioneDaVerificare pranzo = pranzoDiLunedi(5, 11, 0);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Il pranzo è già nella proposta: non aggiungiamo un candidato separato.
        // È anticipato alle 11:00 per ottenere 10 ore e 30 minuti di riposo.
        // La cena viene fornita direttamente in memoria, senza leggere uno storico dal DB.
        List<ViolazioneTurno> violazioni = verificatore.verificaRiposiProposta(
                List.of(pranzo), List.of(cena));

        assertEquals(1, violazioni.size());
        ViolazioneTurno violazione = violazioni.getFirst();
        assertEquals(5, violazione.dipendenteId());
        assertEquals(TipoViolazione.RIPOSO_INSUFFICIENTE, violazione.tipo());
        assertEquals(List.of(cena, pranzo), violazione.turniCoinvolti());
        assertTrue(violazione.motivo().contains("10 ore e 30 minuti"));
        assertTrue(violazione.motivo().contains("11 ore"));
    }

    @Test
    void laPropostaConUndiciOreEsatteNonViolaIlRiposoRispettoAiConfinanti() {
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Il pranzo standard delle 11:30 lascia 11 ore di riposo dalla cena confinante.
        // Questo esito riguarda il riposo, non il divieto separato di fasce consecutive.
        assertTrue(verificatore.verificaRiposiProposta(
                List.of(pranzoDiLunedi(5, 11, 30)),
                List.of(cenaDomenicaleDi(5))).isEmpty());
    }

    @Test
    void laStessaAssegnazioneNonVieneVerificataControUnaSuaCopia() {
        AssegnazioneDaVerificare cena = cenaDomenicaleDi(5);
        AssegnazioneDaVerificare pranzo = pranzoDiLunedi(5, 11, 0);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Gli helper creano oggetti distinti ma con tutti i valori uguali.
        // Se l'unione contiene copie della stessa assegnazione, non devono comparire
        // falsi riposi negativi confrontando un turno con la sua copia.
        List<ViolazioneTurno> violazioni = verificatore.verificaRiposiProposta(
                List.of(pranzo, pranzoDiLunedi(5, 11, 0)),
                List.of(cena, cenaDomenicaleDi(5), pranzoDiLunedi(5, 11, 0)));

        assertEquals(1, violazioni.size());
        assertEquals(List.of(cena, pranzo), violazioni.getFirst().turniCoinvolti());
    }

    @Test
    void nonAttribuisceAllaPropostaUnaViolazioneTraSoliConfinanti() {
        LocalDate domenica = LocalDate.of(2026, 11, 22);
        LocalDate lunedi = domenica.plusDays(1);
        AssegnazioneDaVerificare pranzoDomenica = new AssegnazioneDaVerificare(
                5, domenica, Servizio.PRANZO, domenica.atTime(11, 30), domenica.atTime(16, 0));
        AssegnazioneDaVerificare cenaDomenica = cenaDomenicaleDi(5);
        AssegnazioneDaVerificare cenaLunedi = new AssegnazioneDaVerificare(
                5, lunedi, Servizio.CENA, lunedi.atTime(18, 0),
                lunedi.plusDays(1).atTime(0, 30));
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // I due turni domenicali hanno solo 2 ore di riposo, ma sono entrambi confinanti.
        // La cena proposta del lunedì ha invece 17 ore e 30 minuti di riposo:
        // il problema precedente non deve far rifiutare questa proposta.
        assertTrue(verificatore.verificaRiposiProposta(
                List.of(cenaLunedi), List.of(pranzoDomenica, cenaDomenica)).isEmpty());
    }

    @Test
    void controllaAncheUnPranzoAnticipatoConfinanteSuccessivoAllaProposta() {
        LocalDate domenica = LocalDate.of(2026, 11, 29);
        LocalDate lunediSeguente = domenica.plusDays(1);
        AssegnazioneDaVerificare cenaProposta = new AssegnazioneDaVerificare(
                5, domenica, Servizio.CENA, domenica.atTime(18, 0),
                lunediSeguente.atTime(0, 30));
        AssegnazioneDaVerificare pranzoGiaAssegnato = new AssegnazioneDaVerificare(
                5, lunediSeguente, Servizio.PRANZO, lunediSeguente.atTime(11, 0),
                lunediSeguente.atTime(16, 0));
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // I confinanti possono essere precedenti o successivi al periodo proposto.
        // Anche il riposo con il turno già assegnato nella settimana seguente conta.
        // Il pranzo delle 11:00 è anticipato appositamente per ottenere una violazione.
        List<ViolazioneTurno> violazioni = verificatore.verificaRiposiProposta(
                List.of(cenaProposta), List.of(pranzoGiaAssegnato));

        assertEquals(1, violazioni.size());
        assertEquals(List.of(cenaProposta, pranzoGiaAssegnato), violazioni.getFirst().turniCoinvolti());
    }

    @Test
    void unaPropostaVuotaNonProduceViolazioniDelloStorico() {
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Senza assegnazioni proposte nessuna coppia coinvolge la proposta.
        // La verifica generale dell'intero storico è un'operazione diversa.
        assertTrue(verificatore.verificaRiposiProposta(
                List.of(), List.of(cenaDomenicaleDi(5), pranzoDiLunedi(5, 11, 0))).isEmpty());
    }

    @Test
    void stessoEsitoConIngressiRiordinatiSenzaModificareGliElenchiRicevuti() {
        AssegnazioneDaVerificare cenaAnna = cenaDomenicaleDi(5);
        AssegnazioneDaVerificare pranzoAnna = pranzoDiLunedi(5, 11, 0);
        AssegnazioneDaVerificare cenaAltroDipendente = cenaDomenicaleDi(6);
        AssegnazioneDaVerificare pranzoAltroDipendente = pranzoDiLunedi(6, 11, 0);
        List<AssegnazioneDaVerificare> proposta = new ArrayList<>(
                List.of(pranzoAltroDipendente, pranzoAnna));
        List<AssegnazioneDaVerificare> confinanti = new ArrayList<>(
                List.of(cenaAnna, cenaAltroDipendente));
        List<AssegnazioneDaVerificare> propostaOriginale = List.copyOf(proposta);
        List<AssegnazioneDaVerificare> confinantiOriginali = List.copyOf(confinanti);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        List<ViolazioneTurno> primoEsito = verificatore.verificaRiposiProposta(proposta, confinanti);
        List<ViolazioneTurno> esitoRiordinato = verificatore.verificaRiposiProposta(
                List.of(pranzoAnna, pranzoAltroDipendente),
                List.of(cenaAltroDipendente, cenaAnna));

        // Generatore e modifica manuale possono fornire gli stessi dati in ordini diversi.
        // Il contenuto e l'ordine delle violazioni devono restare uguali e attribuire
        // correttamente ciascuna coppia al proprio dipendente.
        assertEquals(2, primoEsito.size());
        assertEquals(5, primoEsito.get(0).dipendenteId());
        assertEquals(List.of(cenaAnna, pranzoAnna), primoEsito.get(0).turniCoinvolti());
        assertEquals(6, primoEsito.get(1).dipendenteId());
        assertEquals(List.of(cenaAltroDipendente, pranzoAltroDipendente),
                primoEsito.get(1).turniCoinvolti());
        assertEquals(primoEsito, esitoRiordinato);
        assertEquals(propostaOriginale, proposta);
        assertEquals(confinantiOriginali, confinanti);
    }

    @Test
    void unaSolaViolazioneAncheQuandoEntrambiITurniSonoNellaProposta() {
        LocalDate lunedi = LocalDate.of(2026, 11, 23);
        AssegnazioneDaVerificare pranzo = pranzoDiLunedi(5, 11, 30);
        AssegnazioneDaVerificare cena = new AssegnazioneDaVerificare(
                5, lunedi, Servizio.CENA, lunedi.atTime(18, 0),
                lunedi.plusDays(1).atTime(0, 30));
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // I turni non si sovrappongono, ma fra le 16:00 e le 18:00 ci sono solo 2 ore.
        // Entrambi coinvolgono la proposta: la stessa coppia va segnalata una volta sola.
        List<ViolazioneTurno> violazioni = verificatore.verificaRiposiProposta(
                List.of(cena, pranzo), List.of());

        assertEquals(1, violazioni.size());
        assertEquals(List.of(pranzo, cena), violazioni.getFirst().turniCoinvolti());
    }

    @Test
    void laModificaManualeVerificaIlPianoRisultanteSenzaTrattenereIlTurnoPrecedente() {
        LocalDate lunedi = LocalDate.of(2026, 11, 23);
        AssegnazioneDaVerificare cenaDomenica = cenaDomenicaleDi(5);
        AssegnazioneDaVerificare pranzoDaSostituire = pranzoDiLunedi(5, 11, 0);
        AssegnazioneDaVerificare nuovaCenaLunedi = new AssegnazioneDaVerificare(
                5, lunedi, Servizio.CENA, lunedi.atTime(18, 0),
                lunedi.plusDays(1).atTime(0, 30));
        VerificaVincoliService verificatore = new VerificaVincoliService();

        List<ViolazioneTurno> prima = verificatore.verificaRiposiProposta(
                List.of(pranzoDaSostituire), List.of(cenaDomenica));
        // Simuliamo il contratto di chiamata di Valerio: il turno nuovo sostituisce
        // quello precedente. È una prova in memoria, non della schermata o del salvataggio.
        List<ViolazioneTurno> dopo = verificatore.verificaRiposiProposta(
                List.of(nuovaCenaLunedi), List.of(cenaDomenica));

        assertEquals(1, prima.size());
        assertTrue(dopo.isEmpty(), "La nuova proposta lascia 17 ore e 30 minuti di riposo");
        assertEquals(List.of(cenaDomenica, pranzoDaSostituire), prima.getFirst().turniCoinvolti(),
                "Una verifica successiva non deve modificare un risultato già restituito");
    }

    @Test
    void segnalaPranzoECenaConsecutiviNelloStessoGiorno() {
        LocalDate lunedi = LocalDate.of(2026, 11, 23);
        AssegnazioneDaVerificare pranzo = pranzoDiLunedi(5, 11, 30);
        AssegnazioneDaVerificare cena = new AssegnazioneDaVerificare(
                5, lunedi, Servizio.CENA, lunedi.atTime(18, 0),
                lunedi.plusDays(1).atTime(0, 30));
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Queste fasce sono adiacenti nel calendario del ristorante.
        // Qui verifichiamo solo il nuovo divieto, separatamente dalle 11 ore.
        List<ViolazioneTurno> violazioni = verificatore.verificaTurniConsecutivi(pranzo, cena);

        assertEquals(1, violazioni.size());
        ViolazioneTurno violazione = violazioni.getFirst();
        assertEquals(TipoViolazione.TURNI_CONSECUTIVI, violazione.tipo());
        assertEquals(5, violazione.dipendenteId());
        assertEquals(List.of(pranzo, cena), violazione.turniCoinvolti());
        assertTrue(violazione.motivo().contains("fasce consecutive"),
                "La spiegazione deve identificare la regola distinta dal riposo minimo");
    }

    @Test
    void segnalaCenaEPranzoDelGiornoDopoAncheConUndiciOreDiRiposo() {
        AssegnazioneDaVerificare cenaDomenica = cenaDomenicaleDi(5);
        AssegnazioneDaVerificare pranzoLunedi = pranzoDiLunedi(5, 11, 30);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Anna ha precisamente 11 ore di riposo. Non dobbiamo cambiare quella soglia
        // per vietare cena/pranzo: è una nuova regola sulle fasce del piano.
        assertEquals(Duration.ofHours(11),
                verificatore.calcolaRiposo(cenaDomenica.fine(), pranzoLunedi.inizio()));
        assertTrue(verificatore.verificaRiposo(cenaDomenica, pranzoLunedi).isEmpty());

        List<ViolazioneTurno> violazioni =
                verificatore.verificaTurniConsecutivi(cenaDomenica, pranzoLunedi);

        assertEquals(1, violazioni.size());
        ViolazioneTurno violazione = violazioni.getFirst();
        assertEquals(TipoViolazione.TURNI_CONSECUTIVI, violazione.tipo());
        assertEquals(5, violazione.dipendenteId());
        assertEquals(List.of(cenaDomenica, pranzoLunedi), violazione.turniCoinvolti());
        assertTrue(violazione.motivo().contains("fasce consecutive"));
    }

    @Test
    void nonSegnalaFasceConsecutiveDiDipendentiDiversi() {
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Il divieto riguarda una singola persona: la cena di Anna non impedisce
        // a un altro dipendente di coprire il pranzo immediatamente successivo.
        assertTrue(verificatore.verificaTurniConsecutivi(
                cenaDomenicaleDi(5), pranzoDiLunedi(6, 11, 30)).isEmpty());
    }

    @Test
    void nonSegnalaDuePranziODueCeneInGiorniSuccessivi() {
        LocalDate domenica = LocalDate.of(2026, 11, 22);
        LocalDate lunedi = domenica.plusDays(1);
        AssegnazioneDaVerificare pranzoDomenica = new AssegnazioneDaVerificare(
                5, domenica, Servizio.PRANZO, domenica.atTime(11, 30), domenica.atTime(16, 0));
        AssegnazioneDaVerificare cenaLunedi = new AssegnazioneDaVerificare(
                5, lunedi, Servizio.CENA, lunedi.atTime(18, 0),
                lunedi.plusDays(1).atTime(0, 30));
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Fra due pranzi c'è una cena, e fra due cene c'è un pranzo.
        // Sono giornate consecutive, ma non fasce adiacenti del ristorante.
        assertTrue(verificatore.verificaTurniConsecutivi(
                pranzoDomenica, pranzoDiLunedi(5, 11, 30)).isEmpty());
        assertTrue(verificatore.verificaTurniConsecutivi(
                cenaDomenicaleDi(5), cenaLunedi).isEmpty());
    }

    @Test
    void nonSegnalaPranzoECenaDiGiorniDiversi() {
        LocalDate domenica = LocalDate.of(2026, 11, 22);
        LocalDate lunedi = domenica.plusDays(1);
        AssegnazioneDaVerificare pranzoDomenica = new AssegnazioneDaVerificare(
                5, domenica, Servizio.PRANZO, domenica.atTime(11, 30), domenica.atTime(16, 0));
        AssegnazioneDaVerificare cenaLunedi = new AssegnazioneDaVerificare(
                5, lunedi, Servizio.CENA, lunedi.atTime(18, 0),
                lunedi.plusDays(1).atTime(0, 30));
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Confrontare solo PRANZO e CENA darebbe un falso errore:
        // devono appartenere allo stesso giorno del piano per essere adiacenti.
        assertTrue(verificatore.verificaTurniConsecutivi(pranzoDomenica, cenaLunedi).isEmpty());
    }

    @Test
    void nonSegnalaCenaEPranzoConUnGiornoLiberoInMezzo() {
        LocalDate martedi = LocalDate.of(2026, 11, 24);
        AssegnazioneDaVerificare pranzoMartedi = new AssegnazioneDaVerificare(
                5, martedi, Servizio.PRANZO, martedi.atTime(11, 30), martedi.atTime(16, 0));
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // La cena appartiene alla domenica anche se finisce lunedì alle 00:30.
        // Il pranzo di martedì non è quello del giorno di piano immediatamente dopo.
        assertTrue(verificatore.verificaTurniConsecutivi(
                cenaDomenicaleDi(5), pranzoMartedi).isEmpty());
    }

    @Test
    void restaUnGiornoLiberoConSettimanaVuotaOAncoraInCostruzione() {
        LocalDate lunedi = LocalDate.of(2026, 11, 23);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Il generatore controlla anche piani incompleti. Finché non sono occupati
        // tutti i sette giorni, questa regola non deve impedire di proseguire.
        assertTrue(verificatore.haRiposoSettimanale(5, lunedi, List.of()));
        assertTrue(verificatore.haRiposoSettimanale(
                5, lunedi, List.of(pranzoDiLunedi(5, 11, 30))));
    }

    @Test
    void mancaIlRiposoQuandoIlDipendenteLavoraTuttiISetteGiorni() {
        LocalDate lunedi = LocalDate.of(2026, 11, 23);
        List<AssegnazioneDaVerificare> settimana = pranziStandardDa(5, lunedi, 7);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Sette pranzi occupano sette giorni: anche se il totale è solo 31 ore
        // e 30 minuti, manca il giorno libero. Monte ore e riposo sono regole distinte.
        assertFalse(verificatore.haRiposoSettimanale(5, lunedi, settimana));
    }

    @Test
    void dueTurniNelloStessoGiornoNonContanoComeDueGiorniOccupati() {
        LocalDate lunedi = LocalDate.of(2026, 11, 23);
        List<AssegnazioneDaVerificare> settimana =
                new ArrayList<>(pranziStandardDa(5, lunedi, 6));
        settimana.add(new AssegnazioneDaVerificare(
                5, lunedi, Servizio.CENA, lunedi.atTime(18, 0),
                lunedi.plusDays(1).atTime(0, 30)));
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Sette assegnazioni occupano soltanto sei giorni: la domenica è libera.
        // Pranzo/cena del lunedì violano altri vincoli, verificati in test separati.
        assertTrue(verificatore.haRiposoSettimanale(5, lunedi, settimana));
    }

    @Test
    void laCenaDomenicalePrecedenteNonOccupaIlLunediDelPiano() {
        LocalDate lunedi = LocalDate.of(2026, 11, 23);
        List<AssegnazioneDaVerificare> assegnazioni =
                new ArrayList<>(pranziStandardDa(5, lunedi.plusDays(1), 6));
        assegnazioni.add(cenaDomenicaleDi(5));
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Anna lavora da martedì a domenica. La cena della domenica precedente
        // finisce lunedì alle 00:30, ma appartiene al giornoPiano precedente.
        // Secondo la regola concordata, il lunedì resta il giorno libero del piano.
        assertTrue(verificatore.haRiposoSettimanale(5, lunedi, assegnazioni));
    }

    @Test
    void ilRiposoSettimanaleIgnoraLeAssegnazioniFuoriDallaSettimana() {
        LocalDate lunedi = LocalDate.of(2026, 11, 23);
        List<AssegnazioneDaVerificare> assegnazioni =
                new ArrayList<>(pranziStandardDa(5, lunedi, 6));
        assegnazioni.addAll(pranziStandardDa(5, lunedi.minusDays(1), 1));
        assegnazioni.addAll(pranziStandardDa(5, lunedi.plusDays(7), 1));
        List<AssegnazioneDaVerificare> originali = List.copyOf(assegnazioni);
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // La domenica corrente è libera. Né la domenica precedente né il lunedì
        // successivo appartengono alla settimana controllata, anche se sono nell'elenco.
        assertTrue(verificatore.haRiposoSettimanale(5, lunedi, assegnazioni));
        assertEquals(originali, assegnazioni, "La verifica non deve modificare gli ingressi");
    }

    @Test
    void ilRiposoSettimanaleNonMescolaIGiorniDiDipendentiDiversi() {
        LocalDate lunedi = LocalDate.of(2026, 11, 23);
        List<AssegnazioneDaVerificare> assegnazioni =
                new ArrayList<>(pranziStandardDa(5, lunedi.plusDays(1), 6));
        assegnazioni.add(pranzoDiLunedi(6, 11, 30));
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Il pranzo del lunedì appartiene all'ID 6: per Anna, ID 5, è un giorno libero.
        assertTrue(verificatore.haRiposoSettimanale(5, lunedi, assegnazioni));
    }

    @Test
    void ilRiposoSettimanaleRichiedeUnaDataDiLunedi() {
        VerificaVincoliService verificatore = new VerificaVincoliService();

        // Una data mancante o un martedì non devono spostare silenziosamente
        // la finestra settimanale: il chiamante riceve un errore esplicito.
        assertThrows(IllegalArgumentException.class,
                () -> verificatore.haRiposoSettimanale(5, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> verificatore.haRiposoSettimanale(
                        5, LocalDate.of(2026, 11, 24), List.of()));
    }

    @Test
    void laVerificaCombinataSegnalaSoloLeFasceConsecutiveAlLimiteDelleUndiciOre() {
        var cena = cenaDomenicaleDi(5);
        var pranzo = pranzoDiLunedi(5, 11, 30);
        var verificatore = new VerificaVincoliService();

        // Il turno confinante precede la proposta: le 11 ore sono rispettate,
        // ma il divieto cena/pranzo deve comunque arrivare al chiamante.
        var esito = verificatore.verificaVincoliTraTurniProposta(List.of(pranzo), List.of(cena));
        assertEquals(1, esito.size());
        assertEquals(TipoViolazione.TURNI_CONSECUTIVI, esito.getFirst().tipo());
        assertEquals(5, esito.getFirst().dipendenteId());
        assertEquals(List.of(cena, pranzo), esito.getFirst().turniCoinvolti());

        // Controlliamo anche il verso opposto: il confinante è successivo alla proposta.
        assertEquals(esito,
                verificatore.verificaVincoliTraTurniProposta(List.of(cena), List.of(pranzo)));
    }

    @Test
    void laVerificaCombinataRestituisceDueRegoleDistinteSenzaDuplicarle() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var pranzo = pranzoDiLunedi(5, 11, 30);
        var cena = new AssegnazioneDaVerificare(5, lunedi, Servizio.CENA,
                lunedi.atTime(18, 0), lunedi.plusDays(1).atTime(0, 30));
        var verificatore = new VerificaVincoliService();

        // Pranzo/cena viola due regole. Le copie identiche negli ingressi
        // non devono moltiplicare le segnalazioni, nemmeno con entrambi i turni proposti.
        var esito = verificatore.verificaVincoliTraTurniProposta(
                List.of(cena, pranzo, pranzoDiLunedi(5, 11, 30)), List.of(cena));
        assertEquals(2, esito.size());
        assertEquals(List.of(TipoViolazione.RIPOSO_INSUFFICIENTE, TipoViolazione.TURNI_CONSECUTIVI),
                esito.stream().map(ViolazioneTurno::tipo).toList());
        for (var violazione : esito) {
            assertEquals(5, violazione.dipendenteId());
            assertEquals(List.of(pranzo, cena), violazione.turniCoinvolti());
        }
    }

    @Test
    void laVerificaCombinataIgnoraProblemiTraSoliConfinanti() {
        var cena = cenaDomenicaleDi(5);
        var pranzo = pranzoDiLunedi(5, 11, 0);
        var verificatore = new VerificaVincoliService();

        // Entrambe le regole sono violate nello storico, ma nessun turno
        // della coppia appartiene alla proposta dell'altro dipendente.
        assertTrue(verificatore.verificaVincoliTraTurniProposta(
                List.of(pranzoDiLunedi(6, 11, 30)), List.of(cena, pranzo)).isEmpty());
        assertTrue(verificatore.verificaVincoliTraTurniProposta(
                List.of(), List.of(cena, pranzo)).isEmpty());
    }

    @Test
    void laVerificaCombinataNonDipendeDallOrdineENonModificaGliIngressi() {
        var cenaAnna = cenaDomenicaleDi(5);
        var cenaAltro = cenaDomenicaleDi(6);
        var pranzoAnna = pranzoDiLunedi(5, 11, 30);
        var pranzoAltro = pranzoDiLunedi(6, 11, 30);
        var proposta = new ArrayList<>(List.of(pranzoAltro, pranzoAnna));
        var confinanti = new ArrayList<>(List.of(cenaAnna, cenaAltro));
        var copiaProposta = List.copyOf(proposta);
        var copiaConfinanti = List.copyOf(confinanti);
        var verificatore = new VerificaVincoliService();

        var esito = verificatore.verificaVincoliTraTurniProposta(proposta, confinanti);
        assertEquals(2, esito.size());
        assertEquals(List.of(5, 6), esito.stream().map(ViolazioneTurno::dipendenteId).toList());
        assertEquals(esito, verificatore.verificaVincoliTraTurniProposta(
                List.of(pranzoAnna, pranzoAltro), List.of(cenaAltro, cenaAnna)));
        assertEquals(copiaProposta, proposta);
        assertEquals(copiaConfinanti, confinanti);
        assertThrows(UnsupportedOperationException.class, () -> esito.clear());
    }

    @Test
    void laVerificaCombinataAccettaLaSostituzioneConUnTurnoCompatibile() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var cenaDomenica = cenaDomenicaleDi(5);
        var cenaLunedi = new AssegnazioneDaVerificare(5, lunedi, Servizio.CENA,
                lunedi.atTime(18, 0), lunedi.plusDays(1).atTime(0, 30));
        var verificatore = new VerificaVincoliService();

        // Simuliamo in memoria il piano risultante dalla modifica manuale:
        // il pranzo viene sostituito dalla cena, non mantenuto nella nuova proposta.
        var prima = verificatore.verificaVincoliTraTurniProposta(
                List.of(pranzoDiLunedi(5, 11, 30)), List.of(cenaDomenica));
        assertEquals(1, prima.size());
        assertTrue(verificatore.verificaVincoliTraTurniProposta(
                List.of(cenaLunedi), List.of(cenaDomenica)).isEmpty());
        assertEquals(1, prima.size(), "La verifica successiva non altera il risultato precedente");
    }

    @Test
    void segnalaLaSettimanaSenzaRiposoConDipendenteTurniEMotivo() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var settimana = pranziStandardDa(5, lunedi, 7);
        var verificatore = new VerificaVincoliService();

        var esito = verificatore.verificaRiposoSettimanale(5, lunedi, settimana);

        // Il chiamante deve poter identificare persona, periodo e assegnazioni,
        // senza interpretare il testo per riconoscere il tipo di problema.
        assertEquals(1, esito.size());
        var violazione = esito.getFirst();
        assertEquals(TipoViolazione.RIPOSO_SETTIMANALE_ASSENTE, violazione.tipo());
        assertEquals(5, violazione.dipendenteId());
        assertEquals(settimana, violazione.turniCoinvolti());
        assertTrue(violazione.motivo().contains("2026-11-23"));
        assertTrue(violazione.motivo().contains("2026-11-29"));
        assertTrue(violazione.motivo().contains("giorno del piano libero"));
        assertThrows(UnsupportedOperationException.class, () -> esito.clear());
    }

    @Test
    void laSegnalazioneSettimanaleFiltraOrdinaENonDuplicaITurni() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var settimana = pranziStandardDa(5, lunedi, 7);
        var ingressi = new ArrayList<AssegnazioneDaVerificare>();
        for (int i = settimana.size() - 1; i >= 0; i--) {
            ingressi.add(settimana.get(i));
        }
        ingressi.add(pranzoDiLunedi(5, 11, 30));
        ingressi.add(cenaDomenicaleDi(5));
        ingressi.addAll(pranziStandardDa(5, lunedi.plusDays(7), 1));
        ingressi.add(pranzoDiLunedi(6, 11, 30));
        var originali = List.copyOf(ingressi);
        var verificatore = new VerificaVincoliService();

        // Mescoliamo duplicati, persone e settimane: il risultato deve contenere
        // soltanto le sette assegnazioni pertinenti, in ordine cronologico.
        var esito = verificatore.verificaRiposoSettimanale(5, lunedi, ingressi);
        assertEquals(1, esito.size());
        assertEquals(settimana, esito.getFirst().turniCoinvolti());
        assertEquals(originali, ingressi);
        ingressi.clear();
        assertEquals(settimana, esito.getFirst().turniCoinvolti());
    }

    @Test
    void nessunaSegnalazioneSettimanaleQuandoRestaUnGiornoLibero() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var turni = new ArrayList<>(pranziStandardDa(5, lunedi.plusDays(1), 6));
        turni.add(cenaDomenicaleDi(5));
        turni.add(pranzoDiLunedi(6, 11, 30));
        var verificatore = new VerificaVincoliService();

        // Il lunedì è libero per Anna: la cena precedente appartiene alla domenica
        // e il pranzo del lunedì appartiene a un'altra persona.
        assertTrue(verificatore.verificaRiposoSettimanale(5, lunedi, turni).isEmpty());
        assertTrue(verificatore.verificaRiposoSettimanale(5, lunedi, List.of()).isEmpty());
    }

    @Test
    void ancheLaSegnalazioneSettimanaleRifiutaUnaDataDiInizioNonValida() {
        var verificatore = new VerificaVincoliService();
        // La validazione deve valere anche chiamando il metodo strutturato.
        assertThrows(IllegalArgumentException.class,
                () -> verificatore.verificaRiposoSettimanale(5, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> verificatore.verificaRiposoSettimanale(
                        5, LocalDate.of(2026, 11, 24), List.of()));
    }

    @Test
    void sommaPranzoECenaConservandoLeMezzeOre() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var martedi = lunedi.plusDays(1);
        var cena = new AssegnazioneDaVerificare(5, martedi, Servizio.CENA,
                martedi.atTime(18, 0), martedi.plusDays(1).atTime(0, 30));
        var verificatore = new VerificaVincoliService();

        // Pranzo di 270 minuti e cena di 390: totale 660, cioè 11 ore.
        assertEquals(270L, verificatore.calcolaMinutiSettimanali(
                5, lunedi, List.of(pranzoDiLunedi(5, 11, 30))));
        assertEquals(390L, verificatore.calcolaMinutiSettimanali(5, lunedi, List.of(cena)));
        assertEquals(660L, verificatore.calcolaMinutiSettimanali(
                5, lunedi, List.of(pranzoDiLunedi(5, 11, 30), cena)));
    }

    @Test
    void attribuisceTuttaLaCenaDomenicaleAllaSettimanaDelGiornoPiano() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var domenica = lunedi.plusDays(6);
        var cena = new AssegnazioneDaVerificare(5, domenica, Servizio.CENA,
                domenica.atTime(18, 0), domenica.plusDays(1).atTime(0, 30));
        var verificatore = new VerificaVincoliService();

        // I 30 minuti dopo mezzanotte restano nella settimana della domenica.
        assertEquals(390L, verificatore.calcolaMinutiSettimanali(5, lunedi, List.of(cena)));
        assertEquals(0L, verificatore.calcolaMinutiSettimanali(
                5, lunedi.plusDays(7), List.of(cena)));
        assertEquals(0L, verificatore.calcolaMinutiSettimanali(
                5, lunedi, List.of(cenaDomenicaleDi(5))));
    }

    @Test
    void ilConteggioFiltraDipendenteESettimanaSenzaDuplicatiOModificheAgliIngressi() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var pranzo = pranzoDiLunedi(5, 11, 30);
        var turni = new ArrayList<>(List.of(pranzoDiLunedi(6, 11, 30),
                pranzo, cenaDomenicaleDi(5), pranzoDiLunedi(5, 11, 30)));
        turni.addAll(pranziStandardDa(5, lunedi.plusDays(7), 1));
        var originali = List.copyOf(turni);
        var verificatore = new VerificaVincoliService();

        // Conta soltanto il pranzo di Anna, una volta: il candidato è già nel piano.
        assertEquals(270L, verificatore.calcolaMinutiSettimanali(5, lunedi, turni));
        assertEquals(originali, turni);
        assertEquals(0L, verificatore.calcolaMinutiSettimanali(5, lunedi, List.of()));
    }

    @Test
    void ilConteggioUsaLeDurateRicevuteSenzaArrotondareAlleOre() {
        var lunedi = LocalDate.of(2026, 11, 23);
        // Un intervallo di prova non standard verifica la precisione al minuto.
        var turno = new AssegnazioneDaVerificare(5, lunedi, Servizio.PRANZO,
                lunedi.atTime(11, 30), lunedi.atTime(16, 1));
        var verificatore = new VerificaVincoliService();
        assertEquals(271L, verificatore.calcolaMinutiSettimanali(5, lunedi, List.of(turno)));
    }

    @Test
    void ilConteggioSettimanaleRifiutaUnaDataDiInizioNonValida() {
        var verificatore = new VerificaVincoliService();
        assertThrows(IllegalArgumentException.class,
                () -> verificatore.calcolaMinutiSettimanali(5, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> verificatore.calcolaMinutiSettimanali(
                        5, LocalDate.of(2026, 11, 24), List.of()));
    }

    @Test
    void accettaIlLimiteEsattoERilevaUnMinutoInPiuAncheAlleQuarantaOre() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var verificatore = new VerificaVincoliService();
        // Verifichiamo sia il limite individuale sia il massimo del progetto.
        for (int ore : List.of(20, 40)) {
            var contratto = contrattoSettimanaleDiProva(ore);
            assertTrue(verificatore.verificaMonteOreSettimanale(
                    5, lunedi, turniDiProvaPerMinuti(ore * 60), contratto).isEmpty());
            var esito = verificatore.verificaMonteOreSettimanale(
                    5, lunedi, turniDiProvaPerMinuti(ore * 60 + 1), contratto);
            assertEquals(1, esito.size());
            assertEquals(TipoViolazione.MONTE_ORE_SUPERATO, esito.getFirst().tipo());
            assertTrue(esito.getFirst().motivo().contains(ore + " ore e 1 minuti"));
        }
    }

    @Test
    void ilSuperamentoIdentificaSoloITurniConteggiatiSenzaDuplicati() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var pertinenti = turniDiProvaPerMinuti(1201);
        var ingressi = new ArrayList<>(pertinenti);
        ingressi.add(pertinenti.getFirst());
        ingressi.add(pranzoDiLunedi(6, 11, 30));
        ingressi.add(cenaDomenicaleDi(5));
        ingressi.addAll(pranziStandardDa(5, lunedi.plusDays(7), 1));
        var originali = List.copyOf(ingressi);
        var verificatore = new VerificaVincoliService();
        var esito = verificatore.verificaMonteOreSettimanale(
                5, lunedi, ingressi, contrattoSettimanaleDiProva(20));
        assertEquals(1, esito.size());
        assertEquals(5, esito.getFirst().dipendenteId());
        assertEquals(pertinenti, esito.getFirst().turniCoinvolti());
        assertTrue(esito.getFirst().motivo().contains("2026-11-23"));
        assertTrue(esito.getFirst().motivo().contains("limite applicabile è 20 ore e 0 minuti"));
        assertEquals(originali, ingressi);
    }

    @Test
    void richiedeUnContrattoDellaPersonaVerificata() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var verificatore = new VerificaVincoliService();
        assertThrows(IllegalArgumentException.class, () ->
                verificatore.verificaMonteOreSettimanale(5, lunedi, List.of(), null));
        var altro = contrattoSettimanaleDiProva(20);
        altro.setDipendenteId(6);
        assertThrows(IllegalArgumentException.class, () ->
                verificatore.verificaMonteOreSettimanale(5, lunedi, List.of(), altro));
    }

    @Test
    void rifiutaLimitiContrattualiNonValidi() {
        var verificatore = new VerificaVincoliService();
        // Un contratto oltre 40 ore è un dato non valido, non un'autorizzazione
        // a superare il massimo. Vale anche senza turni ancora assegnati.
        for (int ore : List.of(-1, 0, 41)) {
            assertThrows(IllegalArgumentException.class, () ->
                    verificatore.verificaMonteOreSettimanale(5, LocalDate.of(2026, 11, 23),
                            List.of(), contrattoSettimanaleDiProva(ore)));
        }
    }

    @Test
    void mantieneIlControlloSulleAssunzioniEAmmetteLaScadenzaInSettimana() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var verificatore = new VerificaVincoliService();
        var contratto = contrattoSettimanaleDiProva(20);
        // La fixture copre esattamente lunedì-domenica: entrambi gli estremi valgono.
        assertTrue(verificatore.verificaMonteOreSettimanale(5, lunedi, List.of(), contratto).isEmpty());
        contratto.setDataInizio(lunedi.plusDays(1));
        assertThrows(IllegalArgumentException.class, () ->
                verificatore.verificaMonteOreSettimanale(5, lunedi, List.of(), contratto));
        contratto.setDataInizio(lunedi);
        contratto.setDataFine(lunedi.plusDays(5));
        // Nessun turno oltre la scadenza: la domenica scoperta non è più un errore.
        assertTrue(verificatore.verificaMonteOreSettimanale(5, lunedi, List.of(), contratto).isEmpty());
    }

    @Test
    void ilRicontrolloUsaIlContrattoRicevutoSenzaCambiareIlPiano() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var piano = pranziStandardDa(5, lunedi, 4); // 18 ore.
        var copia = List.copyOf(piano);
        var verificatore = new VerificaVincoliService();
        // Simulazione in memoria di due contratti applicabili all'intera settimana.
        // Non prova ancora l'avviso US02, la persistenza o cambi a metà settimana.
        assertTrue(verificatore.verificaMonteOreSettimanale(
                5, lunedi, piano, contrattoSettimanaleDiProva(20)).isEmpty());
        assertEquals(1, verificatore.verificaMonteOreSettimanale(
                5, lunedi, piano, contrattoSettimanaleDiProva(16)).size());
        assertEquals(copia, piano);
    }

    @Test
    void vecchioIngressoEStoricoRestituisconoLoStessoEsito() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var verificatore = new VerificaVincoliService();
        var contratto = contrattoSettimanaleDiProva(20);
        // Confrontiamo l'intero risultato, inclusi turni e messaggio, su entrambi i percorsi.
        for (int minuti : List.of(1200, 1201)) {
            var turni = turniDiProvaPerMinuti(minuti);
            assertEquals(verificatore.verificaMonteOreConStorico(5, lunedi, turni, List.of(contratto)),
                    verificatore.verificaMonteOreSettimanale(5, lunedi, turni, contratto));
        }
    }

    @Test
    void laVerificaCompletaAccettaPianiVuotiEParziali() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var verificatore = new VerificaVincoliService();
        // Nessuna persona proposta: non verifichiamo violazioni del solo storico.
        assertTrue(verificatore.verificaSettimana(lunedi, List.of(),
                List.of(cenaDomenicaleDi(5), pranzoDiLunedi(5, 11, 0)), List.of()).isEmpty());
        var proposta = List.of(pranzoDiLunedi(5, 11, 30));
        assertTrue(verificatore.verificaSettimana(lunedi, proposta,
                List.of(cenaDomenicaleDi(6)), List.of(contrattoSettimanaleDiProva(20))).isEmpty());
    }

    @Test
    void laVerificaCompletaRaccoglieTutteLeRegoleSenzaConfondereIDipendenti() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var proposta = new ArrayList<>(pranziStandardDa(5, lunedi, 7));
        proposta.add(new AssegnazioneDaVerificare(5, lunedi, Servizio.CENA,
                lunedi.atTime(18, 0), lunedi.plusDays(1).atTime(0, 30)));
        proposta.add(pranzoDiLunedi(6, 11, 30));
        var contrattoAltro = contrattoSettimanaleDiProva(40);
        contrattoAltro.setDipendenteId(6);
        var contratti = new ArrayList<>(List.of(contrattoSettimanaleDiProva(20), contrattoAltro));
        var confinanti = new ArrayList<>(List.of(cenaDomenicaleDi(5)));
        var copiaProposta = List.copyOf(proposta);
        var copiaConfinanti = List.copyOf(confinanti);
        var copiaContratti = List.copyOf(contratti);
        var verificatore = new VerificaVincoliService();

        // Anna: 38 ore, sette giorni occupati, pranzo/cena e confine domenica/lunedì.
        // L'altra persona ha un solo pranzo e non deve ricevere segnalazioni.
        var esito = verificatore.verificaSettimana(lunedi, proposta, confinanti, contratti);
        assertEquals(6, esito.size());
        assertTrue(esito.stream().allMatch(v -> v.dipendenteId() == 5));
        assertEquals(4L, esito.stream().map(ViolazioneTurno::tipo).distinct().count());
        assertEquals(1L, esito.stream().filter(v -> v.tipo() == TipoViolazione.MONTE_ORE_SUPERATO).count());
        assertEquals(1L, esito.stream().filter(v -> v.tipo() == TipoViolazione.RIPOSO_SETTIMANALE_ASSENTE).count());
        assertEquals(copiaProposta, proposta);
        assertEquals(copiaConfinanti, confinanti);
        assertEquals(copiaContratti, contratti);

        // Ordine e copie identiche non devono cambiare il risultato né contare il candidato due volte.
        var riordinata = new ArrayList<>(proposta.reversed());
        riordinata.add(pranzoDiLunedi(5, 11, 30));
        assertEquals(esito, verificatore.verificaSettimana(
                lunedi, riordinata, confinanti, contratti.reversed()));
        assertThrows(UnsupportedOperationException.class, () -> esito.clear());
    }

    @Test
    void laVerificaCompletaRileggeGliIngressiDopoLaSostituzioneManuale() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var contratti = List.of(contrattoSettimanaleDiProva(40));
        var confinanti = List.of(cenaDomenicaleDi(5));
        var verificatore = new VerificaVincoliService();
        var prima = verificatore.verificaSettimana(
                lunedi, List.of(pranzoDiLunedi(5, 11, 30)), confinanti, contratti);
        assertEquals(1, prima.size());
        assertEquals(TipoViolazione.TURNI_CONSECUTIVI, prima.getFirst().tipo());
        var nuovaCena = new AssegnazioneDaVerificare(5, lunedi, Servizio.CENA,
                lunedi.atTime(18, 0), lunedi.plusDays(1).atTime(0, 30));
        // Solo il nuovo turno: il service non trattiene lo stato della chiamata precedente.
        assertTrue(verificatore.verificaSettimana(lunedi, List.of(nuovaCena), confinanti, contratti).isEmpty());
        assertEquals(1, prima.size());
    }

    @Test
    void laVerificaCompletaConsideraIlConfinanteDellaSettimanaSuccessiva() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var domenica = lunedi.plusDays(6);
        var cena = new AssegnazioneDaVerificare(5, domenica, Servizio.CENA,
                domenica.atTime(18, 0), domenica.plusDays(1).atTime(0, 30));
        var pranzoSeguente = pranziStandardDa(5, lunedi.plusDays(7), 1);
        var verificatore = new VerificaVincoliService();
        // Contratto da 7 ore: la cena vale 6h30; il pranzo della settimana dopo
        // serve al controllo delle fasce consecutive ma non entra nel monte ore corrente.
        var esito = verificatore.verificaSettimana(lunedi, List.of(cena), pranzoSeguente,
                List.of(contrattoSettimanaleDiProva(7)));
        assertEquals(1, esito.size());
        assertEquals(TipoViolazione.TURNI_CONSECUTIVI, esito.getFirst().tipo());
        assertEquals(List.of(cena, pranzoSeguente.getFirst()), esito.getFirst().turniCoinvolti());
    }

    @Test
    void laVerificaCompletaNonNascondeContrattiMancantiOSovrapposti() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var proposta = List.of(pranzoDiLunedi(5, 11, 30));
        var verificatore = new VerificaVincoliService();
        assertThrows(IllegalArgumentException.class, () ->
                verificatore.verificaSettimana(lunedi, proposta, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                verificatore.verificaSettimana(lunedi, proposta, List.of(),
                        List.of(contrattoSettimanaleDiProva(20), contrattoSettimanaleDiProva(40))));
        var incompleto = contrattoSettimanaleDiProva(20);
        incompleto.setDataFine(lunedi.plusDays(2));
        // Il turno di lunedì precede la scadenza di mercoledì ed è coperto.
        assertTrue(verificatore.verificaSettimana(
                lunedi, proposta, List.of(), List.of(incompleto)).isEmpty());
    }

    /** La scadenza non riproporziona le ore: 20 ore restano il massimo settimanale. */
    @Test
    void scadenzaMercolediMantieneIlLimiteInteroAncheAlMinuto() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var contratto = contrattoSettimanaleDiProva(20);
        contratto.setDataFine(lunedi.plusDays(2));
        var verificatore = new VerificaVincoliService();
        // Questi turni sintetici occupano solo lunedì, martedì e mercoledì.
        assertTrue(verificatore.verificaSettimana(lunedi,
                turniDiProvaPerMinuti(1200), List.of(), List.of(contratto)).isEmpty());
        var esito = verificatore.verificaSettimana(lunedi,
                turniDiProvaPerMinuti(1201), List.of(), List.of(contratto));
        assertEquals(1, esito.size());
        assertEquals(TipoViolazione.MONTE_ORE_SUPERATO, esito.getFirst().tipo());
        assertEquals(5, esito.getFirst().dipendenteId());
        assertEquals(lunedi.plusDays(2), contratto.getDataFine());
    }

    /** La cena appartiene al giorno iniziale, anche se finisce dopo la scadenza. */
    @Test
    void ammetteLaCenaDelGiornoDiScadenzaFinoAlleZeroETrenta() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var scadenza = lunedi.plusDays(2);
        var contratto = contrattoSettimanaleDiProva(20);
        contratto.setDataFine(scadenza);
        var cena = new AssegnazioneDaVerificare(5, scadenza, Servizio.CENA,
                scadenza.atTime(18, 0), scadenza.plusDays(1).atTime(0, 30));
        assertTrue(new VerificaVincoliService().verificaSettimana(
                lunedi, List.of(cena), List.of(), List.of(contratto)).isEmpty());
    }

    /** Poche ore non rendono ammissibile un turno iniziato dopo la scadenza. */
    @Test
    void rifiutaUnTurnoDelGiornoSuccessivoAllaScadenza() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var contratto = contrattoSettimanaleDiProva(20);
        contratto.setDataFine(lunedi.plusDays(2));
        assertThrows(IllegalArgumentException.class, () ->
                new VerificaVincoliService().verificaSettimana(lunedi,
                        pranziStandardDa(5, lunedi.plusDays(3), 1), List.of(), List.of(contratto)));
    }

    @Test
    void laVerificaCompletaRifiutaProposteFuoriSettimanaEDateNonValide() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var verificatore = new VerificaVincoliService();
        for (var fuori : List.of(cenaDomenicaleDi(5), pranziStandardDa(5, lunedi.plusDays(7), 1).getFirst())) {
            assertThrows(IllegalArgumentException.class, () ->
                    verificatore.verificaSettimana(lunedi, List.of(fuori), List.of(), List.of()));
        }
        assertThrows(IllegalArgumentException.class, () ->
                verificatore.verificaSettimana(null, List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                verificatore.verificaSettimana(lunedi.plusDays(1), List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                verificatore.verificaSettimana(lunedi, List.of(), List.of(), null));
    }

    @Test
    void usaIlVecchioContrattoFinoAllaDomenicaEIlNuovoDalPrimoLunediDelMese() {
        var decorrenza = LocalDate.of(2026, 12, 7); // Primo lunedì di dicembre.
        var precedente = contrattoSettimanaleDiProva(20);
        precedente.setDataFine(decorrenza.minusDays(1));
        var nuovo = contrattoSettimanaleDiProva(10);
        nuovo.setId(2);
        nuovo.setDataInizio(decorrenza);
        nuovo.setDataFine(null);
        var contratti = List.of(nuovo, precedente);
        var verificatore = new VerificaVincoliService();

        // La settimana che comprende il giorno 1 del mese usa ancora il vecchio
        // contratto. US07 legge queste date: il salvataggio futuro di US02 va testato a parte.
        var settimanaPrecedente = decorrenza.minusDays(7);
        assertTrue(verificatore.verificaSettimana(settimanaPrecedente,
                pranziStandardDa(5, settimanaPrecedente, 3), List.of(), contratti).isEmpty());
        var proposta = pranziStandardDa(5, decorrenza, 3); // 13 ore e 30 minuti.
        var esito = verificatore.verificaSettimana(decorrenza, proposta, List.of(), contratti);
        assertEquals(1, esito.size());
        assertEquals(TipoViolazione.MONTE_ORE_SUPERATO, esito.getFirst().tipo());
        assertEquals(proposta, esito.getFirst().turniCoinvolti());
        assertTrue(esito.getFirst().motivo().contains("limite applicabile è 10 ore e 0 minuti"));
        assertEquals(decorrenza.minusDays(1), precedente.getDataFine());
        assertEquals(decorrenza, nuovo.getDataInizio());
    }

    @Test
    void rifiutaUnCambioAMetaSettimanaSenzaInventareUnLimiteIntermedio() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var precedente = contrattoSettimanaleDiProva(40);
        precedente.setDataFine(lunedi.plusDays(2));
        var nuovo = contrattoSettimanaleDiProva(20);
        nuovo.setId(2);
        nuovo.setDataInizio(lunedi.plusDays(3));
        var verificatore = new VerificaVincoliService();
        // Eventuali dati preesistenti non vengono convertiti o accettati silenziosamente.
        var errore = assertThrows(IllegalArgumentException.class, () ->
                verificatore.verificaSettimana(lunedi, List.of(pranzoDiLunedi(5, 11, 30)),
                        List.of(), List.of(precedente, nuovo)));
        assertTrue(errore.getMessage().contains("Più contratti interessano la settimana"));
    }

    @Test
    void selezionaIlContrattoSettimanaleIgnorandoAltrePersoneEPeriodiEsterni() {
        var lunedi = LocalDate.of(2026, 11, 23);
        var valido = contrattoSettimanaleDiProva(20);
        var altro = contrattoSettimanaleDiProva(40);
        altro.setDipendenteId(6);
        var passato = contrattoSettimanaleDiProva(10);
        passato.setDataInizio(lunedi.minusDays(7));
        passato.setDataFine(lunedi.minusDays(1));
        var futuro = contrattoSettimanaleDiProva(10);
        futuro.setDataInizio(lunedi.plusDays(7));
        futuro.setDataFine(null);
        var contratti = new ArrayList<>(List.of(futuro, altro, valido, passato));
        var originali = List.copyOf(contratti);
        var verificatore = new VerificaVincoliService();
        assertTrue(verificatore.verificaMonteOreConStorico(
                5, lunedi, turniDiProvaPerMinuti(1200), contratti).isEmpty());
        assertEquals(1, verificatore.verificaMonteOreConStorico(
                5, lunedi, turniDiProvaPerMinuti(1201), contratti).size());
        assertEquals(originali, contratti);
    }

    /** Contratto con dati validi, valido esattamente dal lunedì alla domenica del test. */
    private Contratto contrattoSettimanaleDiProva(int ore) {
        var lunedi = LocalDate.of(2026, 11, 23);
        return new Contratto(1, 5, ore == 40 ? TipoContratto.FULL_TIME : TipoContratto.PART_TIME,
                ore, BigDecimal.TEN, lunedi, lunedi.plusDays(6));
    }

    /**
     * Orari sintetici per raggiungere limiti esatti e superarli di un minuto.
     * Non definiscono fasce standard: ogni giorno ha al massimo un turno di 8 ore.
     */
    private List<AssegnazioneDaVerificare> turniDiProvaPerMinuti(int minuti) {
        var giorno = LocalDate.of(2026, 11, 23);
        var turni = new ArrayList<AssegnazioneDaVerificare>();
        while (minuti > 0) {
            int durata = Math.min(minuti, 480);
            var inizio = giorno.atTime(11, 30);
            turni.add(new AssegnazioneDaVerificare(5, giorno, Servizio.PRANZO,
                    inizio, inizio.plusMinutes(durata)));
            minuti -= durata;
            giorno = giorno.plusDays(1);
        }
        return List.copyOf(turni);
    }

    /** Crea un pranzo standard per ogni giorno, con date fisse scelte dal test. */
    private List<AssegnazioneDaVerificare> pranziStandardDa(
            int dipendenteId, LocalDate primoGiorno, int numeroGiorni) {
        List<AssegnazioneDaVerificare> pranzi = new ArrayList<>();
        for (int i = 0; i < numeroGiorni; i++) {
            LocalDate giorno = primoGiorno.plusDays(i);
            pranzi.add(new AssegnazioneDaVerificare(
                    dipendenteId, giorno, Servizio.PRANZO,
                    giorno.atTime(11, 30), giorno.atTime(16, 0)));
        }
        return List.copyOf(pranzi);
    }

    /** Dato di prova locale: conserva la domenica nel piano e termina il lunedì. */
    private AssegnazioneDaVerificare cenaDomenicaleDi(int dipendenteId) {
        LocalDate domenica = LocalDate.of(2026, 11, 22);
        return new AssegnazioneDaVerificare(dipendenteId, domenica, Servizio.CENA,
                domenica.atTime(18, 0), domenica.plusDays(1).atTime(0, 30));
    }

    /**
     * Le 11:30 rappresentano il pranzo standard e la soglia esatta delle 11 ore.
     * Le 11:00 o le 11:29 sono anticipi intenzionali per le prove di riposo insufficiente.
     * L'orario variabile serve ai test: non definisce le fasce condivise del progetto.
     */
    private AssegnazioneDaVerificare pranzoDiLunedi(int dipendenteId, int ora, int minuto) {
        LocalDate lunedi = LocalDate.of(2026, 11, 23);
        return new AssegnazioneDaVerificare(dipendenteId, lunedi, Servizio.PRANZO,
                lunedi.atTime(ora, minuto), lunedi.atTime(16, 0));
    }
}
