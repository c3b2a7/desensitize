package me.lolico.desensitize.codec;

public interface Codec {
    String encode(String source);

    String decode(String encoded);

    Encoder getEncoder();

    Decoder getDecoder();

}
