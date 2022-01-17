package me.lolico.desensitize.codec;

import java.util.function.Function;

public interface Encoder extends Function<String, String> {
    String encode(String source);

    @Override
    default String apply(String s) {
        return encode(s);
    }
}
