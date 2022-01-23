package me.lolico.desensitize.codec;

public abstract class IdentityCodec extends AbstractCodec {

    protected final String identity;

    public IdentityCodec(String identity) {
        this.identity = identity;
    }

    @Override
    public String encode(String source) {
        return identity + encodeWithoutIdentity(source);
    }

    @Override
    public String decode(String encoded) {
        if (encoded.startsWith(identity)) {
            return decodeWithoutIdentity(encoded.substring(identity.length()));
        }
        return encoded;
    }

    public abstract String encodeWithoutIdentity(String source);

    public abstract String decodeWithoutIdentity(String encoded);
}
