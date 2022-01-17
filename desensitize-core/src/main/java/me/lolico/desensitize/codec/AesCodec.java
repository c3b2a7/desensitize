package me.lolico.desensitize.codec;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AesCodec extends AbstractCodec {

    private final String aesKey;

    public AesCodec(String aesKey) {
        this.aesKey = aesKey;
    }

    @Override
    public String encode(String source) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            byte[] keyBytes = Base64.getDecoder().decode(aesKey);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"));
            byte[] encoded = cipher.doFinal(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().withoutPadding().encodeToString(encoded);
        } catch (Exception e) {
            throw new RuntimeException("aes算法加密异常->" + e.getMessage());
        }
    }

    @Override
    public String decode(String encoded) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            byte[] keyBytes = Base64.getDecoder().decode(aesKey);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"));
            byte[] source = cipher.doFinal(encoded.getBytes(StandardCharsets.UTF_8));
            return new String(Base64.getDecoder().decode(source));
        } catch (Exception e) {
            throw new RuntimeException("aes算法加密异常->" + e.getMessage());
        }
    }
}
