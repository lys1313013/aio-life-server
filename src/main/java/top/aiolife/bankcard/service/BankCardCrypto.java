package top.aiolife.bankcard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 单一主密钥派生 SM4-GCM / HMAC-SM3 子密钥。 */
@Component
public class BankCardCrypto {
    private static final Provider PROVIDER = new BouncyCastleProvider();
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String masterKey;

    public BankCardCrypto(@Value("${AIO_LIFE_BANK_CARD_ENCRYPTION_KEY:}") String masterKey) {
        this.masterKey = masterKey;
    }

    private byte[] rootKey() {
        try {
            byte[] bytes = Base64.getDecoder().decode(masterKey);
            if (bytes.length == 32) return bytes;
        } catch (IllegalArgumentException | NullPointerException ignored) { }
        throw new IllegalStateException("银行卡加密配置未就绪，请联系管理员配置密钥");
    }

    private byte[] derive(String purpose, int length) {
        byte[] root = rootKey();
        try {
            var hkdf = new HKDFBytesGenerator(new SM3Digest());
            hkdf.init(new HKDFParameters(root, null,
                    ("aio-life/bank-card/v2/" + purpose).getBytes(StandardCharsets.UTF_8)));
            byte[] derived = new byte[length];
            hkdf.generateBytes(derived, 0, length);
            return derived;
        } finally { Arrays.fill(root, (byte) 0); }
    }

    public String normalize(String value) {
        String number = value == null ? "" : value.replaceAll("[\\s-]", "");
        if (!number.matches("[0-9]{12,19}")) throw new IllegalArgumentException("卡号须为12至19位数字");
        return number;
    }

    public String encrypt(String number, long userId, long cardId) {
        byte[] bytes = derive("sm4-gcm", 16);
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("SM4/GCM/NoPadding", PROVIDER);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(bytes, "SM4"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD((userId + ":" + cardId).getBytes(StandardCharsets.UTF_8));
            return "v2:" + Base64.getEncoder().encodeToString(nonce) + ":" +
                    Base64.getEncoder().encodeToString(cipher.doFinal(number.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("卡号加密失败"); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }

    public String decrypt(String encrypted, long userId, long cardId) {
        byte[] bytes = derive("sm4-gcm", 16);
        try {
            String[] parts = encrypted.split(":", -1);
            if (parts.length != 3 || !"v2".equals(parts[0])) throw new IllegalArgumentException();
            byte[] nonce = Base64.getDecoder().decode(parts[1]);
            byte[] payload = Base64.getDecoder().decode(parts[2]);
            if (nonce.length != 12 || payload.length < 16) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("SM4/GCM/NoPadding", PROVIDER);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(bytes, "SM4"),
                    new GCMParameterSpec(128, nonce));
            cipher.updateAAD((userId + ":" + cardId).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("卡号读取失败，请联系管理员检查密钥"); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }

    public byte[] fingerprint(String number, long userId) {
        byte[] bytes = derive("fingerprint", 32);
        try {
            var mac = new HMac(new SM3Digest());
            mac.init(new KeyParameter(bytes));
            byte[] input = (userId + ":" + number).getBytes(StandardCharsets.UTF_8);
            mac.update(input, 0, input.length);
            byte[] result = new byte[mac.getMacSize()];
            mac.doFinal(result, 0);
            return result;
        } finally { Arrays.fill(bytes, (byte) 0); }
    }
}
