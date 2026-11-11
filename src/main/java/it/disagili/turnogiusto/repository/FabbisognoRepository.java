package it.disagili.turnogiusto.repository;

//in model trovo gli enum e le classi del dominio
import it.disagili.turnogiusto.model.Fabbisogno;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.TipoGiorno;

//in sql trovo tutto ciò che serve per parlare con il database
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.List;

//accesso alla tabella "fabbisogno" = regole sql e conversione righe - oggetti
public class FabbisognoRepository {
    //OBIETTIVO: leggere tutti i fabbisogni
    //"throws SQLException": se il database è spento o la query è sbagliata, esce dal metodo e il controller gestisce l'errore

    public List<Fabbisogno> trovaTutti() throws SQLException {
        //step 1: creo la lista da riempire e restituire alla fine
        List<Fabbisogno> risultato = new ArrayList<>();

        //step 2: query, devo fare una richiesta dal repository al database per poter leggere la sua tabella
        String sql = "SELECT id, ruolo, tipo_giorno, servizio, numero_minimo_dipendenti "
                + "FROM fabbisogno ORDER BY id";

        //step 3: try-with-resources: nelle parentesi dopo il try scrivo le risorse da chiudere, cosicchè java le chiude da solo a fine blocco
        //qui nessun catch, dato che l'errore deve salire
        try (Connection connection = Database.getConnection(); //apre la connessione
             Statement st = connection.createStatement(); //prepara invio della query
             ResultSet rs = st.executeQuery(sql)) {  //esegue la query

            //step 4: scorrere le righe, spostando il cursore con rs.next()
            while (rs.next()) {
                //step 5:ogni riga del db diventa un oggetto Fabbisogno
                Fabbisogno f = new Fabbisogno(
                        rs.getInt("id"),
                        Ruolo.valueOf(rs.getString("ruolo")),
                        TipoGiorno.valueOf(rs.getString("tipo_giorno")),
                        Servizio.valueOf(rs.getString("servizio")),
                        rs.getInt("numero_minimo_dipendenti"));
                //step 6: aggiungo alla lista l'oggetto
                risultato.add(f);
            }
        } //qui vengono chiusi automaticamente connection, st e rs

        //step 7: restituisco la lista
        return risultato;
    }

    //OBIETTIVO: salvare un fabbisogno (inserisce o aggiorna se già esiste)
    //aggiorno un dato se trovo una tripla (ruolo, tipo_giorno, servizio) dato che sono UNIQUE
    public void salva(Fabbisogno f) throws SQLException {

        //step 1: faccio la query prendendo quattro buchi ? dove andranno i valori, MAI incollarli
        String sql = "INSERT INTO fabbisogno (ruolo, tipo_giorno, servizio, numero_minimo_dipendenti) "
                + "VALUES (?, ?, ?, ?) AS nuovo "  //mi permette di assegnare un nome ai valori nuovi
                + "ON DUPLICATE KEY UPDATE numero_minimo_dipendenti = nuovo.numero_minimo_dipendenti"; //posso grazie ad AS nuovo, richiamare il numero nuovo di dipendenti

        //step 2: try-with-resources, questa volta con PreparedStatement (perchè ho valori '?' che possono cambiare: PreparedStatement serve proprio a metterli con i ?)
        //questa è una query INSERT, mentre la prima una SELECT = non devo ricevere dati dal db, bensì inserirli
        try(Connection connection = Database.getConnection();
            PreparedStatement ps = connection.prepareStatement(sql)) {
            //step 3: riempio i buchi con numeri che indicano la posizione del ? (da 1, non da 0)
            ps.setString(1, f.getRuolo().name());
            ps.setString(2, f.getTipoGiorno().name());
            ps.setString(3, f.getServizio().name());
            ps.setInt(4, f.getNumeroMinimoDipendenti());

            //step 4: eseguo aggiornamento
            ps.executeUpdate();
        }
    }
}

