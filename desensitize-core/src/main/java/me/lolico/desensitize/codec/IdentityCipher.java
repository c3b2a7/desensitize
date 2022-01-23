package me.lolico.desensitize.codec;

public class IdentityCipher {

    private final String identity;
    private final String cipher;

    public IdentityCipher(String identity, String cipher) {
        this.identity = identity;
        this.cipher = cipher;
    }

    public String getIdentity() {
        return identity;
    }

    public String getCipher() {
        return cipher;
    }
}
