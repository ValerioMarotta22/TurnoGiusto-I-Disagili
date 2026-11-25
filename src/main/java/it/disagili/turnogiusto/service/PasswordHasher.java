package it.disagili.turnogiusto.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Trasforma le password in un "impronta" (hash) da salvare nel database e le confronta.
 * La password in chiaro NON viene mai salvata.
 *
 * Algoritmo: PBKDF2 con HMAC-SHA256, che e' gia' nel JDK (nessuna libreria in piu'). E' lento di proposito:
 * chi rubasse il database dovrebbe provare le password una alla volta, a caro prezzo.
 * Ogni password ha un "sale" casuale suo: due persone con la stessa password hanno hash diversi.
 *
 * Formato salvato (un solo testo): pbkdf2-sha256$ITERAZIONI$SALE$HASH   (sale e hash in Base64)
 * Le iterazioni stanno dentro il testo: si possono aumentare in futuro senza rompere le password vecchie.
 */
public final class PasswordHasher {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String PREFISSO = "pbkdf2-sha256";
    private static final int ITERAZIONI = 210_000;
    private static final int BYTE_SALE = 16;
    private static final int BIT_HASH = 256;

    private PasswordHasher() {
    }

    /** Crea l'hash di una password, con un sale nuovo. */
    public static String hash(char[] password) {
        byte[] sale = new byte[BYTE_SALE];
        new SecureRandom().nextBytes(sale); // sale casuale e imprevedibile
        byte[] impronta = calcola(password, sale, ITERAZIONI);
        return PREFISSO + "$" + ITERAZIONI + "$"
                + Base64.getEncoder().encodeToString(sale) + "$"
                + Base64.getEncoder().encodeToString(impronta);
    }

    /** Vero se la password corrisponde all'hash salvato. Un formato non valido vale "no", mai un errore. */
    public static boolean verifica(char[] password, String salvato) {
        if (password == null || salvato == null) {
            return false;
        }
        String[] parti = salvato.split("\\$");
        if (parti.length != 4 || !PREFISSO.equals(parti[0])) {
            return false;
        }
        try {
            int iterazioni = Integer.parseInt(parti[1]);
            byte[] sale = Base64.getDecoder().decode(parti[2]);
            byte[] atteso = Base64.getDecoder().decode(parti[3]);
            byte[] calcolato = calcola(password, sale, iterazioni);
            // isEqual confronta in tempo costante: non rivela dove i due hash differiscono.
            return MessageDigest.isEqual(atteso, calcolato);
        } catch (IllegalArgumentException formatoErrato) {
            return false;
        }
    }

    private static byte[] calcola(char[] password, byte[] sale, int iterazioni) {
        PBEKeySpec specifica = new PBEKeySpec(password, sale, iterazioni, BIT_HASH);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(specifica).getEncoded();
        } catch (GeneralSecurityException errore) {
            throw new IllegalStateException("Algoritmo per le password non disponibile", errore);
        } finally {
            specifica.clearPassword(); // cancella la copia della password tenuta dalla specifica
        }
    }

    /** Svuota un array di caratteri (le password non devono restare in memoria piu' del necessario). */
    public static void cancella(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
