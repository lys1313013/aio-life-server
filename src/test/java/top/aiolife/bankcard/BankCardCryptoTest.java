package top.aiolife.bankcard;

import org.junit.jupiter.api.Test;
import top.aiolife.bankcard.service.BankCardCrypto;
import java.util.Arrays;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class BankCardCryptoTest {
    private final String number = "6222000000001234";
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);

    @Test void singleMasterKeySupportsStableFingerprintsAcrossRestarts() {
        var first = new BankCardCrypto(key);
        var restarted = new BankCardCrypto(key);
        String ciphertext = first.encrypt(number, 1, 2);
        assertTrue(ciphertext.startsWith("v2:"));
        assertEquals(number, restarted.decrypt(ciphertext, 1, 2));
        assertArrayEquals(first.fingerprint(number, 1), restarted.fingerprint(number, 1));
        assertEquals(32, first.fingerprint(number, 1).length);
        assertFalse(Arrays.equals(first.fingerprint(number, 1), first.fingerprint(number, 2)));
        assertNotEquals(ciphertext, first.encrypt(number, 1, 2));
    }

    @Test void sm4RejectsTamperedCiphertextNonceVersionAndWrongKey() {
        var crypto = new BankCardCrypto(key);
        String ciphertext = crypto.encrypt(number, 1, 2);
        String[] parts = ciphertext.split(":");
        for (int part : new int[]{1, 2}) {
            String[] changed = parts.clone();
            byte[] bytes = Base64.getDecoder().decode(changed[part]);
            bytes[bytes.length - 1] ^= 1;
            changed[part] = Base64.getEncoder().encodeToString(bytes);
            assertThrows(IllegalStateException.class, () -> crypto.decrypt(String.join(":", changed), 1, 2));
        }
        for (String invalid : new String[]{ciphertext.replace("v2:", "v1:"), ciphertext.replace("v2:", "v3:"), "v2::", "v2:bad:bad", ciphertext + ":extra"})
            assertThrows(IllegalStateException.class, () -> crypto.decrypt(invalid, 1, 2));
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(ciphertext, 2, 2));
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(ciphertext, 1, 3));
        byte[] other = new byte[32];other[0] = 1;
        var wrongKey = new BankCardCrypto(Base64.getEncoder().encodeToString(other));
        assertThrows(IllegalStateException.class, () -> wrongKey.decrypt(ciphertext, 1, 2));
        assertFalse(Arrays.equals(crypto.fingerprint(number, 1), wrongKey.fingerprint(number, 1)));
    }

    @Test void missingOrMalformedMasterKeyNeverFallsBackToDefault() {
        for (String invalid : new String[]{"", "not-base64", Base64.getEncoder().encodeToString(new byte[16]), Base64.getEncoder().encodeToString(new byte[31])}) {
            var crypto = new BankCardCrypto(invalid);
            assertThrows(IllegalStateException.class, () -> crypto.encrypt(number, 1, 2));
            assertThrows(IllegalStateException.class, () -> crypto.fingerprint(number, 1));
        }
    }
}
