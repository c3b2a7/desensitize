package me.lolico.desensitize.codec;

import me.lolico.desensitize.util.AllUtils;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public class AesCodecTest {

    private Codec codec;
    private String original;

    @Before
    public void setup() throws Exception {
        // generate aes key
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256, new SecureRandom());
        SecretKey secretKey = keyGenerator.generateKey();
        String aesKey = Base64.getEncoder().withoutPadding().encodeToString(secretKey.getEncoded());

        codec = new AesCodec(new IdentityCipher(AllUtils.DEFAULT_IDENTITY, aesKey));
        original = UUID.randomUUID().toString();
    }

    @Test
    public void encodeAndDecode() {
        String encode = codec.encode(original);
        String decode = codec.decode(encode);
        Assertions.assertThat(decode).isEqualTo(original);
    }
}