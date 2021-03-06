package me.lolico.desensitize.codec;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AesCodec extends IdentityCodec {

    private final String aesKey;

    public AesCodec(IdentityCipher cipher) {
        super(cipher.getIdentity());
        this.aesKey = cipher.getCipher();
    }

    @Override
    public String encodeWithoutIdentity(String source) {
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
    public String decodeWithoutIdentity(String encoded) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            byte[] keyBytes = Base64.getDecoder().decode(aesKey);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"));
            byte[] source = cipher.doFinal(Base64.getDecoder().decode(encoded));
            return new String(source, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("aes算法加密异常->" + e.getMessage());
        }
    }
}
