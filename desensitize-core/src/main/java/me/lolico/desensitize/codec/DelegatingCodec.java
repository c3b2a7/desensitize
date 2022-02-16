package me.lolico.desensitize.codec;

public class DelegatingCodec<S, R> implements Codec<S, R> {

    private final Encoder<S, R> encoder;
    private final Decoder<R, S> decoder;

    public DelegatingCodec(Encoder<S, R> encoder, Decoder<R, S> decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    @Override
    public R encode(S source) {
        return encoder.encode(source);
    }

    @Override
    public S decode(R encoded) {
        return decoder.decode(encoded);
    }

    @Override
    public Encoder<S, R> getEncoder() {
        return this.encoder;
    }

    @Override
    public Decoder<R, S> getDecoder() {
        return this.decoder;
    }
}
