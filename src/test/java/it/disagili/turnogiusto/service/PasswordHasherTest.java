package it.disagili.turnogiusto.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Le password si salvano come hash con un sale diverso per ognuna e si confrontano senza mai tenerle in chiaro. */
class PasswordHasherTest {

    @Test
    void unaPasswordGiustaVieneRiconosciuta() {
        String hash = PasswordHasher.hash("segreta123".toCharArray());

        assertTrue(PasswordHasher.verifica("segreta123".toCharArray(), hash));
    }

    @Test
    void unaPasswordSbagliataVieneRifiutata() {
        String hash = PasswordHasher.hash("segreta123".toCharArray());

        assertFalse(PasswordHasher.verifica("segreta124".toCharArray(), hash));
        assertFalse(PasswordHasher.verifica("SEGRETA123".toCharArray(), hash)); // maiuscole e minuscole contano
        assertFalse(PasswordHasher.verifica("".toCharArray(), hash));
    }

    @Test
    void laStessaPasswordDaHashDiversiPerViaDelSale() {
        String primo = PasswordHasher.hash("segreta123".toCharArray());
        String secondo = PasswordHasher.hash("segreta123".toCharArray());

        assertNotEquals(primo, secondo);
        // ma entrambi si verificano con la stessa password
        assertTrue(PasswordHasher.verifica("segreta123".toCharArray(), primo));
        assertTrue(PasswordHasher.verifica("segreta123".toCharArray(), secondo));
    }

    @Test
    void ilTestoSalvatoNonContieneLaPassword() {
        String hash = PasswordHasher.hash("segreta123".toCharArray());

        assertFalse(hash.contains("segreta123"));
        assertTrue(hash.startsWith("pbkdf2-sha256$210000$"));
        assertEquals(4, hash.split("\\$").length); // algoritmo, iterazioni, sale, hash
    }

    @Test
    void unFormatoSalvatoNonValidoVaintendutoComeNo() {
        char[] password = "segreta123".toCharArray();

        assertFalse(PasswordHasher.verifica(password, null));
        assertFalse(PasswordHasher.verifica(password, ""));
        assertFalse(PasswordHasher.verifica(password, "segreta123")); // password in chiaro: non e' un hash
        assertFalse(PasswordHasher.verifica(password, "pbkdf2-sha256$abc$def$ghi"));
        assertFalse(PasswordHasher.verifica(password, "md5$1$AAAA$BBBB"));
        assertFalse(PasswordHasher.verifica(null, PasswordHasher.hash(password)));
    }

    @Test
    void cancellareUnaPasswordSvuotaLArray() {
        char[] password = "segreta123".toCharArray();

        PasswordHasher.cancella(password);

        for (char carattere : password) {
            assertEquals('\0', carattere);
        }
        PasswordHasher.cancella(null); // non deve dare errore
    }
}
