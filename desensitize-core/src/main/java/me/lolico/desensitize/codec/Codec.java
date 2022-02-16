package me.lolico.desensitize.codec;

public interface Codec<S, R> {
    R encode(S source);

    S decode(R encoded);

    Encoder<S, R> getEncoder();

    Decoder<R, S> getDecoder();
}
