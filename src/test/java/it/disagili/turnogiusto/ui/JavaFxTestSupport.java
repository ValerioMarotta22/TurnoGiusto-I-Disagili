package it.disagili.turnogiusto.ui;

import javafx.application.Platform;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Gestisce JavaFX esclusivamente durante i test, senza modificare l'applicazione.
 *
 * ATTENZIONE: JavaFX può essere avviato una sola volta nello stesso processo.
 * Spegnerlo alla fine dei soli test US01 impedirebbe ai successivi test US02
 * di utilizzarlo. Entrambe le classi devono quindi usare questa estensione:
 * l'avvio avviene una volta e lo spegnimento alla fine dell'intera esecuzione JUnit.
 */
final class JavaFxTestSupport implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        // Lo store della radice JUnit è condiviso fra classi e gruppi @Nested.
        // La chiave unica fa creare il toolkit soltanto al primo utilizzo.
        // JUnit chiuderà la risorsa alla fine di tutta l'esecuzione, anche su errori.
        context.getRoot().getStore(ExtensionContext.Namespace.create(JavaFxTestSupport.class))
                .getOrComputeIfAbsent(Toolkit.class, chiave -> new Toolkit(), Toolkit.class);
    }

    /** Esegue letture e modifiche dei controlli sul thread grafico, con attesa limitata. */
    static <T> T suJavaFx(Callable<T> azione) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return azione.call();
        }
        // FutureTask riporta al thread del test sia il risultato sia eventuali errori.
        FutureTask<T> operazione = new FutureTask<>(azione);
        Platform.runLater(operazione);
        return operazione.get(10, TimeUnit.SECONDS);
    }

    /** Risorsa posseduta da JUnit: non va chiusa dai singoli test o controller. */
    private static final class Toolkit implements ExtensionContext.Store.CloseableResource {
        Toolkit() {
            CountDownLatch pronto = new CountDownLatch(1);
            Platform.startup(() -> {
                // Chiudere l'ultima finestra non deve spegnere JavaFX:
                // potrebbero esserci altre prove grafiche ancora da eseguire.
                Platform.setImplicitExit(false);
                pronto.countDown();
            });
            try {
                if (!pronto.await(10, TimeUnit.SECONDS)) {
                    Platform.exit();
                    throw new IllegalStateException("Avvio di JavaFX non completato entro 10 secondi");
                }
            } catch (InterruptedException errore) {
                Thread.currentThread().interrupt();
                Platform.exit();
                throw new IllegalStateException("Attesa dell'avvio di JavaFX interrotta", errore);
            }
        }

        @Override
        public void close() {
            // JUnit arriva qui dopo entrambe le classi di test, non dopo la prima.
            Platform.exit();
        }
    }
}
