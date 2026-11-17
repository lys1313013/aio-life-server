package top.aiolife.record.notification;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 服务端第三方凭据统一使用 SM4-GCM，主密钥由同一环境变量派生。 */
@Component
public class NotificationCryptoService {

    private static final Provider PROVIDER = new BouncyCastleProvider();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final String PREFIX = "sm4:v1:";
    private static final String DEFAULT_PURPOSE = "credential";

    private final String encryptionKey;

    public NotificationCryptoService(
            @Value("${AIO_LIFE_NOTIFICATION_ENCRYPTION_KEY:}") String encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    public String encrypt(String plaintext) {
        return encrypt(DEFAULT_PURPOSE, plaintext, "");
    }

    public String decrypt(String ciphertext) {
        return decrypt(DEFAULT_PURPOSE, ciphertext, "");
    }

    public String encrypt(String purpose, String plaintext, String aad) {
        if (plaintext == null) {
            return null;
        }
        ensureConfigured();
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("SM4/GCM/NoPadding", PROVIDER);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(purpose), new GCMParameterSpec(TAG_LENGTH, nonce));
            if (StringUtils.hasText(aad)) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(nonce) + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    public String decrypt(String purpose, String ciphertext, String aad) {
        if (!StringUtils.hasText(ciphertext) || !ciphertext.startsWith(PREFIX)) {
            throw new IllegalArgumentException("凭据密文格式无效");
        }
        ensureConfigured();
        try {
            String[] parts = ciphertext.substring(PREFIX.length()).split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("凭据密文格式无效");
            }
            byte[] nonce = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            if (nonce.length != NONCE_LENGTH || encrypted.length <= TAG_LENGTH / 8) {
                throw new IllegalArgumentException("凭据密文格式无效");
            }
            Cipher cipher = Cipher.getInstance("SM4/GCM/NoPadding", PROVIDER);
            cipher.init(Cipher.DECRYPT_MODE, keySpec(purpose), new GCMParameterSpec(TAG_LENGTH, nonce));
            if (StringUtils.hasText(aad)) {
                cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            }
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("凭据解密失败，请检查环境变量", e);
        }
    }

    private SecretKeySpec keySpec(String purpose) throws Exception {
        byte[] root = MessageDigest.getInstance("SHA-256")
                .digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
        try {
            var hkdf = new HKDFBytesGenerator(new SM3Digest());
            hkdf.init(new HKDFParameters(root, null,
                    ("aio-life/" + purpose + "/v1/sm4-gcm").getBytes(StandardCharsets.UTF_8)));
            byte[] key = new byte[16];
            hkdf.generateBytes(key, 0, key.length);
            return new SecretKeySpec(key, "SM4");
        } finally {
            Arrays.fill(root, (byte) 0);
        }
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(encryptionKey) || encryptionKey.length() < 16) {
            throw new IllegalStateException(
                    "请配置长度不少于 16 位的 AIO_LIFE_NOTIFICATION_ENCRYPTION_KEY");
        }
    }
}
