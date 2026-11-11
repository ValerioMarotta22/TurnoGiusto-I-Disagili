// INTERFACCIA SOSPESA: codice conservato in attesa del CSS condiviso del team.
// Per ripristinarlo, rimuovere questi due avvisi e un prefisso // da ogni riga.
// package it.disagili.turnogiusto;
//
// import it.disagili.turnogiusto.repository.Database;
// import javafx.fxml.FXML;
// import javafx.scene.control.Label;
//
// import java.sql.Connection;
// import java.sql.ResultSet;
// import java.sql.SQLException;
// import java.sql.Statement;
//
// /**
//  * Schermata di prova: il bottone legge dal database il numero di dipendenti.
//  * E' codice temporaneo per verificare che app e database comunichino;
//  * va sostituito dalle schermate vere (US-01 in poi).
//  */
// public class HelloController {
//     @FXML
//     private Label welcomeText;
//
//     @FXML
//     protected void onHelloButtonClick() {
//         try (Connection c = Database.getConnection();
//              Statement st = c.createStatement();
//              ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM dipendente")) {
//             rs.next();
//             welcomeText.setText("Database collegato: " + rs.getInt(1) + " dipendenti");
//         } catch (SQLException e) {
//             welcomeText.setText("Database non raggiungibile.\nAvvia 'docker compose up -d'.");
//         }
//     }
// }
//