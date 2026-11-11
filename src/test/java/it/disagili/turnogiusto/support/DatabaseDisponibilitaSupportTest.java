package it.disagili.turnogiusto.support;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifica le protezioni senza aprire connessioni o toccare database reali. */
class DatabaseDisponibilitaSupportTest {

    @Test
    void configurazioneMavenAssenteUsaLaPortaDiTest() {
        assertEquals(33317, MySqlDisponibilitaExtension.leggiPorta(null));
        assertEquals(33318, MySqlDisponibilitaExtension.leggiPorta("33318"));
    }

    @Test
    void porteInvalideSonoBloccatePrimaDiAvviareMySql() {
        for (String porta : new String[]{"", "0", "-1", "65536", "non-una-porta"}) {
            assertThrows(IllegalArgumentException.class, () -> MySqlDisponibilitaExtension.leggiPorta(porta));
        }
    }

    @Test
    void catalogoErratoBloccaSqlESecondaPulizia() throws SQLException {
        AmbienteSimulato ambiente = new AmbienteSimulato("turnogiusto");
        SQLException errore = assertThrows(SQLException.class, ambiente::preparaDati);
        assertTrue(errore.getMessage().contains("catalogo ricevuto 'turnogiusto'"));
        ambiente.pulisciDati();
        assertEquals(1, ambiente.connessioniAperte);
        assertEquals(1, ambiente.connessioniChiuse);
        assertTrue(ambiente.comandi.isEmpty());
    }

    @Test
    void connessioneFallitaNonVieneRitentataDallaPuliziaFinale() throws SQLException {
        AmbienteSimulato ambiente = new AmbienteSimulato("turnogiusto_us03_test");
        ambiente.connessioneDisponibile = false;
        assertThrows(SQLException.class, ambiente::preparaDati);
        ambiente.pulisciDati();
        assertEquals(1, ambiente.connessioniAperte);
        assertTrue(ambiente.comandi.isEmpty());
    }

    @Test
    void puliziaInizialeFallitaNonVieneRipetutaAllaFine() throws SQLException {
        AmbienteSimulato ambiente = new AmbienteSimulato("turnogiusto_us03_test");
        ambiente.puliziaDisponibile = false;
        assertThrows(SQLException.class, ambiente::preparaDati);
        ambiente.pulisciDati();
        assertEquals(1, ambiente.connessioniAperte);
        assertEquals(1, ambiente.connessioniChiuse);
    }

    @Test
    void erroreDopoInizioPreparazionePulisceComunqueIDatiDiProva() throws SQLException {
        AmbienteSimulato ambiente = new AmbienteSimulato("turnogiusto_us03_test");
        SQLException errore = assertThrows(SQLException.class, ambiente::preparaDati);
        assertEquals("Inserimento simulato fallito", errore.getMessage());
        ambiente.pulisciDati();
        assertEquals(2, ambiente.connessioniAperte);
        assertEquals(2, ambiente.connessioniChiuse);
        assertEquals(8, ambiente.comandi.size());
    }

    @Test
    void ancheLaPuliziaFinaleRicontrollaIlCatalogo() {
        AmbienteSimulato ambiente = new AmbienteSimulato("turnogiusto_us03_test");
        assertThrows(SQLException.class, ambiente::preparaDati);
        ambiente.catalogo = "turnogiusto";
        assertThrows(SQLException.class, ambiente::pulisciDati);
        assertEquals(4, ambiente.comandi.size());
        assertEquals(2, ambiente.connessioniChiuse);
    }

    /**
     * Proxy implementa soltanto le chiamate JDBC usate dalla preparazione.
     * Registra i comandi in una lista: nessun driver o server viene contattato.
     */
    private static class AmbienteSimulato extends DatabaseDisponibilitaSupport {
        private String catalogo;
        private boolean connessioneDisponibile = true;
        private boolean puliziaDisponibile = true;
        private int connessioniAperte;
        private int connessioniChiuse;
        private final List<String> comandi = new ArrayList<>();

        private AmbienteSimulato(String catalogo) {
            this.catalogo = catalogo;
        }

        @Override
        protected Connection apriConnessione() throws SQLException {
            connessioniAperte++;
            if (!connessioneDisponibile) throw new SQLException("Connessione simulata non disponibile");
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, metodo, argomenti) -> {
                        switch (metodo.getName()) {
                            case "getCatalog": return catalogo;
                            case "createStatement": return statementSimulato();
                            case "close": connessioniChiuse++; return null;
                            default: throw new AssertionError("Chiamata JDBC inattesa: " + metodo.getName());
                        }
                    });
        }

        private Statement statementSimulato() {
            return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                    new Class<?>[]{Statement.class}, (proxy, metodo, argomenti) -> {
                        switch (metodo.getName()) {
                            case "execute":
                                if (!puliziaDisponibile) throw new SQLException("Pulizia simulata fallita");
                                comandi.add((String) argomenti[0]);
                                return false;
                            case "executeUpdate": comandi.add((String) argomenti[0]); return 0;
                            case "close": return null;
                            default: throw new AssertionError("Chiamata SQL inattesa: " + metodo.getName());
                        }
                    });
        }

        @Override
        protected int creaDipendente(String nome, String cognome, String tipo) throws SQLException {
            throw new SQLException("Inserimento simulato fallito");
        }
    }
}
