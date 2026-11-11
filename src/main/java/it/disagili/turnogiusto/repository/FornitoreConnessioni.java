package it.disagili.turnogiusto.repository;

import java.sql.Connection;
import java.sql.SQLException;

/** Apre una connessione nuova, che il chiamante deve chiudere. */
@FunctionalInterface
public interface FornitoreConnessioni {
    Connection apri() throws SQLException;
}
